package dev.doughbay.fabric;

import java.util.List;

/** Connection ownership during restart recovery, independent of Minecraft transport. */
final class RestartRoute {
    enum State { IDLE, ARMED, DISCONNECTING, CONNECTING, VERIFYING, RETRY }
    enum Action { NONE, DISCONNECT, CONNECT, WORLD_READY, RELEASED, CANCEL }
    record Result(Action action, String address, String detail) {
        static Result none() { return new Result(Action.NONE, "", ""); }
    }
    record World(long now, boolean inWorld, boolean connected, boolean connecting, boolean joinable,
                 String address, Object level, boolean freshBalance, long limboAt,
                 boolean restartHeld, boolean safeToLeave) { }
    private State state = State.IDLE;
    private List<String> addresses = List.of();
    private Object parkedLevel, verifyingLevel;
    private int index;
    private long stateAt, connectAt, lastDisconnectAt;
    boolean active() { return state != State.IDLE; }
    long connectAt() { return connectAt; }
    void cancel() { state = State.IDLE; }

    void start(List<String> addresses, Object parkedLevel, long now) {
        if (addresses.isEmpty()) throw new IllegalArgumentException("No regional address");
        this.addresses = List.copyOf(addresses);
        this.parkedLevel = parkedLevel;
        index = 0;
        connectAt = 0;
        lastDisconnectAt = 0;
        go(State.ARMED, now);
    }

    Result tick(World w) {
        String target = addresses.isEmpty() ? "" : addresses.get(index);
        if (active() && state != State.ARMED && w.inWorld() && !RestartPlan.sameSite(w.address(), target)) {
            cancel();
            return new Result(Action.CANCEL, "", "another server was opened; yielding to it");
        }
        switch (state) {
            case IDLE -> { return Result.none(); }
            case ARMED -> {
                if (!w.restartHeld() || (w.inWorld() && w.level() != parkedLevel)) {
                    cancel();
                    return new Result(Action.RELEASED, "", "waiting room already released; keeping the connection");
                }
                if (w.now() - stateAt < RestartPlan.ARM_MILLIS || !w.safeToLeave()) return Result.none();
                go(State.DISCONNECTING, w.now());
                lastDisconnectAt = w.now();
                return new Result(Action.DISCONNECT, target, "disconnecting to rejoin through " + target);
            }
            case DISCONNECTING -> {
                if (w.inWorld() || w.connected() || w.connecting()) return disconnectAgain(w, target);
                if (!w.joinable() || w.now() - stateAt < 1_000L) return Result.none();
                return connect(w, target);
            }
            case CONNECTING -> {
                if (w.inWorld()) {
                    if (!RestartPlan.host(w.address()).equals(RestartPlan.host(target))) {
                        cancel();
                        return new Result(Action.CANCEL, "", "another connection was opened; yielding to it");
                    }
                    verifyingLevel = w.level();
                    go(State.VERIFYING, w.now());
                    return Result.none();
                }
                if (w.now() - connectAt >= RestartPlan.CONNECT_TIMEOUT_MILLIS
                        || (!w.connected() && !w.connecting() && w.joinable() && w.now() - connectAt >= 2_000L)) {
                    return retry(w, target + " did not connect");
                }
            }
            case VERIFYING -> {
                if (w.limboAt() > connectAt) return retry(w, target + " put us in the waiting room");
                if (!w.inWorld()) return retry(w, target + " disconnected before the world was verified");
                if (!RestartPlan.host(w.address()).equals(RestartPlan.host(target))) {
                    cancel();
                    return new Result(Action.CANCEL, "", "another connection was opened; yielding to it");
                }
                if (w.level() != verifyingLevel) {
                    verifyingLevel = w.level();
                    go(State.VERIFYING, w.now());
                    return Result.none();
                }
                if (w.freshBalance() && w.now() - stateAt >= RestartPlan.SETTLE_MILLIS) {
                    cancel();
                    return new Result(Action.WORLD_READY, target, "world verified on " + target + "; staying on this region");
                }
                if (w.now() - stateAt >= 60_000L) return retry(w, target + " has no fresh balance sidebar");
            }
            case RETRY -> {
                if (w.inWorld() || w.connected() || w.connecting()) return disconnectAgain(w, target);
                if (!w.joinable() || w.now() - stateAt < RestartPlan.RETRY_MILLIS) return Result.none();
                return connect(w, target);
            }
        }
        return Result.none();
    }
    private Result connect(World w, String target) {
        connectAt = w.now();
        go(State.CONNECTING, w.now());
        return new Result(Action.CONNECT, target, "connecting directly to " + target);
    }
    private Result retry(World w, String reason) {
        index = (index + 1) % addresses.size();
        go(State.RETRY, w.now());
        lastDisconnectAt = w.now();
        return new Result(Action.DISCONNECT, addresses.get(index), reason + "; retrying through " + addresses.get(index));
    }
    private Result disconnectAgain(World w, String target) {
        if (w.now() - lastDisconnectAt < 10_000L) return Result.none();
        lastDisconnectAt = w.now();
        return new Result(Action.DISCONNECT, target, "waiting for the old connection to close");
    }
    private void go(State next, long now) { state = next; stateAt = now; }
}
