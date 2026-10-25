package dev.doughbay.fabric.staffwatch;

/** Donut's shard-local message completion endpoint; this is never executed as a command. */
public final class StaffCompletionProbe {
    private StaffCompletionProbe() { }
    // Donut ignores requests without the slash even though vanilla accepts them.
    public static final String REQUEST = "/minecraft:msg ";
}
