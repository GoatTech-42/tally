package dev.doughbay.fabric.mixin;

import dev.doughbay.fabric.NameMask;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scrambles the player's own name in plain strings as they are drawn, which
 * is how the mod's own screens draw everything: the account line beside the
 * balance, the hive roster, a log line that names us. Only while "hide name"
 * is on, and only on the way to the screen; the string the mod holds is not
 * changed.
 */
@Mixin(GuiGraphics.class)
abstract class GuiTextNameMixin {

    // Not required, as with the name tag and the sidebar: an overload that
    // has moved shows the name again rather than stopping the client.
    @ModifyVariable(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private String doughbay$hideOwnNameShadowed(String text) {
        return NameMask.mask(text);
    }

    @ModifyVariable(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private String doughbay$hideOwnName(String text) {
        return NameMask.mask(text);
    }

    @ModifyVariable(
            method = "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private String doughbay$hideOwnNameCentered(String text) {
        return NameMask.mask(text);
    }
}
