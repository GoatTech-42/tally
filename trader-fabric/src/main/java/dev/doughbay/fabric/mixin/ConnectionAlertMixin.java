package dev.doughbay.fabric.mixin;

import dev.doughbay.fabric.ConnectionAlerts;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ConnectionAlertMixin {
    @Shadow @Final protected ServerData serverData;
    @Shadow protected boolean isTransferring;
    @Unique private boolean doughbay$reported;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void doughbay$disconnected(DisconnectionDetails details, CallbackInfo ci) {
        if (doughbay$reported || isTransferring || serverData == null) return;
        doughbay$reported = true;
        try {
            ConnectionAlerts.disconnected(serverData.ip, details.reason().getString());
        } catch (RuntimeException e) {
            // Reporting must never prevent vanilla disconnect or reconnect handling.
            dev.doughbay.fabric.DoughBayClient.LOGGER.warn("GoNuts disconnect reporting failed", e);
        }
    }
}
