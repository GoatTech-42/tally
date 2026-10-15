package dev.doughbay.fabric.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.netty.bootstrap.*;
import io.netty.channel.ChannelHandler;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Connection.class)
abstract class ProxyConnectionMixin {
    @WrapOperation(method="connect",at=@At(value="INVOKE",target="Lio/netty/bootstrap/Bootstrap;handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;"))
    private static AbstractBootstrap<?,?> gonuts$proxy(Bootstrap bootstrap,ChannelHandler initializer,
            Operation<AbstractBootstrap<?,?>> original,@Local(argsOnly=true) InetSocketAddress target) {
        return original.call(bootstrap,dev.doughbay.fabric.proxy.ProxyRouting.route(target,initializer));
    }
}
