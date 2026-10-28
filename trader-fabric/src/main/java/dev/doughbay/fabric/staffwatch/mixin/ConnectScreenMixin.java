package dev.doughbay.fabric.staffwatch.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import dev.doughbay.fabric.staffwatch.ReconnectController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
 @Inject(method="startConnecting",at=@At("RETURN"))
 private static void started(Screen parent,Minecraft mc,ServerAddress address,ServerData server,boolean quickPlay,TransferState transfer,CallbackInfo ci){
  if(mc.screen instanceof ConnectScreen)ReconnectController.INSTANCE.connectionStarted(server);
 }
}
