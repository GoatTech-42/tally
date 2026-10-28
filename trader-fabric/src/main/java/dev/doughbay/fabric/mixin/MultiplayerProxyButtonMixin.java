package dev.doughbay.fabric.mixin;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(JoinMultiplayerScreen.class)
abstract class MultiplayerProxyButtonMixin extends Screen {
    protected MultiplayerProxyButtonMixin(Component title){super(title);}
    @Inject(method="init",at=@At("TAIL"))
    private void gonuts$proxies(CallbackInfo ci){
        addRenderableWidget(Button.builder(Component.literal("GoNuts hive"),b->dev.doughbay.fabric.DoughBayClient.openHiveSetup())
            .bounds(width-242,6,110,20).build());
        addRenderableWidget(Button.builder(Component.literal("GoNuts proxies"),b->minecraft.gui.setScreen(new dev.doughbay.fabric.ProxyScreen(this)))
            .bounds(width-126,6,120,20).build());
    }
}
