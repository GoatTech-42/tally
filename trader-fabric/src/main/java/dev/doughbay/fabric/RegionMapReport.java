package dev.doughbay.fabric;

import dev.doughbay.fabric.staffwatch.DonutServer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Only coarse server identity crosses into background renderers. No XYZ in snapshots. */
public final class RegionMapReport {
    public record Snapshot(long at, int area, String status) {
        public String server() { return RegionMapData.name(area); }
        public boolean mapped() { return area >= 0; }
    }
    private static volatile Snapshot current = new Snapshot(0, -1, "Not connected to DonutSMP");
    private RegionMapReport() { }
    public static void tick(Minecraft client) {
        var server = client.getCurrentServer();
        boolean connected = client.getConnection() != null && client.player != null && client.level != null
                && !client.hasSingleplayerServer() && server != null && DonutServer.matches(server.ip);
        current = locate(System.currentTimeMillis(), connected,
                connected && client.level.dimension() == Level.OVERWORLD,
                connected ? client.player.getX() : 0, connected ? client.player.getZ() : 0);
    }
    public static Snapshot locate(long at, boolean connected, boolean overworld, double x, double z) {
        if (!connected) return new Snapshot(at, -1, "Not connected to DonutSMP");
        if (!overworld) return new Snapshot(at, -1, "Region map is available in the Overworld");
        int area = RegionMapData.regionAt(x, z);
        return new Snapshot(at, area, area < 0 ? "Outside the known region map" : "Requires Donut Randomized Coords: OFF");
    }
    public static Snapshot snapshot() {
        var value = current;
        long age = System.currentTimeMillis() - value.at();
        return age < 0 || age > 15_000 ? new Snapshot(0, -1, "Current region unavailable") : value;
    }
    /** Fixed-length dummy characters: scrambling never renders real digits or their length. */
    public static String coordinates(double x, double y, double z, boolean privacy) {
        if (privacy) return "X §k000000§r   Y §k000000§r   Z §k000000§r";
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return "XYZ unavailable";
        return String.format(java.util.Locale.ROOT, "X %.0f   Y %.0f   Z %.0f", x, y, z);
    }
}
