package dev.doughbay.fabric.staffwatch;

import java.util.Locale;

/** Limits reconnect ownership to the intended network. */
public final class DonutServer {
    private DonutServer() { }

    public static boolean matches(String address) {
        if (address == null) return false;
        String host = address.strip().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals(WatchConfig.SERVER_ADDRESS) || host.endsWith("." + WatchConfig.SERVER_ADDRESS);
    }
}
