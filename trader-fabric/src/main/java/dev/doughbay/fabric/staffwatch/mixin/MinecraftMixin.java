package dev.doughbay.fabric.staffwatch.mixin;
import net.minecraft.client.Minecraft;import dev.doughbay.fabric.staffwatch.event.*;import dev.doughbay.fabric.staffwatch.event.impl.TickEvent;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class) public class MinecraftMixin {
 @Inject(method="tick",at=@At("HEAD")) private void before(CallbackInfo ci){EventBus.INSTANCE.drainAsync();TickEvent.dispatch();}
 @Inject(method="tick",at=@At("RETURN")) private void after(CallbackInfo ci){TickEvent.dispatchPost();dev.doughbay.fabric.staffwatch.StaffWatchClient.tick();}
}
