package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.Supplier;

/** Hourly numeric report snapshots on the existing authenticated dashboard transport. */
final class SettingsAnalysis {
    static final long HOUR = 3_600_000L;
    private String account = "", code = "";
    private long sampledAt, sentAt, sentEpoch = Long.MIN_VALUE;

    static boolean negotiated(JsonNode welcome) {
        var schema = welcome.path("settingsAnalysisSchema");
        return schema.isIntegralNumber() && schema.canConvertToInt() && schema.intValue() == 1;
    }

    /** Called on the game thread; supplier must be SettingsCode.exportForReport, never full export. */
    synchronized void capture(String currentAccount, boolean sharing, long now, Supplier<String> numericCode) {
        if (!sharing || currentAccount == null || currentAccount.isBlank()) { clear(); return; }
        if (account.equals(currentAccount) && sampledAt > 0 && (now < sampledAt || now - sampledAt < HOUR)) return;
        String next = numericCode.get();
        if (now <= 0 || next == null || next.length() > 12000 || !next.matches("DB1-[A-Z2-7]+(?:-[A-Z2-7]+)*")) {
            clear(); return;
        }
        account = currentAccount; code = next; sampledAt = now;
        sentAt = 0; sentEpoch = Long.MIN_VALUE;
    }

    synchronized ObjectNode pending(String currentAccount, long epoch, boolean supported, boolean sharing, long now) {
        if (!supported || !sharing || !account.equals(currentAccount) || sampledAt <= 0
                || now < sampledAt || now - sampledAt >= HOUR || code.isEmpty()
                || sentAt == sampledAt && sentEpoch == epoch) return null;
        return DashboardProtocol.JSON.createObjectNode().put("version", 1).put("sampledAt", sampledAt).put("code", code);
    }

    /** Only a frame accepted into the connection send queue consumes this attempt. */
    synchronized void sent(String currentAccount, long epoch, long at) {
        if (!account.equals(currentAccount) || sampledAt != at) return;
        sentAt = at; sentEpoch = epoch;
    }

    synchronized void clear() {
        account = ""; code = ""; sampledAt = 0; sentAt = 0; sentEpoch = Long.MIN_VALUE;
    }
}
