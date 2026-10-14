package dev.doughbay.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FollowNoticePipelineTest {
    @Test void englishServerNamesResolveAlongsideLocalizedAndResourcePackNames() {
        Map<String, String> names = new HashMap<>();
        ItemNames.indexNames(names, "minecraft:emerald_block", "block.minecraft.emerald_block", "Bloc d'émeraude");
        assertEquals("minecraft:emerald_block", ItemNames.resolve("Blocks of Emerald", names));
        assertEquals("minecraft:emerald_block", ItemNames.resolve("Bloc d'émeraude", names));
        assertNull(ItemNames.resolve("Made Up Item", names));
    }

    @Test void rawEnglishChatProducesRivalEvenWithLocalizedItemNames() {
        Map<String, String> names = new HashMap<>();
        ItemNames.indexNames(names, "minecraft:emerald_block", "block.minecraft.emerald_block", "Bloc d'émeraude");
        long now = 1_800_000_000_000L;
        List<Follows.Seen> seen = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String line = i == 0 ? "TraderOne ordered 192 Blocks of Emerald for $ 1.2M"
                    : "TraderOne listed 64 Blocks of Emerald for $ 582K";
            var event = Follows.parse(line);
            assertNotNull(event);
            seen.add(Follows.seen(now - (8-i)*60_000L, event, item -> ItemNames.resolve(item, names)));
        }
        var build = RivalIntel.fromFollows(seen, Set.of(), "me", now, Set.of(), 3, 25, 60);
        assertEquals(1, build.rivals().size());
        assertEquals(7, build.rivals().getFirst().sales(), "listings, not confirmed sales");
        assertEquals(7, build.rivals().getFirst().flips().size());
        assertTrue(build.rivals().getFirst().flips().stream().allMatch(f -> f.soldPrice() == 0));
        assertTrue(RivalIntel.followDataStatus(seen).contains("8 resolved trades"));
    }

    @Test void emptyProfilesExplainUnresolvedItemsAndRequiredEvidence() {
        var seen = List.of(new Follows.Seen(1, "TraderOne", "listed", 64, "", 1000));
        String status = RivalIntel.followDataStatus(seen);
        assertTrue(status.contains("1 chat notices"));
        assertTrue(status.contains("1 unresolved item names"));
        assertTrue(status.contains("8 orders/listings per player"));
        assertTrue(RivalIntel.fromFollows(seen, Set.of(), "me", 1, Set.of(), 3, 25, 60).rivals().isEmpty());
    }
}
