package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SweepPickTest {
    private static final long EVERY = 45 * 60_000L;

    @Test
    void aLongWatchListGetsOneTurnInFourAndNoMore() {
        Set<String> core = Set.of("minecraft:diamondx64", "minecraft:emerald_blockx64", "minecraft:gunpowderx64");
        List<String> keys = new ArrayList<>(core);
        // Everything a player could tick, none of it ever checked.
        for (int i = 0; i < 1_500; i++) keys.add("minecraft:junk_" + i + "x64");
        Map<String, Long> swept = new HashMap<>();
        long now = 10 * EVERY;
        int coreTurns = 0;
        for (long turn = 0; turn < 400; turn++) {
            String pick = AutomationSessionController.sweepPick(keys, core, swept, now, EVERY, turn);
            if (core.contains(pick)) coreTurns++;
            swept.put(pick, now);
            now += 47_000L;
        }
        // The three real markets come due again every forty-five minutes and are checked each time they do:
        // five and a quarter hours is seven rounds of three. Checked in one line they got one round and no more.
        assertEquals(21, coreTurns);
        // And the rest still moves along, with every other turn.
        assertEquals(379, swept.size() - core.size());
    }

    @Test
    void theMarketsTheBotIsInComeFirstOldestFirst() {
        Set<String> core = Set.of("a", "b");
        List<String> keys = List.of("x", "a", "b", "y");
        Map<String, Long> swept = new HashMap<>(Map.of("a", 5_000L, "b", 1_000L));
        long now = 5_000L + EVERY + 1;
        assertEquals("b", AutomationSessionController.sweepPick(keys, core, swept, now, EVERY, 0));
        // The fourth turn is the watch list's.
        assertEquals("x", AutomationSessionController.sweepPick(keys, core, swept, now, EVERY, 3));
    }

    @Test
    void aTurnNobodyElseCanUseIsNotWasted() {
        long now = 100 * EVERY;
        long lately = now - 1_000L;
        // Nothing of the bot's own is due: the watch list takes the turn.
        assertEquals("x", AutomationSessionController.sweepPick(List.of("x", "a"), Set.of("a"),
                new HashMap<>(Map.of("a", lately)), now, EVERY, 0));
        // The watch list's turn and nothing of it is due: a market of the bot's own takes it.
        assertEquals("a", AutomationSessionController.sweepPick(List.of("x", "a"), Set.of("a"),
                new HashMap<>(Map.of("x", lately)), now, EVERY, 3));
        // A fresh install has no markets of its own yet: its starting list is what is priced.
        assertEquals("x", AutomationSessionController.sweepPick(List.of("x", "y"), Set.of(), new HashMap<>(), now, EVERY, 0));
        // Everything checked lately.
        assertNull(AutomationSessionController.sweepPick(List.of("x", "a"), Set.of("a"),
                new HashMap<>(Map.of("x", lately, "a", lately)), now, EVERY, 0));
    }
}
