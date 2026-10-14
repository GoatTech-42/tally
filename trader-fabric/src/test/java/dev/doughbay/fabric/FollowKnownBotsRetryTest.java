package dev.doughbay.fabric;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FollowKnownBotsRetryTest {
    @TempDir Path directory;

    @Test void clickingAgainRestartsAStoppedQueueEvenWhenNamesAreAlreadyListed() throws Exception {
        double enabled = Tuning.get("follow.enabled"), cap = Tuning.get("follow.max");
        try {
            var follows = new Follows(directory, new AutomatedExecutionDriver());
            follows.addKnownBots();
            field("stopped").set(follows, "3 follows in a row went unanswered");
            state(follows).put(Follows.knownBots().getFirst().toLowerCase(), "NO_REPLY");
            assertEquals(0, follows.followKnownBots());
            assertFalse(follows.status().contains("stopped:"));
            assertFalse(state(follows).containsKey(Follows.knownBots().getFirst().toLowerCase()));
        } finally { Tuning.set("follow.enabled", enabled); Tuning.set("follow.max", cap); }
    }

    @Test void existingFollowersAreIncludedWhenRaisingTheCap() throws Exception {
        double enabled = Tuning.get("follow.enabled"), cap = Tuning.get("follow.max");
        try {
            Tuning.set("follow.max", 50);
            var follows = new Follows(directory, new AutomatedExecutionDriver());
            for (int i = 0; i < 600; i++) state(follows).put("prior_trader_" + i, "FOLLOWED");
            follows.followKnownBots();
            assertEquals(1000, Tuning.get("follow.max"));
        } finally { Tuning.set("follow.enabled", enabled); Tuning.set("follow.max", cap); }
    }

    @Test void explicitKnownBotsComeBeforeAutomaticTraders() throws Exception {
        double enabled = Tuning.get("follow.enabled"), cap = Tuning.get("follow.max");
        try {
            var follows = new Follows(directory, new AutomatedExecutionDriver());
            follows.request(List.of("fresh_counterparty"));
            follows.followKnownBots();
            assertEquals(Follows.knownBots().getFirst(), follows.nextRequestedName());
        } finally { Tuning.set("follow.enabled", enabled); Tuning.set("follow.max", cap); }
    }

    @Test void confirmedNamesAndAnInFlightFollowAreNotRequeued() throws Exception {
        double enabled = Tuning.get("follow.enabled"), cap = Tuning.get("follow.max");
        try {
            var follows = new Follows(directory, new AutomatedExecutionDriver());
            String confirmed = Follows.knownBots().get(0), pending = Follows.knownBots().get(1);
            state(follows).put(confirmed.toLowerCase(), "FOLLOWED");
            field("pending").set(follows, pending);
            follows.followKnownBots();
            follows.followKnownBots();
            assertEquals("FOLLOWED", state(follows).get(confirmed.toLowerCase()));
            assertEquals(pending, field("pending").get(follows));
            assertEquals(Follows.knownBots().get(2), follows.nextRequestedName());
        } finally { Tuning.set("follow.enabled", enabled); Tuning.set("follow.max", cap); }
    }

    @Test void capCountsOverlappingNamesOnlyOnceAndNeverLowersIt() throws Exception {
        double enabled = Tuning.get("follow.enabled"), cap = Tuning.get("follow.max");
        try {
            Tuning.set("follow.max", 50);
            var follows = new Follows(directory, new AutomatedExecutionDriver());
            for (String name : Follows.knownBots()) state(follows).put(name.toLowerCase(), "FOLLOWED");
            follows.followKnownBots();
            assertEquals(500, Tuning.get("follow.max"));
            Tuning.set("follow.max", 900);
            follows.followKnownBots();
            assertEquals(900, Tuning.get("follow.max"));
        } finally { Tuning.set("follow.enabled", enabled); Tuning.set("follow.max", cap); }
    }

    @Test void hiveAccountsDoNotInheritEachOthersFollowConfirmations() throws Exception {
        Path main = Files.createDirectories(directory.resolve("accounts/main"));
        Path alt = Files.createDirectories(directory.resolve("accounts/alt"));
        String bot = Follows.knownBots().getFirst();
        Files.writeString(directory.resolve("follow-state.tsv"), bot + "\tFOLLOWED\t1\n");
        Files.writeString(main.resolve("follow-state.tsv"), bot + "\tFOLLOWED\t2\n");
        var mainFollows = new Follows(directory, main, new AutomatedExecutionDriver());
        var altFollows = new Follows(directory, alt, new AutomatedExecutionDriver());
        assertEquals("FOLLOWED", state(mainFollows).get(bot.toLowerCase()));
        assertFalse(state(altFollows).containsKey(bot.toLowerCase()), "a shared legacy record cannot prove this alt followed anyone");
        assertTrue(Files.exists(directory.resolve("follow-state.tsv")), "preserve the legacy record");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> state(Follows follows) throws Exception {
        return (Map<String, String>) field("state").get(follows);
    }

    private static Field field(String name) throws Exception {
        Field field = Follows.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
