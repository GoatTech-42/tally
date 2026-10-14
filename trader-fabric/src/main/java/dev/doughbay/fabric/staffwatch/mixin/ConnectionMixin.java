package dev.doughbay.fabric.staffwatch.mixin;
import io.netty.channel.*;import net.minecraft.network.*;import net.minecraft.network.protocol.*;
import dev.doughbay.fabric.staffwatch.event.impl.*;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class) public abstract class ConnectionMixin {
 @Shadow public abstract PacketFlow getReceiving();
 @Inject(method="channelRead0",at=@At("HEAD"),cancellable=true)
 private void receive(ChannelHandlerContext ctx,Packet<?> packet,CallbackInfo ci){if(getReceiving()==PacketFlow.CLIENTBOUND&&PacketReceiveNettyEvent.dispatchSync(packet))ci.cancel();}
 @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"),cancellable=true)
 private void send(Packet<?> packet,ChannelFutureListener listener,boolean flush,CallbackInfo ci){if(getReceiving()==PacketFlow.CLIENTBOUND&&PacketSendEvent.dispatchSync(packet))ci.cancel();}
}
