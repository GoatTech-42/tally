package dev.doughbay.fabric.mixin;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.platform.Window;
import dev.doughbay.fabric.QualWorldSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// Harness-only pre-swap capture hook. Fires on the render thread at the HEAD of
// Window.updateDisplay (intermediary: method_15998(Lnet/minecraft/class_10219;)V - hardcoded
// because this jar is remapped without a loom refmap; require=1 plus defaultRequire=1 makes a
// mismatch fail closed at apply time). No candidate class is touched.
@Mixin(Window.class)
public abstract class WindowCaptureMixin {
 @Inject(method="method_15998(Lnet/minecraft/class_10219;)V",at=@At("HEAD"),require=1)
 private void qualworld$captureBeforeSwap(TracyFrameCapture tracy,CallbackInfo ci){QualWorldSmoke.captureBeforeSwap();}
}
