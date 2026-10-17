package dev.doughbay.fabric.hosted;
/** Read-only admission never authorizes a recovery, trade or resume. */
public final class IsolatedOrderTestGate {
    private IsolatedOrderTestGate() { }
    public static boolean canStart(String state,String intent,boolean running,boolean unsafeInventory,boolean staffHold,boolean operatorStopped) {
        return operatorStopped && ("STOPPED".equals(state)||"PAUSED".equals(state))
                && "NONE".equals(intent) && !running && !unsafeInventory && !staffHold;
    }
}
