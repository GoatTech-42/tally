package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;

/** Bounded live detector projection. No local history, reporter identity, coordinates or game actions. */
public final class SwarmStaffSightings {
    public static final SwarmStaffSightings INSTANCE = new SwarmStaffSightings();
    static final long TTL = 15 * 60_000L;
    static final int LIMIT = 20;
    private record Sighting(String staff, String region, String event, long observedAt) {}
    private final LinkedHashMap<String,Sighting> sightings = new LinkedHashMap<>();
    private static final Set<String> EVENTS = Set.of("OBSERVED", "SPECTATOR", "NEARBY", "MODE_CHANGED");

    public synchronized void observe(String staff, String serverLabel, String event, long at,
                                     boolean sameRegion, boolean participating, long now) {
        if (!participating) { clear(); return; }
        expire(now);
        if (!sameRegion || staff == null || !staff.matches("[A-Za-z0-9_]{1,16}")
                || !EVENTS.contains(event == null ? "" : event) || at <= 0 || at > now || now - at > TTL) return;
        String region = broadRegion(serverLabel);
        String key = staff.toLowerCase(Locale.ROOT) + "|" + region;
        var previous = sightings.get(key);
        if (previous != null && previous.observedAt() >= at) return;
        sightings.remove(key);
        sightings.put(key, new Sighting(staff, region, event, at));
        while (sightings.size() > LIMIT) sightings.remove(sightings.keySet().iterator().next());
    }

    public synchronized ArrayNode snapshot(boolean participating, long now) {
        if (!participating) clear();
        expire(now);
        var rows = DashboardProtocol.JSON.createArrayNode();
        sightings.values().stream().sorted(Comparator.comparingLong(Sighting::observedAt).reversed())
                .forEach(s -> rows.addObject().put("staff", s.staff()).put("region", s.region())
                        .put("event", s.event()).put("observedAt", s.observedAt()));
        return rows;
    }

    public synchronized void clear() { sightings.clear(); }
    private void expire(long now) {
        sightings.values().removeIf(s -> now < s.observedAt() || now - s.observedAt() > TTL);
    }
    static String broadRegion(String label) {
        if (label == null) return "unknown";
        // Input is the explicit server-provided shard label, never a map-cell estimate.
        var match = java.util.regex.Pattern.compile("^(na-west|na-east|eu-west|eu-central|asia|oceania|europe)-[0-9]{1,3}$")
                .matcher(label);
        return match.matches() ? match.group(1) : "unknown";
    }
}
