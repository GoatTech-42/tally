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

import java.util.List;

/**
 * Gets a session through a server restart with as little time lost as there
 * has to be.
 *
 * <p>The server parks everyone in a waiting room while it restarts and never
 * says when it lets them go, so the session held for a fixed ten minutes. On
 * 2026-10-13 the world was back after four, and the other six were spent
 * waiting on nothing. Being moved to another world with the money sidebar up
 * is the end of the restart, and the hold is lifted on that.
 *
 * <p>Switched on, it also does not sit in the waiting room at all. The
 * network has regional addresses beside the main one; a client that leaves,
 * joins through one of those and then joins the main address again is put
 * straight into the world instead of the queue. That is three connections in
 * a row and nothing hidden about any of them. The session stays stopped the
 * whole way and is only let go once the world at the home address is proven
 * to be there.
 *
 * <p>Once it has taken a client off the server it never gives up on putting
 * it back: the side doors are tried once each, the home address for as long
 * as it takes.
 */
final class RestartRecovery {
    private enum Step { IDLE, ARMED, TO_HOP, ON_HOP, TO_HOME, HOME_CHECK, RESUMING }

    private Step step = Step.IDLE;
    private long stepAt;
    private long connectAt;
    private ServerData homeServer;
    private String home = "";
    private List<String> hops = List.of();
    private int hopIndex;
    private int homeAttempts;
    private long homeJoinedAt;
    /** No second round of hops for the same restart. */
    private long hoppedUntil;
    /** Whether the session was running until the restart stopped it, so it is this that starts it again. */
    private boolean resumeOwed;
    private int resumeTries;
    private volatile long limboSeenAt;
    private Object lastLevel;
    private long levelChangedAt;

    /** True while this owns the connection: nothing else may join, leave or flee. */
    boolean active() {
        return step != Step.IDLE && step != Step.RESUMING;
    }

    void observeChat(String line) {
        if (RestartPlan.saysLimbo(line)) limboSeenAt = System.currentTimeMillis();
    }

    void tick(Minecraft client, AutomationSessionController session) {
        if (client == null || session == null) return;
        long now = System.currentTimeMillis();
        if (client.level != lastLevel) {
            lastLevel = client.level;
            if (client.level != null) levelChangedAt = now;
        }
        boolean inWorld = client.player != null && client.level != null && client.getConnection() != null;
        ServerData current = client.getCurrentServer();
        String on = inWorld && current != null ? RestartPlan.host(current.ip) : "";
        boolean balance = SidebarBalance.current() >= 0;
        switch (step) {
            case IDLE -> idle(client, session, now, inWorld, current, on, balance);
            case ARMED -> {
                if (now - stepAt < RestartPlan.ARM_MILLIS) return;
                DoughBayClient.LOGGER.info("DoughBay restart: parked for the restart; leaving to come back in through {}",
                        hops.get(hopIndex));
                leave(client);
                go(Step.TO_HOP, now);
            }
            case TO_HOP -> {
                String hop = hops.get(hopIndex);
                if (inWorld && on.equals(RestartPlan.host(hop))) {
                    DoughBayClient.LOGGER.info("DoughBay restart: on {}; going back to {}", hop, home);
                    go(Step.ON_HOP, now);
                    return;
                }
                if (inWorld && now - stepAt > 15_000L) {
                    // Still where we were: the leave did not take. Nothing has been lost; wait it out here.
                    DoughBayClient.LOGGER.info("DoughBay restart: could not leave {}; waiting the restart out", home);
                    go(Step.IDLE, now);
                    return;
                }
                if (inWorld || client.getConnection() != null || !joinableFrom(client.gui.screen())) return;
                if (connectAt == 0) {
                    connect(client, new ServerData("DonutSMP", hop, ServerData.Type.OTHER));
                    connectAt = now;
                } else if (now - connectAt > RestartPlan.CONNECT_TIMEOUT_MILLIS) {
                    DoughBayClient.LOGGER.info("DoughBay restart: {} did not let us in; going back to {}", hop, home);
                    go(Step.TO_HOME, now);
                }
            }
            case ON_HOP -> {
                if (inWorld && now - stepAt < RestartPlan.DWELL_MILLIS) return;
                if (inWorld) leave(client);
                go(Step.TO_HOME, now);
            }
            case TO_HOME -> {
                if (inWorld && on.equals(home)) {
                    // Counted from when the connection was asked for: whatever the
                    // server says on the way in is said about this join.
                    homeJoinedAt = connectAt > 0 ? connectAt : now;
                    go(Step.HOME_CHECK, now);
                    return;
                }
                if (inWorld && now - stepAt > 15_000L) {
                    // Still on the side door: leave it again. Home is the only place this ends.
                    leave(client);
                    stepAt = now;
                    return;
                }
                if (inWorld || client.getConnection() != null || !joinableFrom(client.gui.screen())) return;
                if (connectAt == 0 || now - connectAt > RestartPlan.HOME_RETRY_MILLIS) {
                    homeAttempts++;
                    DoughBayClient.LOGGER.info("DoughBay restart: connecting to {} (attempt {})", home, homeAttempts);
                    connect(client, homeServer != null ? homeServer : new ServerData("DonutSMP", home, ServerData.Type.OTHER));
                    connectAt = now;
                }
            }
            case RESUMING -> resume(session, now);
            case HOME_CHECK -> {
                if (!inWorld) {
                    go(Step.TO_HOME, now);
                    return;
                }
                long said = Math.max(session.restartNoticedAt(), limboSeenAt);
                if (said > homeJoinedAt) {
                    hopIndex++;
                    if (hopIndex < hops.size()) {
                        DoughBayClient.LOGGER.info("DoughBay restart: {} parked us again; trying {}", home, hops.get(hopIndex));
                        go(Step.ARMED, now);
                    } else {
                        DoughBayClient.LOGGER.info("DoughBay restart: {} is still restarting; waiting it out", home);
                        go(Step.IDLE, now);
                    }
                    return;
                }
                if (RestartPlan.homeProven(homeJoinedAt, said, balance, now)) {
                    DoughBayClient.LOGGER.info("DoughBay restart: back on {} with the world there, {} s after leaving; the hold is lifted",
                            home, (now - hoppedUntil + 15 * 60_000L) / 1000);
                    session.observeServerAnswered();
                    limboSeenAt = 0;
                    resumeTries = 0;
                    go(resumeOwed ? Step.RESUMING : Step.IDLE, now);
                } else if (now - homeJoinedAt > 60_000L) {
                    DoughBayClient.LOGGER.info("DoughBay restart: back on {} but no balance shows on the sidebar; "
                            + "the hold runs its time", home);
                    go(Step.IDLE, now);
                }
            }
        }
    }

