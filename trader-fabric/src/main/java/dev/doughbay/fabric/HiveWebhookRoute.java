package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;

/** Reuse an existing Hive destination without copying credentials into a joined profile. */
final class HiveWebhookRoute {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HiveWebhookRoute() {}
    static String resolve(String own, Path primaryConfiguration) {
        return resolve(own,primaryConfiguration,false);
    }
    static String resolve(String own, Path primaryConfiguration, boolean immediateAlert) {
        String local = own == null ? "" : own.strip();
        if (!local.isEmpty() || primaryConfiguration == null) return local;
        Path file = primaryConfiguration.resolve("tuning.json");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 2_000_000) return "";
            var root = JSON.readTree(Files.readString(file));
            if (!root.isObject() || root.path("webhook.enabled").asDouble(0) < .5) return "";
            if (immediateAlert && root.path("webhook.alerts").asDouble(1) < .5) return "";
            return root.path("webhook.url").asText("").strip();
        } catch (Exception ignored) { return ""; }
    }
}
