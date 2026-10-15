package dev.doughbay.fabric.proxy;

import io.netty.channel.*;
import io.netty.handler.proxy.*;
import java.net.*;

/** Minecraft TCP only: login, reconnect, transfer and status connections share the same route. */
public final class ProxyRouting {
    private ProxyRouting() {}
    public static ChannelHandler route(InetSocketAddress target, ChannelHandler initializer) {
        return route(target, initializer, ProxyAccess.settings());
    }
    static ChannelHandler route(InetSocketAddress target, ChannelHandler initializer, ProxySettings settings) {
        if(isLocal(target))return initializer;
        synchronized(settings) {
            if(settings.blocked())throw new IllegalStateException("GoNuts proxy configuration unavailable; direct connection blocked.");
            if(!settings.state().enabled())return initializer;
            var profile=settings.selected();
            var address=new InetSocketAddress(profile.host(),profile.port());
            return new ChannelInitializer<Channel>() {
                @Override protected void initChannel(Channel channel) {
                    ProxyHandler handler=profile.type().equals("SOCKS4")
                        ? new Socks4ProxyHandler(address,profile.username().isEmpty()?null:profile.username())
                        : new Socks5ProxyHandler(address,profile.username().isEmpty()?null:profile.username(),profile.password().isEmpty()?null:profile.password());
                    handler.setConnectTimeoutMillis(10000);
                    channel.pipeline().addFirst("gonuts_proxy",handler);
                    channel.pipeline().addLast(initializer);
                }
            };
        }
    }
    static boolean isLocal(InetSocketAddress target) {
        if(target==null)return true;
        InetAddress ip=target.getAddress();
        if(ip==null)return target.getHostString().equalsIgnoreCase("localhost");
        byte[] bytes=ip.getAddress();
        return ip.isLoopbackAddress()||ip.isAnyLocalAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()
            ||bytes.length==16&&(bytes[0]&254)==252;
    }
}
