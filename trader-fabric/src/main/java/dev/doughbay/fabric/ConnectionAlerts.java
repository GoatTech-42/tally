package dev.doughbay.fabric;

import dev.doughbay.fabric.staffwatch.DonutServer;
import java.util.Locale;

/** Called once by a connection listener, before vanilla tears down its world. */
public final class ConnectionAlerts {
    private ConnectionAlerts() { }

    public static void disconnected(String server, String reason) {
        if (!DonutServer.matches(server) || DisconnectRecovery.intentional(reason)) return;
        dev.doughbay.fabric.staffwatch.ReconnectController.INSTANCE.unexpectedConnectionLost(reason);
        String recovery = "Pacing unchanged.";
        if (DisconnectRecovery.clickSpam(reason) && Tuning.get("pace.recover_click_spam") >= .5) {
            try {
                boolean changed = Tuning.setLocalBatch(DisconnectRecovery.slower(Tuning::get));
                recovery = String.format(Locale.ROOT,
                        "%s Click gap %.2f–%.2fs; command gap %.2f–%.2fs. Saved for this account only.",
                        changed ? "Slower pacing applied." : "Conservative pacing already at its limit.",
                        Tuning.get("pace.click_gap_min_sec"), Tuning.get("pace.click_gap_max_sec"),
                        Tuning.get("pace.command_min_sec"), Tuning.get("pace.command_max_sec"));
            } catch (Exception e) {
                recovery = "Could not save slower pacing; existing settings retained. Check the client log.";
                DoughBayClient.LOGGER.warn("GoNuts click-spam recovery failed", e);
            }
        }
        String retry = dev.doughbay.fabric.staffwatch.ReconnectController.INSTANCE.unexpectedDisconnectNextAction();
        String detail = DisconnectAlertSummary.detail(server, reason, recovery, retry);
        DoughBayClient.LOGGER.warn("GoNuts connection lost: {} — {}", DoughBayClient.account(), detail);
        var guard = DoughBayClient.evasionGuard();
        if (guard != null) guard.noteDisconnect(reason, detail);
        var webhook = DoughBayClient.webhook();
        if (webhook != null) webhook.alert(DisconnectAlertSummary.title(reason), 0xFFA85C,
                DisconnectAlertSummary.fields(server, reason, recovery, retry), DoughBayClient.account());
    }
}
