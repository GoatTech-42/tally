package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPagesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] NUMBERS = {"buy.purchase_cap_pct", "buy.scale_with_bank", "watch.tracked_managed",
            "underdog.enabled"};
    private static final String[] TEXTS = {"webhook.url", "items.allow", "items.deny", "watch.tracked"};

    private interface Body {
        void run() throws Exception;
    }

    /** Tuning is one set of values for the whole run; every test here puts back what it moved. */
    private static void keeping(Body body) throws Exception {
        double[] numbers = new double[NUMBERS.length];
        String[] texts = new String[TEXTS.length];
        for (int i = 0; i < NUMBERS.length; i++) numbers[i] = Tuning.get(NUMBERS[i]);
        for (int i = 0; i < TEXTS.length; i++) texts[i] = Tuning.text(TEXTS[i]);
        try {
            body.run();
        } finally {
            for (int i = 0; i < NUMBERS.length; i++) Tuning.set(NUMBERS[i], numbers[i]);
            for (int i = 0; i < TEXTS.length; i++) Tuning.setText(TEXTS[i], texts[i]);
        }
    }

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    private static JsonNode row(JsonNode settings, String key) {
        for (JsonNode group : settings.path("groups")) {
            for (JsonNode row : group.path("settings")) if (row.path("key").asText().equals(key)) return row;
        }
        for (JsonNode row : settings.path("lists")) if (row.path("key").asText().equals(key)) return row;
        throw new AssertionError(key + " is not on the page");
    }

    @Test
    void theSettingsPageHasEverySettingTheScreenHas() throws Exception {
        JsonNode page = json(WebPages.settings());
        int rows = 0;
        for (JsonNode group : page.path("groups")) rows += group.path("settings").size();
        assertEquals(Tuning.SETTINGS.size(), rows);
        assertEquals(Tuning.groups().size(), page.path("groups").size());
        assertEquals(Tuning.LISTS.size(), page.path("lists").size());
        assertEquals("Default", page.path("presets").get(0).asText());
        JsonNode cap = row(page, "buy.purchase_cap_pct");
        assertEquals("Per-purchase cap", cap.path("label").asText());
        assertEquals("%", cap.path("unit").asText());
        assertEquals(0.5, cap.path("min").asDouble());
        assertEquals(50, cap.path("max").asDouble());
        assertFalse(cap.path("toggle").asBoolean());
        assertTrue(row(page, "buy.scale_with_bank").path("toggle").asBoolean());
    }

    @Test
    void theWebhookIsNeverSentToABrowserAndCannotBeSetFromOne() throws Exception {
        keeping(() -> {
            Tuning.setText("webhook.url", "https://hooks.example.invalid/api/secret-token");
            String page = WebPages.settings();
            assertFalse(page.contains("example.invalid"));
            assertFalse(page.contains("secret-token"));
            JsonNode hook = row(json(page), "webhook.url");
            assertTrue(hook.path("locked").asBoolean());
            assertEquals("set", hook.path("shown").asText());
            assertFalse(hook.has("value"));

            JsonNode refused = json(WebPages.list(Map.of("key", "webhook.url", "text", "https://elsewhere.invalid/x")));
            assertFalse(refused.path("ok").asBoolean());
            assertEquals("https://hooks.example.invalid/api/secret-token", Tuning.text("webhook.url"));

            Tuning.setText("webhook.url", "");
            assertEquals("not set", row(json(WebPages.settings()), "webhook.url").path("shown").asText());
        });
    }

    @Test
    void aSettingIsSetResetAndHeldToItsLimits() throws Exception {
        keeping(() -> {
            JsonNode set = json(WebPages.setting(Map.of("key", "buy.purchase_cap_pct", "value", "30%")));
            assertTrue(set.path("ok").asBoolean());
            assertEquals("Per-purchase cap set to 30%", set.path("note").asText());
            assertEquals(30, Tuning.get("buy.purchase_cap_pct"));
            assertTrue(row(json(WebPages.settings()), "buy.purchase_cap_pct").path("changed").asBoolean());

            // Past the top of its range it is held at the top, and the page is told so.
            JsonNode over = json(WebPages.setting(Map.of("key", "buy.purchase_cap_pct", "value", "400")));
            assertTrue(over.path("ok").asBoolean());
            assertEquals(50, Tuning.get("buy.purchase_cap_pct"));
            assertTrue(over.path("note").asText().endsWith("the most it can be"));

            JsonNode words = json(WebPages.setting(Map.of("key", "buy.purchase_cap_pct", "value", "lots")));
            assertFalse(words.path("ok").asBoolean());
            assertEquals(50, Tuning.get("buy.purchase_cap_pct"));

            assertTrue(json(WebPages.setting(Map.of("key", "buy.purchase_cap_pct", "reset", "1"))).path("ok").asBoolean());
            assertTrue(Tuning.isDefault("buy.purchase_cap_pct"));

            assertFalse(json(WebPages.setting(Map.of("key", "no.such.setting", "value", "1"))).path("ok").asBoolean());
        });
    }

    @Test
    void aSwitchFlipsAndTakesTheWordsOnAndOff() throws Exception {
        keeping(() -> {
            Tuning.set("buy.scale_with_bank", 1);
            assertTrue(json(WebPages.setting(Map.of("key", "buy.scale_with_bank", "toggle", "1"))).path("ok").asBoolean());
            assertEquals(0, Tuning.get("buy.scale_with_bank"));
            WebPages.setting(Map.of("key", "buy.scale_with_bank", "value", "on"));
            assertEquals(1, Tuning.get("buy.scale_with_bank"));
            WebPages.setting(Map.of("key", "buy.scale_with_bank", "value", "off"));
            assertEquals(0, Tuning.get("buy.scale_with_bank"));
            // "toggle" on a setting that is a number is not a way to set it to one.
            Tuning.set("buy.purchase_cap_pct", 14);
            assertFalse(json(WebPages.setting(Map.of("key", "buy.purchase_cap_pct", "toggle", "1"))).path("ok").asBoolean());
            assertEquals(14, Tuning.get("buy.purchase_cap_pct"));
        });
    }

    @Test
    void whatIsTypedIsReadAsTheNumberItMeans() {
        assertEquals(30.0, WebPages.typed("30%", false));
        assertEquals(2.5, WebPages.typed(" 2.5 s ", false));
        assertEquals(1500.0, WebPages.typed("1,500", false));
        assertEquals(-3.0, WebPages.typed("-3", false));
        assertEquals(1.0, WebPages.typed("ON", true));
        assertEquals(0.0, WebPages.typed("off", true));
        assertEquals(null, WebPages.typed("on", false));
        assertEquals(null, WebPages.typed("", false));
        assertEquals(null, WebPages.typed("1.2.3", false));
        assertEquals(null, WebPages.typed(null, false));
    }

    @Test
    void tickedItemsAreMovedOntoTheListsAndThePageIsToldTheNewLists() throws Exception {
        keeping(() -> {
            // The watch list already edited once, so the config file has no say in it.
            Tuning.set("watch.tracked_managed", 1);
            Tuning.setText("watch.tracked", "diamond");
            Tuning.setText("items.allow", "diamond, emerald");
            Tuning.setText("items.deny", "");

            JsonNode out = json(WebPages.itemsChange("minecraft:emerald,Golden Apple,,<script>", "exclude"));
            assertTrue(out.path("ok").asBoolean());
            assertEquals("Excluded 2 item(s)", out.path("note").asText());
            assertEquals(Set.of("diamond"), Tuning.itemSet("items.allow"));
            assertEquals(Set.of("=emerald", "=golden_apple"), Tuning.itemSet("items.deny"));
            assertEquals(2, out.path("deny").size());
            assertEquals("diamond", out.path("allow").get(0).asText());

            WebPages.itemsChange("golden_apple", "include");
            assertEquals(Set.of("diamond", "=golden_apple"), Tuning.itemSet("items.allow"));
            assertFalse(Tuning.buyingAllowed("minecraft:enchanted_golden_apple"));
            assertEquals(Set.of("=emerald"), Tuning.itemSet("items.deny"));

            WebPages.itemsChange("emerald,diamond", "clear");
            assertEquals(Set.of("=golden_apple"), Tuning.itemSet("items.allow"));
            assertEquals(Set.of(), Tuning.itemSet("items.deny"));

            JsonNode tracked = json(WebPages.itemsChange("beacon", "track"));
            assertEquals(Set.of("diamond", "beacon"), Tuning.itemSet("watch.tracked"));
            assertEquals(2, tracked.path("tracked").size());
            WebPages.itemsChange("diamond", "untrack");
            assertEquals(Set.of("beacon"), Tuning.itemSet("watch.tracked"));

            assertFalse(json(WebPages.itemsChange("", "exclude")).path("ok").asBoolean());
            assertFalse(json(WebPages.itemsChange("diamond", "delete")).path("ok").asBoolean());
        });
    }

    private static RivalIntel.Snapshot field(long now, boolean fromFollows) {
        int[] hours = new int[24];
        hours[3] = 4;
        hours[14] = 9;
        RivalIntel.Flip sold = new RivalIntel.Flip("minecraft:diamond", 64, 100_000, 150_000, 140_000,
                now - 3_600_000, now - 3_000_000, now - 1_800_000);
        RivalIntel.Flip up = new RivalIntel.Flip("minecraft:beacon", 1, 2_000_000, 2_600_000, 0,
                now - 600_000, now - 300_000, 0);
        RivalIntel.Rival busy = new RivalIntel.Rival("TraderOne", 1_234, 12, 45_600_000, 3, now - 60_000, now - 30_000,
                hours, List.of("minecraft:diamond", "minecraft:beacon"), List.of(sold, up), 42.5, 1_800_000,
                72, 19, 9_100_000, true);
        RivalIntel.Rival gone = new RivalIntel.Rival("Seller_Two", 20, 2, 900_000, 0, now - 9 * 3_600_000L, 0,
                new int[24], List.of(), List.of(), 0, 0, 10, 2, 180_000, false);
        return new RivalIntel.Snapshot(now - 180_000, "ok", List.of(busy, gone), 1,
                Set.of(RivalIntel.marketKey("minecraft:diamond", 64)),
                Map.of(RivalIntel.marketKey("minecraft:diamond", 64), 3),
                List.of(new RivalIntel.Buyer("BuyerThree", 2, 300_000, now - 120_000, "minecraft:golden_apple")),
                40, 10,
                List.of(new RivalIntel.ShadowMarket("minecraft:diamond", 64, "TraderOne", 5, 100_000, 150_000,
                        1_800_000, false)),
                fromFollows, List.of(), Map.of());
    }

    @Test
    void theRivalsPageCarriesWhatTheScreenShows() throws Exception {
        long now = 1_760_000_000_000L;
        JsonNode page = json(WebPages.rivals(field(now, false), now, ZoneOffset.UTC));
        assertEquals("updated 3m ago", page.path("stamp").asText());
        assertEquals(field(now, false).status(), page.path("followStatus").asText());
        assertEquals("flippers seen in the sales feed, last 24 h", page.path("title").asText());
        assertEquals(5, page.path("cards").size());
        assertEquals("25%", page.path("cards").get(0).path("value").asText());
        assertEquals("1 of 2", page.path("cards").get(1).path("value").asText());
        assertEquals("warn", page.path("cards").get(2).path("tone").asText());
        assertEquals("Sales", page.path("heads").get(1).asText());

        JsonNode first = page.path("rivals").get(0);
        assertEquals("TraderOne", first.path("name").asText());
        assertEquals("TraderOne", WebPages.unhex(first.path("id").asText()));
        assertEquals("1,234", first.path("sales").asText());
        assertEquals("$45.60m", first.path("revenue").asText());
        assertEquals("active", first.path("status").asText());
        assertEquals(72, first.path("bot").asInt());
        assertEquals(24, first.path("hourly").size());
        assertEquals("offline", page.path("rivals").get(1).path("status").asText());
        assertEquals("never", new ObjectMapper().readTree(WebPages.rivals(new RivalIntel.Snapshot(0, "Reading the ledger",
                List.of(new RivalIntel.Rival("Idle", 0, 0, 0, 0, 0, 0, new int[24], List.of(), List.of(), 0, 0, 0, 0, 0,
                        false)), 0, Set.of(), Map.of(), List.of(), 0, 0, List.of(), false, List.of(), Map.of()),
                now, ZoneOffset.UTC)).path("rivals").get(0).path("seen").asText());

        assertEquals("Diamond x64", page.path("underdog").path("rows").get(0).path("name").asText());
        assertTrue(page.path("underdog").path("rows").get(0).path("note").asText().startsWith("TraderOne  ·  5 flip(s)"));
        assertEquals("BuyerThree", page.path("buyers").path("rows").get(0).path("name").asText());
        assertEquals("2x  ·  $300.0k", page.path("buyers").path("rows").get(0).path("value").asText());
        assertEquals(500, page.path("knownBots").asInt());
    }

    @Test
    void oneRivalOpensWithTheirTradesAndWhatTheyTeach() throws Exception {
        long now = 1_760_000_000_000L;
        RivalIntel.Snapshot s = field(now, false);
        List<AutomationSessionController.OpenListingView> mine = List.of(
                new AutomationSessionController.OpenListingView(7, "minecraft:diamond#abc", 64, 90_000, 120_000, now));
        JsonNode page = json(WebPages.rival(s, "TraderOne", now, ZoneOffset.UTC, mine));
        assertTrue(page.path("ok").asBoolean());
        assertEquals("TraderOne  ·  active  ·  seen 30s ago", page.path("title").asText());
        assertEquals("Sales today", page.path("pairs").get(0).path("k").asText());
        assertEquals("1,234 across 12 items", page.path("pairs").get(0).path("v").asText());
        assertEquals("Diamond, Beacon", page.path("pairs").get(5).path("v").asText());
        assertEquals("42.5%", page.path("pairs").get(7).path("v").asText());
        // They list the same stack above our target: worth knowing, and shown as good news.
        assertEquals("Diamond x64: they list at $150.0k, you at $120.0k; they turn in 30m",
                page.path("lessons").get(0).path("t").asText());
        assertEquals("good", page.path("lessons").get(0).path("tone").asText());
        JsonNode flip = page.path("flips").get(0).path("cells");
        assertEquals("Diamond x64", flip.get(0).path("t").asText());
        assertEquals("$140.0k", flip.get(3).path("t").asText());
        assertEquals("+$40.0k (40%)", flip.get(4).path("t").asText());
        assertEquals("still up", page.path("flips").get(1).path("cells").get(3).path("t").asText());

        // Followed players are shown by what they ask, never as if a sale had been seen.
        JsonNode followed = json(WebPages.rival(field(now, true), "TraderOne", now, ZoneOffset.UTC, List.of()));
        assertEquals("Listings today", followed.path("pairs").get(0).path("k").asText());
        assertEquals("Ask / order", followed.path("heads").get(4).asText());
        assertEquals("x1.5", followed.path("flips").get(0).path("cells").get(4).path("t").asText());

        assertFalse(json(WebPages.rival(s, "Nobody", now, ZoneOffset.UTC, mine)).path("ok").asBoolean());
        assertFalse(json(WebPages.rival(s, WebPages.unhex("zz"), now, ZoneOffset.UTC, mine)).path("ok").asBoolean());
    }

    @Test
    void aRivalsMarketsAreTrackedOrLetThroughTheFilter() throws Exception {
        keeping(() -> {
            long now = 1_760_000_000_000L;
            Tuning.set("watch.tracked_managed", 1);
            Tuning.setText("watch.tracked", "emerald");
            Tuning.setText("items.allow", "emerald");
            Tuning.setText("items.deny", "beacon");
            RivalIntel.Snapshot s = field(now, false);

            assertTrue(json(WebPages.rivalMarkets(s, "TraderOne", "track")).path("ok").asBoolean());
            assertEquals(Set.of("emerald", "diamond", "beacon"), Tuning.itemSet("watch.tracked"));
            assertTrue(json(WebPages.rivalMarkets(s, "TraderOne", "include")).path("ok").asBoolean());
            assertEquals(Set.of("emerald", "=diamond", "=beacon"), Tuning.itemSet("items.allow"));
            assertEquals(Set.of(), Tuning.itemSet("items.deny"));
            // Someone with no trades seen has no markets to take.
            assertFalse(json(WebPages.rivalMarkets(s, "Seller_Two", "track")).path("ok").asBoolean());
        });
    }

    @Test
    void thePricesPageSaysWhatEachEntryHasBoughtAndWhatItIsWorth() throws Exception {
        OwnPrices.Entry observer = new OwnPrices.Entry("observer", 600, 900, 15, true, 100, 1000, 0);
        OwnPrices.Entry totem = new OwnPrices.Entry("totem_of_undying", 45_000, 50_000, 200, false, -1, 0, 2_000_000);
        List<AutomationSessionController.OwnPriceView> views = List.of(
                // 640 bought for 352,000 (550 each); 320 still on order at 560; 300 held, 5 of them listed.
                new AutomationSessionController.OwnPriceView(observer, 640, 352_000, 320, 560, 5, 5, 295, 1_000),
                // Nothing bought yet and the market not known.
                new AutomationSessionController.OwnPriceView(totem, 0, 0, 0, 0, 0, 0, 0, 0));
        JsonNode page = json(WebPages.prices(views, List.of("\"junk\" is not an item, a buy price and a sell price"),
                "observer top+100 600 900 x15 singles total 1000, totem of undying 45k 50k x200 budget 2m, junk"));
        assertEquals(1, page.path("problems").size());
        assertTrue(page.path("text").asText().startsWith("observer top+100"));

        JsonNode first = page.path("rows").get(0);
        assertEquals("Observer", first.path("name").asText());
        assertEquals("top+100, at most $600", first.path("buy").asText());
        assertEquals("$900, one at a time", first.path("sell").asText());
        assertEquals("320 at $560", first.path("order").asText());
        assertEquals("640 of 1,000", first.path("bought").asText());
        assertEquals("$352.0k", first.path("spent").asText());
        assertEquals("$550", first.path("each").asText());
        assertEquals("300 (5 listed)", first.path("held").asText());
        // 300 held at 900 is 270,000, against 165,000 they cost.
        assertEquals("$270.0k", first.path("yours").asText());
        assertEquals("+$105.0k", first.path("profit").asText());
        assertEquals("good", first.path("profitTone").asText());
        // The market pays more than the price asked: it will sell.
        assertEquals("good", first.path("marketTone").asText());

        JsonNode second = page.path("rows").get(1);
        assertEquals("Totem Of Undying", second.path("name").asText());
        assertEquals("$45.0k", second.path("buy").asText());
        assertEquals("none up", second.path("order").asText());
        assertEquals("$0 of $2.00m", second.path("spent").asText());
        assertEquals("-", second.path("each").asText());
        assertEquals("none", second.path("held").asText());
        assertEquals("not known", second.path("market").asText());
        assertEquals("muted", second.path("marketTone").asText());
    }

    @Test
    void aSettingKeptForThisClientIsSavedBackToItsOwnFile() throws Exception {
        Path local = Files.createTempFile("doughbay-local", ".json");
        double before = Tuning.get("buy.purchase_cap_pct");
        try {
            Files.writeString(local, "{\n  \"buy.purchase_cap_pct\" : 30,\n  \"multi.enabled\" : true,\n"
                    + "  \"note\" : \"mine\"\n}\n");
            Tuning.loadLocalFrom(local);
            assertEquals(30, Tuning.get("buy.purchase_cap_pct"));
            assertTrue(Tuning.thisClientOnly("buy.purchase_cap_pct"));
            assertFalse(Tuning.thisClientOnly("buy.outstanding_cap_pct"));
            assertTrue(row(json(WebPages.settings()), "buy.purchase_cap_pct").path("local").asBoolean());

            WebPages.setting(Map.of("key", "buy.purchase_cap_pct", "value", "35"));
            JsonNode saved = json(Files.readString(local));
            // The file now says what was set, so the next read of it does not put thirty back.
            assertEquals(35, saved.path("buy.purchase_cap_pct").asInt());
            assertTrue(saved.path("buy.purchase_cap_pct").isIntegralNumber());
            // And everything else in it is as it was, including what this class does not know.
            assertTrue(saved.path("multi.enabled").asBoolean());
            assertEquals("mine", saved.path("note").asText());
            Tuning.loadLocalFrom(local);
            assertEquals(35, Tuning.get("buy.purchase_cap_pct"));

            // A setting the file never named is not written into it.
            Tuning.set("buy.outstanding_cap_pct", Tuning.get("buy.outstanding_cap_pct"));
            assertFalse(json(Files.readString(local)).has("buy.outstanding_cap_pct"));
        } finally {
            Tuning.loadLocalFrom(null);
            Tuning.set("buy.purchase_cap_pct", before);
            Files.deleteIfExists(local);
        }
    }
}
