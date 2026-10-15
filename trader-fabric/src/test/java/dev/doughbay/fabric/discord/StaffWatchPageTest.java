package dev.doughbay.fabric.discord;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import java.nio.file.*;
import dev.doughbay.fabric.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffWatchPageTest {
    @Test void rendersSharedHistoryAndUnknownPeriodsWithoutNetworkRequests() throws Exception {
        long at=Instant.parse("2026-10-15T05:00:00Z").toEpochMilli();
        var snapshot=new StaffWatchReport.Snapshot(at,true,true,List.of(new StaffWatchReport.Row("DemoMod","SPECTATOR",true,false,"DemoAlt","Unknown")),
            List.of(new StaffWatchReport.Sighting(at,"DemoMod",StaffWatchReport.Event.SPECTATOR,true,"Unknown","DemoAlt")),"Unknown",
            List.of(new StaffActivityMetrics.Summary("DemoMod",3,7_200_000,at,"Insufficient coverage")),18_000_000,2,"");
        var page=DiscordBridge.staffPageFor(snapshot,at,"PREVIEW — demo data",ZoneId.of("America/Los_Angeles"));
        assertEquals("Staff Watch",page.title());assertTrue(page.rows().stream().anyMatch(r->Arrays.asList(r).contains("Spectator observed")));
        assertTrue(page.rows().stream().anyMatch(r->Arrays.asList(r).contains("Insufficient coverage")));
        byte[] image=StatsImage.render(page);assertTrue(image.length>1000);
        Path directory=Path.of("build/staff-watch-preview");Files.createDirectories(directory);Files.write(directory.resolve("discord.png"),image);
        var stale=DiscordBridge.staffPageFor(snapshot,at+20_000,"Preview",ZoneId.of("UTC"));
        assertFalse(stale.rows().stream().anyMatch(r->r[0].equals("Now")));assertTrue(stale.rows().stream().anyMatch(r->r[0].equals("History")));
    }
}
