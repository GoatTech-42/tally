package dev.doughbay.fabric.mixin;
import dev.doughbay.fabric.GoNutsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Reserve the shared footer before any GoNuts child lays out its widgets. Vanilla screens are unchanged. */
@Mixin(Screen.class)
abstract class GoNutsReportFooterMixin {
    @ModifyVariable(method="init(II)V",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private int gonuts$reportFooterHeight(int height){
        return (Object)this instanceof GoNutsScreen own?own.reserveReportSpace(height):height;
    }
}
