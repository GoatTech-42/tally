package dev.doughbay.fabric.staffwatch;

import com.google.gson.*;
import dev.doughbay.fabric.staffwatch.event.EventPriority;
import dev.doughbay.fabric.staffwatch.event.impl.PacketReceiveNettyEvent;
import dev.doughbay.fabric.staffwatch.event.impl.PacketSendEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;

/** Disposable, four-request transport comparison. No actions or detector decisions. */
public final class StaffProbeDiagnostic implements ClientModInitializer {
    private static final String[] INPUTS = {"minecraft:msg ", "/minecraft:msg ", "msg ", "/msg "};
    private final Map<Integer, JsonObject> results = new ConcurrentHashMap<>();
    private final Map<String, String> peers = new HashMap<>();
    private int index, stable;
    private long sentAt;
    private Path output;
    private boolean done;

    public void onInitializeClient() {
        var game = FabricLoader.getInstance().getGameDir();
        output = game.resolve("staff-probe-diagnostic-results.json");
        try {
            var config = JsonParser.parseString(Files.readString(game.resolve("staff-probe-diagnostic-peers.json"))).getAsJsonObject();
            config.entrySet().forEach(e -> peers.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception ignored) { }
        PacketSendEvent.TYPE.listen(this, EventPriority.LOWEST, event -> {
            if (event.packet instanceof ServerboundCommandSuggestionPacket request) {
                var row = results.get(request.getId());
                if (row != null) synchronized (row) { row.addProperty("outboundHookObserved", true); }
            }
        });
        PacketReceiveNettyEvent.TYPE.listen(this, EventPriority.HIGHEST, event -> {
            if (event.packet instanceof ClientboundCommandSuggestionsPacket reply) {
                var row = results.get(reply.id());
                if (row == null) return;
                synchronized (row) {
                    row.addProperty("replyReceived", true);
                    row.addProperty("suggestionCount", reply.toSuggestions().getList().size());
                    var matches = new JsonObject();
                    peers.forEach((alias, name) -> matches.addProperty(alias, reply.toSuggestions().getList().stream()
                        .anyMatch(s -> s.getText().equalsIgnoreCase(name))));
                    row.add("controlledAccountMatches", matches);
                }
                event.setCancelled(true);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (done) return;
        if (mc.level == null || mc.player == null || mc.getConnection() == null) { stable = 0; return; }
        if (++stable < 100) return;
        long now = System.currentTimeMillis();
        if (sentAt != 0 && now - sentAt < 8000) return;
        if (index >= INPUTS.length) { done = true; save(); return; }
        int id = 810000 + index;
        var row = new JsonObject();
        row.addProperty("request", INPUTS[index]);
        row.addProperty("issuedAt", now);
        row.addProperty("outboundHookObserved", false);
        row.addProperty("replyReceived", false);
        results.put(id, row);
        index++; sentAt = now;
        try { mc.getConnection().send(new ServerboundCommandSuggestionPacket(id, INPUTS[index - 1])); }
        catch (RuntimeException failure) { row.addProperty("sendFailed", true); }
        save();
    }

    private void save() {
        try {
            var root = new JsonObject();
            root.addProperty("testOnly", true); root.addProperty("complete", done);
            root.addProperty("noCommandsExecuted", true); root.addProperty("noDetectorRulesChanged", true);
            var rows = new JsonArray();
            results.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                synchronized (e.getValue()) { rows.add(e.getValue().deepCopy()); }
            });
            root.add("results", rows);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception failure) { System.err.println("Staff probe diagnostic could not save aggregate results"); }
    }
}
