package dev.doughbay.fabric.hosted;
/** Bounded read-only retry admission. Never grants permission to trade or bypass a guard. */
public final class IsolatedOrderRetry {
    private boolean pending;
    private int interruptions;
    private long quietSince,deadline,notBefore;
    public void begin(long now) { pending=false;interruptions=0;quietSince=0;deadline=now+30*60_000L; }
    public void waitForGuard(long now) {pending=true;quietSince=0;notBefore=now+15_000;}
    public boolean interrupted(long now) {
        if (++interruptions > 3 || now > deadline) { cancel(); return false; }
        pending=true;quietSince=0;notBefore=now+15_000;return true;
    }
    public boolean ready(long now,boolean connected,boolean safeInventory,boolean guardsClear,boolean operatorHeld) {
        if (!pending) return false;
        if (!operatorHeld || now > deadline) { cancel();return false; }
        if (!connected || !safeInventory || !guardsClear) { quietSince=0;return false; }
        if (quietSince == 0) quietSince=now;
        if (now<notBefore || now-quietSince<15_000) return false;
        pending=false;quietSince=0;return true;
    }
    public boolean pending(){return pending;}
    public int interruptions(){return interruptions;}
    public void cancel(){pending=false;quietSince=0;}
}
