package dev.doughbay.fabric.staffwatch;

import dev.doughbay.fabric.DoughBayClient;
import dev.doughbay.fabric.EvasionGuard;
import dev.doughbay.fabric.Tuning;
import dev.doughbay.storage.Database;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Local-world-only integration probe; no server connection or real notification credentials. */
final class RecoveryIntegrationSmoke {
    static List<String> run(Minecraft mc) throws Exception {
        if (!mc.hasSingleplayerServer()) throw new AssertionError("Recovery smoke requires a disposable local world");
        List<String> report = new ArrayList<>();
        Path ledger = Path.of("recovery-smoke-" + System.currentTimeMillis() + ".db");
        try (var db = new Database(ledger)) { }
        EvasionGuard.setLedger(ledger);
        WatchConfig.get().autoDisconnect = false;
        Tuning.set("pace.click_gap_min_sec", .45);
        Tuning.set("pace.click_gap_max_sec", .95);
        Tuning.set("pace.recover_click_spam", 1);
        var listener = mc.getConnection();
        var server = ClientCommonPacketListenerImpl.class.getDeclaredField("serverData");
        server.setAccessible(true);
        server.set(listener, new ServerData("Disposable smoke", "donutsmp.net", ServerData.Type.OTHER));
        // This is the real Minecraft callback: Mixin application and teardown both run.
        listener.onDisconnect(new DisconnectionDetails(Component.literal("Click-Spam")));
        check(Tuning.get("pace.click_gap_min_sec") >= .65, "real disconnect callback applies slower pacing", report);
        check(Tuning.thisClientOnly("pace.click_gap_min_sec"), "recovery remains local to this account", report);
        check(Files.readString(Path.of("doughbay-local.json")).contains("pace.click_gap_min_sec"), "recovery persisted to the game directory", report);
        double first = Tuning.get("pace.click_gap_min_sec");
        listener.onDisconnect(new DisconnectionDetails(Component.literal("Click-Spam")));
        check(Tuning.get("pace.click_gap_min_sec") == first, "duplicate connection callback does not slow down twice", report);
        for (int i = 0; i < 50; i++) {
            try (var db = new Database(ledger)) {
                var alerts = dev.doughbay.storage.ConnectionAlerts.pending(db.connection());
                if (!alerts.isEmpty()) {
                    check(alerts.size() == 1 && alerts.getFirst().who().equals("Click-Spam"), "one durable kick notification records the real reason", report);
                    check(alerts.getFirst().action().contains("Slower pacing applied"), "notification describes recovery", report);
                    check(dev.doughbay.storage.StaffWatchAlerts.pending(db.connection()).isEmpty(), "kick notifications do not masquerade as staff alerts", report);
                    return report;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Disconnect alert was not queued");
    }

    private static void check(boolean ok, String label, List<String> report) {
        if (!ok) throw new AssertionError(label);
        report.add("PASS: " + label);
        System.out.println("RECOVERY_TEST PASS: " + label);
    }
}
