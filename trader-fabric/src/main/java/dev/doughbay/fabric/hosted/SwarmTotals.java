package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Anonymous service totals, shown only while the authenticated relay snapshot is fresh. */
public final class SwarmTotals {
    private SwarmTotals() { }
    private record Snapshot(long at, long expires, Long bots, Long hives, Long markets, Long sales, Long linkedBots) { }
    private static volatile Snapshot current;
    public static void clear() { current = null; }
    static void receive(JsonNode stats, long now) {
        clear();
        if (stats == null || !stats.isObject() || !stats.path("at").isIntegralNumber() || !stats.path("at").canConvertToLong()) return;
        long at = stats.path("at").asLong();
        if (at <= 0 || at > now + 5000 || now - at >= 180_000) return;
        var stored = stats.path("community").path("stored");
        var participants = stored.path("participants");
        current = new Snapshot(at, at+180_000, count(participants.path("activeBots10m")),
                count(participants.path("activeHives10m")),
                count(stored.path("marketsCovered").path("itemsWithOrderLevels24h")),
                count(stored.path("storedRecords").path("events").path("sale")), null);
    }
    static void receiveFrame(JsonNode frame,long now){
        clear();
        if(frame.path("frameVersion").asInt()!=2||!frame.path("at").isIntegralNumber()
                ||!frame.path("at").canConvertToLong()||!frame.path("expiresAt").isIntegralNumber()
                ||!frame.path("expiresAt").canConvertToLong())return;
        long at=frame.path("at").asLong(),expires=frame.path("expiresAt").asLong();
        if(at<=0||at>now+5000||now-at>=120_000||expires<=now||expires>at+120_000)return;
        receive(frame.path("stats"),now);var s=current;
        Long fallback=null;var swarm=frame.path("swarm");long swarmAt=swarm.path("at").asLong();
        if(swarm.path("at").isIntegralNumber()&&swarmAt>0&&swarmAt<=now+5000&&now-swarmAt<120_000)
            fallback=count(swarm.path("active"));
        current=new Snapshot(at,s==null?expires:Math.min(expires,s.expires),
                s!=null&&s.bots!=null?s.bots:fallback,s==null?null:s.hives,
                s==null?null:s.markets,s==null?null:s.sales,count(frame.path("hive")));
    }
    private static Long count(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.asLong() >= 0
                && node.asLong() <= 1_000_000_000_000L ? node.asLong() : null;
    }
    private static String figure(Long value) {
        return value == null ? "Unavailable" : String.format(java.util.Locale.ROOT, "%,d", value);
    }
    public static List<String[]> rows(long now) {
        var s = current;
        if (s == null || now < s.at - 5000 || now >= s.expires)
            return List.<String[]>of(new String[]{"Swarm totals", "Waiting for current service data"});
        var rows=new java.util.ArrayList<String[]>();
        rows.addAll(List.of(new String[]{"Active Hives · last 10 min", figure(s.hives)},
                new String[]{"Active bots · last 10 min", figure(s.bots)},
                new String[]{"Items with shared order levels · 24h", figure(s.markets)},
                new String[]{"Stored sale observations · 7d", figure(s.sales)}));
        if(s.linkedBots!=null)rows.add(new String[]{"Your linked bots",figure(s.linkedBots)});
        return List.copyOf(rows);
    }
}
