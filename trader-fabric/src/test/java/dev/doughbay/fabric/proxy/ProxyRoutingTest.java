package dev.doughbay.fabric.proxy;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProxyRoutingTest {
    @TempDir Path root;
    private static ChannelHandler initializer(){return new ChannelInitializer<Channel>(){protected void initChannel(Channel c){}};}
    @Test void missingSettingsUseDirectButBrokenSettingsBlockRemoteConnections()throws Exception{
        var handler=initializer();var target=new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte)198,51,100,2}),25565);
        assertSame(handler,ProxyRouting.route(target,handler,new ProxySettings(root)));
        Files.writeString(root.resolve("gonuts-proxies.json"),"bad");
        assertThrows(IllegalStateException.class,()->ProxyRouting.route(target,handler,new ProxySettings(root)));
        assertSame(handler,ProxyRouting.route(new InetSocketAddress("127.0.0.1",25565),handler,new ProxySettings(root)));
    }
    @Test void authenticatedSocks5TunnelCarriesGameTrafficWithoutContactingTheDestination()throws Exception{
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            var accepted=CompletableFuture.runAsync(()->{
                try(var socket=server.accept()){
                    socket.setSoTimeout(5000);var in=socket.getInputStream();var out=socket.getOutputStream();
                    assertEquals(5,in.read());int methods=in.read();assertEquals(methods,in.readNBytes(methods).length);out.write(new byte[]{5,2});out.flush();
                    assertEquals(1,in.read());assertEquals("private-user",new String(in.readNBytes(in.read()),java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals("private-secret",new String(in.readNBytes(in.read()),java.nio.charset.StandardCharsets.UTF_8));out.write(new byte[]{1,0});out.flush();
                    assertEquals(5,in.read());assertEquals(1,in.read());assertEquals(0,in.read());int type=in.read();
                    assertEquals(1,type);assertArrayEquals(new byte[]{(byte)198,51,100,2},in.readNBytes(4));assertEquals(25565,(in.read()<<8)|in.read());
                    out.write(new byte[]{5,0,0,1,127,0,0,1,0,0});out.flush();assertEquals("probe",new String(in.readNBytes(5),java.nio.charset.StandardCharsets.UTF_8));
                }catch(Exception e){throw new CompletionException(e);}
            });
            var settings=new ProxySettings(root);var form=new HashMap<>(ProxySettingsTest.profile());form.put("port",String.valueOf(server.getLocalPort()));settings.change(form);
            settings.change(Map.of("action","enable","id",settings.state().profiles().getFirst().id()));
            EventLoopGroup group=new NioEventLoopGroup(1);Channel channel=null;
            try{
                var target=new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte)198,51,100,2}),25565);
                channel=new Bootstrap().group(group).channel(NioSocketChannel.class).handler(ProxyRouting.route(target,initializer(),settings)).connect(target).sync().channel();
                channel.writeAndFlush(Unpooled.copiedBuffer("probe",java.nio.charset.StandardCharsets.UTF_8)).sync();accepted.get(10,TimeUnit.SECONDS);
            }finally{if(channel!=null)channel.close().sync();group.shutdownGracefully(0,1,TimeUnit.SECONDS).sync();}
        }
    }
}
