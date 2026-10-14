package dev.doughbay.fabric.staffwatch.mixin;
import net.minecraft.client.Minecraft;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class) public class WindowMixin {
 @Inject(method="renderFrame",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/GpuSurface;present()V")) private void draw(CallbackInfo ci){dev.doughbay.fabric.staffwatch.imgui.ImGuiManager.getInstance().renderIfActive();}
}