    /**
     * Starts the session again after a hop. Leaving the server is ordinarily
     * the player's doing and waits for the player; this leaving was the
     * bot's own, around a restart that had stopped a running session, so it
     * is the bot's to undo. Tried a few times, then left to the session.
     */
    private void resume(AutomationSessionController session, long now) {
        if (session.snapshot().state() != AutomationSessionController.State.PAUSED) {
            go(Step.IDLE, now);
            return;
        }
        if (now - stepAt < 15_000L) return;
        stepAt = now;
        var result = session.resumeRecoveredSession(DoughBayClient.continuousPolicy());
        if (!result.ok()) {
            var plain = session.resume();
            if (plain.ok()) result = plain;
        }
        if (result.ok()) {
            DoughBayClient.LOGGER.info("DoughBay restart: session resumed");
            go(Step.IDLE, now);
        } else if (++resumeTries >= 4) {
            DoughBayClient.LOGGER.info("DoughBay restart: the session would not resume ({}); leaving it to its own resume",
                    result.detail());
            go(Step.IDLE, now);
        }
    }

    private void idle(Minecraft client, AutomationSessionController session, long now, boolean inWorld,
                      ServerData current, String on, boolean balance) {
        if (!session.serverRestarting() || !inWorld) return;
        if (RestartPlan.backFromLimbo(limboSeenAt, levelChangedAt, balance, now)) {
            DoughBayClient.LOGGER.info("DoughBay restart: put back in the world {} s after being parked; the hold is lifted",
                    (now - limboSeenAt) / 1000);
            limboSeenAt = 0;
            session.observeServerAnswered();
            return;
        }
        if (Tuning.get("restart.hop") < 0.5 || now < hoppedUntil) return;
        // Only out of the waiting room itself. A warning alone is a restart
        // that may be of some other part of the network.
        if (limboSeenAt == 0 || now - limboSeenAt > 60_000L || current == null) return;
        List<String> doors = RestartPlan.hops(Tuning.text("restart.hop_addresses"), on);
        hoppedUntil = now + 15 * 60_000L;
        if (doors.isEmpty()) {
            DoughBayClient.LOGGER.info("DoughBay restart: no other address of {} to come back in through; waiting it out", on);
            return;
        }
        var snapshot = session.snapshot();
        resumeOwed = snapshot.state() == AutomationSessionController.State.PAUSED && snapshot.detail() != null
                && snapshot.detail().toLowerCase(java.util.Locale.ROOT).contains("server is restarting");
        homeServer = current;
        home = on;
        hops = doors;
        hopIndex = 0;
        homeAttempts = 0;
        go(Step.ARMED, now);
    }

    private void go(Step next, long now) {
        step = next;
        stepAt = now;
        connectAt = 0;
    }

    private static void leave(Minecraft client) {
        try {
            if (client.getConnection() != null) {
                client.getConnection().getConnection().disconnect(Component.literal("GoNuts restart"));
            }
        } catch (RuntimeException e) {
            DoughBayClient.LOGGER.warn("DoughBay restart: could not leave cleanly: {}", e.toString());
        }
    }

    private static void connect(Minecraft client, ServerData server) {
        try {
            ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(server.ip),
                    server, false, null);
        } catch (RuntimeException e) {
            DoughBayClient.LOGGER.warn("DoughBay restart: could not connect to {}: {}", server.ip, e.toString());
        }
    }

    private static boolean joinableFrom(Screen screen) {
        return screen instanceof TitleScreen
                || screen instanceof JoinMultiplayerScreen
                || screen instanceof DisconnectedScreen;
    }
}
