package dev.doughbay.fabric;

import dev.doughbay.fabric.automation.AutomationSessionController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Disconnects from confirmed restart limbo and reconnects directly through another region. */
final class RestartRecovery {
    private final RestartRoute route = new RestartRoute();
    private volatile long limboSeenAt;
    private Object limboLevel, lastLevel;
    private long levelChangedAt, hoppedUntil, resumeAt;
    private boolean resumeOwed;
    private int resumeTries;

    /** Normal reconnect and evasion flows wait while this owns a connection attempt. */
    boolean active() { return route.active(); }

    void yieldToStaffWatch() {
        route.cancel();
        resumeOwed = false;
        limboSeenAt = 0;
        limboLevel = null;
    }

    void observeChat(String line) {
        if (RestartPlan.saysLimbo(line)) {
            limboSeenAt = System.currentTimeMillis();
            limboLevel = null;
        }
    }

    void tick(Minecraft client, AutomationSessionController session) {
        if (client == null || session == null) return;
        long now = System.currentTimeMillis();
        // Complete teardown on the client tick; a headless client must not retain the old world forever.
        if (route.active() && client.getConnection() != null
                && !client.getConnection().getConnection().isConnected()) {
            client.getConnection().getConnection().handleDisconnection();
        }
        if (client.level != lastLevel) {
            lastLevel = client.level;
            if (client.level != null) levelChangedAt = now;
        }
        if (limboSeenAt > 0 && limboLevel == null && client.level != null
                && now - limboSeenAt >= RestartPlan.PARKED_MILLIS) limboLevel = client.level;
        boolean inWorld = client.player != null && client.level != null && client.getConnection() != null;
        ServerData current = client.getCurrentServer();
        String address = current == null ? "" : current.ip;
        if (route.active() && Tuning.get("restart.hop") < 0.5) {
            resumeOwed = false;
            if (inWorld) {
                route.cancel();
                return;
            }
        }
        if (!route.active()) {
            boolean movedOn = limboLevel != null && inWorld && client.level != limboLevel;
            if (session.serverRestarting() && RestartPlan.backFromLimbo(movedOn, levelChangedAt,
                    SidebarBalance.current() >= 0, now)) {
                session.observeServerAnswered();
                limboSeenAt = 0;
                limboLevel = null;
            }
            if (resumeOwed) {
                resume(session, now);
                return;
            }
            if (Tuning.get("restart.hop") < 0.5 || now < hoppedUntil || !session.serverRestarting()
                    || !inWorld || limboLevel == null || client.level != limboLevel
                    || limboSeenAt == 0 || now - limboSeenAt > 60_000L || current == null) return;
            var addresses = RestartPlan.hops(Tuning.text("restart.hop_addresses"), address);
            if (addresses.isEmpty()) return;
            resumeOwed = session.restartRecoveryMayResume();
            resumeTries = 0;
            hoppedUntil = now + 15 * 60_000L;
            route.start(addresses, limboLevel, now);
        }
        boolean safeToLeave = DoughBayClient.executionDriver().operationIntent()
                == AutomatedExecutionDriver.OperationIntent.NONE
                && (client.player == null || client.player.containerMenu.getCarried().isEmpty());
        var result = route.tick(new RestartRoute.World(now, inWorld, client.getConnection() != null,
                client.gui.screen() instanceof ConnectScreen, joinableFrom(client.gui.screen()), address,
                client.level, SidebarBalance.freshSince(route.connectAt()), limboSeenAt,
                session.serverRestarting(), safeToLeave));
        if (result.action() == RestartRoute.Action.NONE) return;
        ActionTrace.INSTANCE.record(ActionTrace.Scope.RESTART,ActionTrace.Event.RESULT,result.action(),"WORLD_RECOVERY_HOLD",-1,-1,-1);
        DoughBayClient.LOGGER.info("DoughBay restart: {}", result.detail());
        switch (result.action()) {
            case DISCONNECT -> leave(client);
            case CONNECT -> connect(client, result.address());
            case WORLD_READY -> {
                session.observeServerAnswered();
                limboSeenAt = 0;
                limboLevel = null;
                resumeAt = now;
                resumeTries = 0;
            }
            case CANCEL -> {
                resumeOwed = false;
                limboSeenAt = 0;
                limboLevel = null;
            }
            case RELEASED -> resumeAt = now;
            case NONE -> { }
        }
    }

    private void resume(AutomationSessionController session, long now) {
        if (!session.restartRecoveryMayResume()) {
            resumeOwed = false;
            return;
        }
        if (now - resumeAt < 15_000L) return;
        resumeAt = now;
        var result = session.resumeAfterRestart();
        if (result.ok()) {
            DoughBayClient.LOGGER.info("DoughBay restart: session resumed on the regional connection");
            resumeOwed = false;
        } else if (++resumeTries >= 4) {
            DoughBayClient.LOGGER.info("DoughBay restart: session still needs reconciliation ({}); retaining its records and automatic recovery",
                    result.detail());
            resumeOwed = false;
        }
    }

    private static void leave(Minecraft client) {
        try {
            if (client.gui.screen() instanceof ConnectScreen connecting) connecting.onClose();
            if (client.getConnection() != null) {
                client.getConnection().getConnection().disconnect(Component.literal("GoNuts restart"));
            }
        } catch (RuntimeException e) {
            DoughBayClient.LOGGER.warn("DoughBay restart: could not disconnect: {}", e.toString());
        }
    }

    private static void connect(Minecraft client, String address) {
        try {
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(address),
                    new ServerData("DonutSMP", address, ServerData.Type.OTHER), false, null);
        } catch (RuntimeException e) {
            DoughBayClient.LOGGER.warn("DoughBay restart: could not connect to {}: {}", address, e.toString());
        }
    }

    private static boolean joinableFrom(Screen screen) {
        return screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof DisconnectedScreen;
    }
}
