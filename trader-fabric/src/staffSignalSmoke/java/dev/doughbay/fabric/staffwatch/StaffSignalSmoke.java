package dev.doughbay.fabric.staffwatch;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.doughbay.fabric.StaffWatchAlert;
import dev.doughbay.fabric.staffwatch.event.impl.PacketReceiveNettyEvent;
import dev.doughbay.fabric.staffwatch.module.ModuleRegistry;
import dev.doughbay.fabric.staffwatch.module.impl.misc.AntiVanishModule;
import dev.doughbay.fabric.staffwatch.module.impl.misc.StaffListModule;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Offline disposable-world replay. Never loaded or packaged in a normal build. */
public final class StaffSignalSmoke implements ClientModInitializer {
    private final UUID staff = UUID.fromString("420f305e-411a-4bdf-8c3d-91797a6d2c21");
    private final String name = StaffListModule.DONUT_STAFF.getFirst();
    private final List<String> checks = new ArrayList<>();
    private final Set<Integer> roundTripReplies = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final long started = System.nanoTime();
    private int phase, ticks;
    public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks.add("PASS: " + label);
        System.out.println("STAFF_SIGNAL_REPLAY PASS: " + label);
    }
    private void next() { phase++; ticks = 0; }
    private void update(Minecraft mc, GameType mode, EnumSet<Action> actions) throws Exception {
        var packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        var entries = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
        entries.setAccessible(true);
        entries.set(packet, List.of(new Entry(staff, new GameProfile(staff, name), true, 0,
                mode, Component.literal(name), false, 0, null)));
        PacketReceiveNettyEvent.dispatchSync(packet);
        mc.getConnection().handlePlayerInfoUpdate(packet);
    }
    private StaffListModule.HudRow row() {
        return StaffListModule.hudRows().rows().stream().filter(r -> r.id().equals(staff)).findFirst().orElseThrow();
    }
    private void tick(Minecraft mc) {
        if (phase == 99) return;
        try {
            if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Replay timeout");
            ticks++;
            if (mc.gui.overlay() != null) return;
            if (phase == 0) {
                if (ticks < 60) return;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                WatchConfig.get().enabled = true;
                WatchConfig.get().staffList = true;
                WatchConfig.get().antiVanish = true;
                WatchConfig.get().alertSounds = false;
                WatchConfig.get().apply();
                mc.createWorldOpenFlows().createFreshLevel("signal-replay-" + System.currentTimeMillis(),
                    new LevelSettings("Offline staff signal replay", GameType.CREATIVE,
                        LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2611211L, false, false),
                    r -> r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.gui.screen());
                next(); return;
            }
            if (mc.level == null || mc.player == null) return;
            switch (phase) {
                case 1 -> {
                    if (ticks < 90) return;
                    check(mc.hasSingleplayerServer(), "isolated single-player world; no public server connection");
                    update(mc, GameType.SURVIVAL, EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_GAME_MODE, Action.UPDATE_LISTED));
                    next();
                }
                case 2 -> {
                    if (ticks < 30) return;
                    check(row().presence() == StaffListModule.Presence.ONLINE, "registered packet listener recognizes known staff");
                    check(!row().sameServer() && !StaffWatchAlert.active(), "global TAB entry does not become a local watcher alert");
                    update(mc, GameType.SPECTATOR, EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_GAME_MODE, Action.UPDATE_LISTED));
                    next();
                }
                case 3 -> {
                    if (ticks < 30) return;
                    check(row().presence() == StaffListModule.Presence.SPECTATOR, "global spectator state is visible");
                    check(!row().sameServer() && !StaffWatchAlert.active(), "global spectator entry alone does not prove same-server presence");
                    update(mc, GameType.SPECTATOR, EnumSet.of(Action.UPDATE_GAME_MODE));
                    next();
                }
                case 4 -> {
                    if (ticks < 20) return;
                    check(row().sameServer(), "standalone mode update establishes detector locality");
                    check(StaffListModule.alarmedRecently(staff), "real detector queues watcher alert");
                    check(StaffWatchAlert.active(), "real detector raises the warning banner without directly invoking alert presentation");
                    check(!ReconnectController.INSTANCE.schedule.active(), "single-player replay does not disconnect or schedule a public join");
                    var module = ModuleRegistry.get(AntiVanishModule.class);
                    int request = 39999;
                    try {
                        var field = AntiVanishModule.class.getDeclaredField("completionInbox"); field.setAccessible(true);
                        var inbox = field.get(module);
                        inbox.getClass().getMethod("register", int.class).invoke(inbox, request);
                    } catch (NoSuchFieldException baseline) {
                        var field = AntiVanishModule.class.getDeclaredField("completionRequestIds"); field.setAccessible(true);
                        ((Set<Integer>) field.get(module)).add(request);
                    }
                    var range = StringRange.at(0);
                    check(PacketReceiveNettyEvent.dispatchSync(new ClientboundCommandSuggestionsPacket(request,
                        new Suggestions(range, List.of(new Suggestion(range, StaffListModule.DONUT_STAFF.get(1)))))),
                        "owned completion reply passes through registered packet hook");
                    next();
                }
                case 5 -> {
                    if (ticks < 20) return;
                    check(AntiVanishModule.hudEntries().stream().anyMatch(e -> e.name().equals(StaffListModule.DONUT_STAFF.get(1))),
                        "matching probe reply reaches hidden-staff detector output");
                    var range = StringRange.at(0);
                    check(!PacketReceiveNettyEvent.dispatchSync(new ClientboundCommandSuggestionsPacket(29999,
                        new Suggestions(range, List.of(new Suggestion(range, "UnrelatedFixture"))))),
                        "unowned completion reply remains available to vanilla");
                    PacketReceiveNettyEvent.TYPE.listen(this, event -> {
                        if (event.packet instanceof ClientboundCommandSuggestionsPacket reply
                                && (reply.id() == 60001 || reply.id() == 60002 || reply.id() == 60003)) roundTripReplies.add(reply.id());
                    });
                    mc.getConnection().send(new ServerboundCommandSuggestionPacket(60001, "minecraft:msg "));
                    mc.getConnection().send(new ServerboundCommandSuggestionPacket(60002, "/msg "));
                    mc.getConnection().send(new ServerboundCommandSuggestionPacket(60003, StaffCompletionProbe.REQUEST));
                    next();
                }
                case 6 -> {
                    if (ticks < 60) return;
                    check(roundTripReplies.contains(60001), "actual vanilla server round trip returns a reply to the existing probe text");
                    check(roundTripReplies.contains(60002), "actual vanilla server round trip returns a reply to normal slash command completion");
                    check(roundTripReplies.contains(60003), "actual vanilla server round trip returns a reply to the repaired production request");
                    Files.write(Path.of("staff-signal-replay-pass.txt"), checks);
                    phase = 99; mc.stop();
                }
            }
        } catch (Throwable failure) {
            try { Files.writeString(Path.of("staff-signal-replay-fail.txt"), failure.toString()); } catch (Exception ignored) { }
            failure.printStackTrace(); phase = 99; mc.stop();
        }
    }
}
