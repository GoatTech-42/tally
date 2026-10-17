package dev.doughbay.fabric;

/** Transport activity only; neither an enrollment indicator nor proof of usable market data. */
public final class SwarmActivity {
    private static volatile long sentAt, receivedAt;
    private SwarmActivity() { }
    public static void sent() { sentAt = System.currentTimeMillis(); }
    public static void received() { receivedAt = System.currentTimeMillis(); }
    public static long sentAt() { return sentAt; }
    public static long receivedAt() { return receivedAt; }
    static boolean recent(long event, long now) { return event > 0 && now >= event && now - event < 2400; }
    static double radius(long event, long now, boolean receiving, boolean still) {
        if (!recent(event, now)) return 0;
        double phase = still ? .5 : ((now - event) % 800) / 800.0;
        return 20 + 28 * (receiving ? 1 - phase : phase);
    }
    static void draw(MascotFace.Canvas canvas, long now, boolean still) {
        wave(canvas, sentAt, now, false, still, 0xFFFFD36C);
        wave(canvas, receivedAt, now, true, still, 0xFF4FF1E3);
    }
    private static void wave(MascotFace.Canvas c, long event, long now, boolean receiving, boolean still, int color) {
        double r = radius(event, now, receiving, still);
        if (r == 0) return;
        // Opposite sides preserve both directions when a fast exchange overlaps.
        int direction = receiving ? -1 : 1;
        for (int degrees = -55; degrees <= 55; degrees += 3) {
            double angle = Math.toRadians(degrees);
            int x = (int)Math.round(128 + direction * r * Math.cos(angle));
            int y = (int)Math.round(42 + r * Math.sin(angle));
            c.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }
}
