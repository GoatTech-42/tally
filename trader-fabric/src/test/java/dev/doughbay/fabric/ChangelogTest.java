package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangelogTest {
    private static final String NOTES = """
            # GoNuts

            Market analysis for the auction house.

            ## Install

            - Put the jar in your mods folder.

            ## 2026-10-11 10:15 UTC
            - New: the update banner sits **top centre**
            - New: `Preview update notice` on the About tab,
              so the notice can be looked at any time

            ## 2026-10-11 12:20 UTC
            - Fixed: the open key is heard over the bot's pages

            ## Downloads

            - The mod jar is the one most people want.
            """;

    @Test
    void theDatedEntriesAreReadAndTheRestOfTheNotesPassedOver() {
        List<Changelog.Entry> entries = Changelog.parse(NOTES);
        assertEquals(2, entries.size());
        // The newest first, whatever order they were written in.
        assertEquals(Instant.parse("2026-10-11T12:20:00Z").toEpochMilli(), entries.get(0).at());
        assertEquals(List.of("Fixed: the open key is heard over the bot's pages"), entries.get(0).lines());
        assertEquals(Instant.parse("2026-10-11T10:15:00Z").toEpochMilli(), entries.get(1).at());
        assertEquals(List.of("New: the update banner sits top centre",
                "New: Preview update notice on the About tab, so the notice can be looked at any time"),
                entries.get(1).lines());
    }

    @Test
    void whatABuildHasNotGotYetIsEverythingWrittenAfterItWasMade() {
        List<Changelog.Entry> entries = Changelog.parse(NOTES);
        long builtBetween = Instant.parse("2026-10-11T10:17:00Z").toEpochMilli();
        List<Changelog.Entry> news = Changelog.after(entries, builtBetween);
        assertEquals(1, news.size());
        assertTrue(news.get(0).lines().get(0).startsWith("Fixed"));
        // A build made after the last entry has it all; one from before any of them has none of it.
        assertEquals(0, Changelog.after(entries, Instant.parse("2026-10-11T12:21:00Z").toEpochMilli()).size());
        assertEquals(2, Changelog.after(entries, 0).size());
    }

    @Test
    void anEntryIsShownAtThePlayersOwnTime() {
        Changelog.Entry entry = Changelog.parse("## 2026-10-11 12:20 UTC\n- Fixed: a thing\n").get(0);
        assertEquals("Oct 11, 5:20 AM", entry.when(ZoneId.of("America/Los_Angeles")));
        assertEquals("Oct 11, 12:20 PM", entry.when(ZoneId.of("UTC")));
    }

    @Test
    void notesWithNoEntriesAndEntriesWithNoLinesAreNothing() {
        assertEquals(0, Changelog.parse(null).size());
        assertEquals(0, Changelog.parse("").size());
        assertEquals(0, Changelog.parse("# Title\n\n- a bullet under no dated heading\n").size());
        assertEquals(0, Changelog.parse("## 2026-10-11 12:20 UTC\n\n## Downloads\n- the jar\n").size());
        // A heading that only looks like a date is not one.
        assertEquals(0, Changelog.parse("## 2026-13-45 99:99 UTC\n- nonsense\n").size());
    }
}
