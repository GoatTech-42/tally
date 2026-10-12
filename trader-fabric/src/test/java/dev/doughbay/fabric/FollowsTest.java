package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FollowsTest {
    @Test
    void theListThatShipsIsFiveHundredNamesTheServerWouldAccept() {
        java.util.List<String> bots = Follows.knownBots();
        assertEquals(500, bots.size());
        java.util.Set<String> once = new java.util.HashSet<>();
        for (String name : bots) {
            // Two to twenty letters, digits or underscores; a Bedrock name carries its dot.
            assertEquals(true, name.matches("\\.?[A-Za-z0-9_]{2,20}"), name);
            assertEquals(true, once.add(name.toLowerCase(java.util.Locale.ROOT)), name + " is listed twice");
        }
    }

    @Test
    void addingTheKnownBotsLeavesAlonePlayersAlreadyOnTheList() {
        java.util.List<String> mine = java.util.List.of("# my own notes", "", "TraderOne", "  bedrock_two  ");
        java.util.List<String> shipped = java.util.List.of("traderone", "NewSeller", ".Bedrock_Two", "newseller", "Third");
        // Already there in another case, or twice in what ships: added once or not at all.
        assertEquals(java.util.List.of("NewSeller", ".Bedrock_Two", "Third"), Follows.notYetListed(mine, shipped));
        // A name with the Bedrock dot is a different player from the same name without it.
        assertEquals(java.util.List.of(), Follows.notYetListed(java.util.List.of("NewSeller", ".Bedrock_Two", "Third", "traderone"), shipped));
    }

    @Test
    void theNoticesTheServerSendsAboutAFollowedPlayerAreRead() {
        // Lines as observed on 2026-10-10.
        Follows.Event order = Follows.parse("traderone ordered 128 Nether Wart for $ 50K");
        assertNotNull(order);
        assertEquals("traderone", order.player());
        assertEquals("ordered", order.kind());
        assertEquals(128, order.quantity());
        assertEquals("Nether Wart", order.item());
        assertEquals(50_000, order.price());

        Follows.Event listing = Follows.parse("GearSeller listed 1 Diamond Boots for $ 93.7K");
        assertEquals("listed", listing.kind());
        assertEquals("Diamond Boots", listing.item());
        assertEquals(93_700, listing.price());

        Follows.Event buy = Follows.parse(".BedrockBuyer1 bought 1 Item Frame for $ 39.8K");
        assertEquals(".BedrockBuyer1", buy.player());
        assertEquals(39_800, buy.price());

        assertEquals(979, Follows.parse("GearSeller bought 1 Slime Block for $ 979").price());
        assertEquals(34_900, Follows.parse("traderone listed 1 Minecart with Chest for $ 34.9K").price());
        assertEquals(1_200_000, Follows.parse("someone listed 64 Diamond Block for $1.2M").price());
    }

    @Test
    void comingAndGoingIsRead() {
        Follows.Event left = Follows.parse(".BedrockBuyer2 left the game");
        assertEquals("left", left.kind());
        assertEquals(".BedrockBuyer2", left.player());
        assertEquals("joined", Follows.parse(".BedrockBuyer2 joined the game").kind());
    }

    @Test
    void aSaleIntoAnOrderIsReadWithItsTotal() {
        Follows.Event sold = Follows.parse("tradertwo sold multiple items for $ 320K");
        assertEquals("sold", sold.kind());
        assertEquals(320_000, sold.price());
        assertEquals(640, Follows.parse("traderone sold multiple items for $ 640").price());
    }

    @Test
    void moneyChangingHandsIsReadWithBothParties() {
        // Lines as observed on 2026-10-11, names changed.
        Follows.Event got = Follows.parse("traderone was paid $ 5M by .BedrockBuyer1");
        assertEquals("was_paid", got.kind());
        assertEquals("traderone", got.player());
        assertEquals(".BedrockBuyer1", got.item());
        assertEquals(5_000_000, got.price());

        Follows.Event sent = Follows.parse("traderone paid tradertwo $ 500K");
        assertEquals("paid", sent.kind());
        assertEquals("tradertwo", sent.item());
        assertEquals(500_000, sent.price());

        Follows.Event bounty = Follows.parse("traderone added $ 20.6M to tradertwo's bounty");
        assertEquals("bounty", bounty.kind());
        assertEquals("tradertwo", bounty.item());
        assertEquals(20_600_000, bounty.price());

        // Our own payment receipt is ours, not a notice.
        assertNull(Follows.parse("You paid tradertwo $ 20M"));
    }

    @Test
    void theServersOtherWordingForNoSuchPlayerIsRecognised() {
        assertEquals(Follows.Reply.NO_SUCH_PLAYER, Follows.classify("That player does not exist", "tradertwo"));
        assertEquals(Follows.Reply.NO_SUCH_PLAYER, Follows.classify("That player does not exist.", "tradertwo"));
    }

    @Test
    void ourOwnReceiptsAndOrdinaryChatAreNotNotices() {
        assertNull(Follows.parse("You listed 64 Obsidian for $ 48K"));
        assertNull(Follows.parse(".SomeBuyer77 bought your Deepslate Diamond Ore for $27.8K"));
        assertNull(Follows.parse("[GoNuts] Listed black stained glass x64 at 130,905"));
        assertNull(Follows.parse("SomeSeller delivered you 64 TNT"));
        assertNull(Follows.parse("Steve: traderone listed 64 Diamond for $ 1"));
        assertNull(Follows.parse("You followed traderone"));
        assertNull(Follows.parse(null));
    }

    @Test
    void theAnswerToAFollowIsToldApartFromEverythingElse() {
        assertEquals(Follows.Reply.FOLLOWED, Follows.classify("You followed traderone", "traderone"));
        assertEquals(Follows.Reply.FOLLOWED, Follows.classify("You followed TraderOne", "traderone"));
        assertEquals(Follows.Reply.NOT_FOUND, Follows.classify("No player named BedrockBuyer2 was found.", "BedrockBuyer2"));
        assertEquals(Follows.Reply.UNFOLLOWED, Follows.classify("You unfollowed tradertwo", "tradertwo"));
        // An answer about somebody else, typed by hand at the same moment.
        assertEquals(Follows.Reply.UNRELATED, Follows.classify("You followed tradertwo", "traderone"));
        assertEquals(Follows.Reply.UNRELATED, Follows.classify("GearSeller bought 1 Slime Block for $ 979", "tradertwo"));
        assertEquals(Follows.Reply.UNRELATED, Follows.classify("Steve: follow me to spawn", "tradertwo"));
        // Words not seen before are how a limit will announce itself.
        assertEquals(Follows.Reply.REFUSED, Follows.classify("You can only follow 50 players", "tradertwo"));
        assertEquals(Follows.Reply.REFUSED, Follows.classify("Follow limit reached", "tradertwo"));
        assertEquals(Follows.Reply.FOLLOWED, Follows.classify("You are already following tradertwo", "tradertwo"));
    }
}
