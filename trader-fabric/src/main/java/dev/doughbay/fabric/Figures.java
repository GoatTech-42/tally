package dev.doughbay.fabric;

import java.util.Locale;

/**
 * How the mod writes money, counts and ages, in one place: the screen in game
 * and the web panel show the same figure the same way.
 */
final class Figures {
    private Figures() {
    }

    static String money(double value) {
        if (!Double.isFinite(value)) return "$—";
        String sign = value < 0 ? "-" : "";
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000) {
            return sign + "$" + String.format(Locale.ROOT, "%.2fb", absolute / 1e9);
        }
        if (absolute >= 1_000_000) {
            return sign + "$" + String.format(Locale.ROOT, "%.2fm", absolute / 1e6);
        }
        if (absolute >= 1_000) {
            return sign + "$" + String.format(Locale.ROOT, "%.1fk", absolute / 1e3);
        }
        return sign + "$" + String.format(Locale.ROOT, "%.0f", absolute);
    }

    static String signedMoney(long value) {
        return value > 0 ? "+" + money(value) : money(value);
    }

    static String relativeAge(long millis) {
        long seconds = Math.max(0, millis) / 1_000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        return hours + "h ago";
    }

    static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** An asking price as so many times the order price it is set against. */
    static String askOverOrder(long order, long ask) {
        if (order <= 0 || ask <= 0) return "-";
        double times = ask / (double) order;
        return times >= 10 ? String.format(Locale.ROOT, "x%.0f", times) : String.format(Locale.ROOT, "x%.1f", times);
    }
}
