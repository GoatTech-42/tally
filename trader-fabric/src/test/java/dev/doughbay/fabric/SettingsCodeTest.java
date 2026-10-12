package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsCodeTest {
    private static final String[] TOUCHED = {"slots.max", "orders.bid_slots", "orders.bid_stacks",
            "follow.enabled", "follow.auto", "follow.money", "follow.max", "follow.gap_sec"};

    private interface Body {
        void run() throws Exception;
    }

    /** Runs with the settings the test changes put back afterwards. */
    private static void keeping(Body body) throws Exception {
        double[] before = new double[TOUCHED.length];
        for (int i = 0; i < TOUCHED.length; i++) before[i] = Tuning.get(TOUCHED[i]);
        try {
            body.run();
        } finally {
            for (int i = 0; i < TOUCHED.length; i++) Tuning.set(TOUCHED[i], before[i]);
        }
    }

    @Test
    void aCodeMadeOnNinetySlotsLeavesAFriendOnEighteen() throws Exception {
        keeping(() -> {
            Tuning.set("slots.max", 90);
            Tuning.set("orders.bid_slots", 90);
            Tuning.set("orders.bid_stacks", 11);
            String code = SettingsCode.export();
            assertTrue(code.startsWith("DB1-"));

            // The friend: fewer slots by rank, and his own settings until now.
            Tuning.set("slots.max", 18);
            Tuning.set("orders.bid_slots", 18);
            Tuning.set("orders.bid_stacks", 5);
            SettingsCode.Result result = SettingsCode.apply(code);
            assertTrue(result.ok());
            assertEquals(18, Tuning.get("slots.max"));
            assertEquals(18, Tuning.get("orders.bid_slots"));
            // Everything that is the setup and not the account came across.
            assertEquals(11, Tuning.get("orders.bid_stacks"));
            // The code never had the slot counts in it to begin with.
            assertEquals(0, result.kept());
        });
    }

    @Test
    void aCodeDoesNotCarryWhetherOrWhomAnAccountFollows() throws Exception {
        keeping(() -> {
            // The one who shares: following on, with everything it has turned up.
            Tuning.set("follow.enabled", 1);
            Tuning.set("follow.auto", 1);
            Tuning.set("follow.money", 1);
            Tuning.set("follow.max", 1000);
            Tuning.set("follow.gap_sec", 11);
            Tuning.set("orders.bid_stacks", 11);
            String code = SettingsCode.export();

            // The friend never switched following on, and a pasted code does not switch it on for him.
            Tuning.set("follow.enabled", 0);
            Tuning.set("follow.auto", 0);
            Tuning.set("follow.money", 0);
            Tuning.set("follow.max", 200);
            Tuning.set("follow.gap_sec", 5);
            Tuning.set("orders.bid_stacks", 5);
            SettingsCode.Result result = SettingsCode.apply(code);
            assertTrue(result.ok());
            assertEquals(0, Tuning.get("follow.enabled"));
            assertEquals(0, Tuning.get("follow.auto"));
            assertEquals(0, Tuning.get("follow.money"));
            assertEquals(200, Tuning.get("follow.max"));
            assertEquals(5, Tuning.get("follow.gap_sec"));
            // The trading setup still came across, and the code had no follow settings in it at all.
            assertEquals(11, Tuning.get("orders.bid_stacks"));
            assertEquals(0, result.kept());
        });
    }

    @Test
    void aCodeFromAnOlderBuildThatStillCarriesFollowSettingsDoesNotApplyThem() throws Exception {
        keeping(() -> {
            Tuning.set("follow.enabled", 0);
            Tuning.set("follow.max", 200);
            Tuning.set("orders.bid_stacks", 5);
            String old = SettingsCode.encode(
                    "{\"v\":1,\"n\":{\"follow.enabled\":1,\"follow.max\":1000,\"orders.bid_stacks\":11}}"
                            .getBytes(StandardCharsets.UTF_8));
            SettingsCode.Result result = SettingsCode.apply(old);
            assertTrue(result.ok());
            assertEquals(2, result.kept());
            assertEquals(1, result.applied());
            assertEquals(0, Tuning.get("follow.enabled"));
            assertEquals(200, Tuning.get("follow.max"));
            assertEquals(11, Tuning.get("orders.bid_stacks"));
        });
    }

    @Test
    void aCodeFromAnOlderBuildThatStillCarriesSlotCountsDoesNotApplyThem() throws Exception {
        keeping(() -> {
            Tuning.set("slots.max", 18);
            Tuning.set("orders.bid_slots", 18);
            Tuning.set("orders.bid_stacks", 5);
            String old = SettingsCode.encode(
                    "{\"v\":1,\"n\":{\"slots.max\":90,\"orders.bid_slots\":90,\"orders.bid_stacks\":11}}"
                            .getBytes(StandardCharsets.UTF_8));
            SettingsCode.Result result = SettingsCode.apply(old);
            assertTrue(result.ok());
            assertEquals(2, result.kept());
            assertEquals(1, result.applied());
            assertEquals(18, Tuning.get("slots.max"));
            assertEquals(18, Tuning.get("orders.bid_slots"));
            assertEquals(11, Tuning.get("orders.bid_stacks"));
        });
    }
}
