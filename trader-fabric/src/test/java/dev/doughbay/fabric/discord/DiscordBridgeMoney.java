package dev.doughbay.fabric.discord;

/** A way for a test outside the package to reach the panel's money format. */
public final class DiscordBridgeMoney {
    private DiscordBridgeMoney() {
    }

    public static String of(long value) {
        return DiscordBridge.money(value);
    }
}
