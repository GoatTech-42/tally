package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MoneyFlowsTest {
    private static final long NOW = 1_800_000_000_000L;

    /** "payer paid receiver", as the payer's followers are told it. */
    private static Follows.Seen paid(long at, String payer, String receiver, long amount) {
        return new Follows.Seen(at, payer, "paid", 0, receiver, amount);
    }

    /** "receiver was paid by payer", as the receiver's followers are told it. */
    private static Follows.Seen wasPaid(long at, String receiver, String payer, long amount) {
        return new Follows.Seen(at, receiver, "was_paid", 0, payer, amount);
    }

    private static Follows.Seen listed(long at, String player) {
        return new Follows.Seen(at, player, "listed", 64, "minecraft:obsidian", 48_000);
    }

    @Test
    void oneNoticeNamesBothPlayersFollowedOrNot() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(wasPaid(NOW - 5_000, "shopkeeper", "stranger", 5_000_000)),
                Map.of("shopkeeper", 0L), NOW);
        assertEquals(1, s.payments());
        assertEquals(5_000_000, s.moved());
        assertEquals(2, s.accounts().size());
        MoneyFlows.Account stranger = s.account("stranger");
        assertNotNull(stranger);
        assertEquals(5_000_000, stranger.out());
        assertEquals(MoneyFlows.Privacy.NOT_FOLLOWED, stranger.privacy());
        assertEquals(5_000_000, stranger.exposed());
        assertEquals(MoneyFlows.Privacy.OPEN, s.account("shopkeeper").privacy());
        assertEquals(1, s.unfollowed());
        assertEquals(0, s.hidden());
    }

    @Test
    void aPaymentToldFromBothSidesIsCountedOnce() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(
                paid(NOW - 9_000, "traderone", "tradertwo", 2_000_000),
                wasPaid(NOW - 8_500, "tradertwo", "traderone", 2_000_000)), Map.of(), NOW);
        assertEquals(1, s.payments());
        assertEquals(2_000_000, s.moved());
    }

    @Test
    void theSameAmountPaidAgainStraightAwayIsAnotherPayment() {
        // As seen on the server: four identical payments inside one second, each told by the payer.
        List<Follows.Seen> seen = new ArrayList<>();
        for (int i = 0; i < 4; i++) seen.add(paid(NOW - 9_000 + i * 100, "traderone", "tradertwo", 6_700));
        MoneyFlows.Snapshot s = MoneyFlows.build(seen, Map.of(), NOW);
        assertEquals(4, s.payments());
        assertEquals(26_800, s.moved());
        assertEquals(1, s.edges().size());
        assertEquals(4, s.edges().get(0).count());
        assertEquals(6_700, s.edges().get(0).largest());
    }

    @Test
    void theReceiversTellingOfAFollowedPayerShowsThePayerIsPrivate() {
        // Both followed. Had the payer's payments been open, the payer's own
        // notice is the one the server sends; the receiver's came instead.
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(wasPaid(NOW - 5_000, "shopkeeper", "quietone", 40_000_000)),
                Map.of("shopkeeper", 0L, "quietone", NOW - 60_000), NOW);
        MoneyFlows.Account quiet = s.account("quietone");
        assertEquals(MoneyFlows.Privacy.HIDDEN, quiet.privacy());
        assertEquals(1, s.hidden());
        assertEquals(40_000_000, s.hiddenMoved());
    }

    @Test
    void aPayerFollowedOnlyAfterwardsIsNotCalledPrivate() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(wasPaid(NOW - 5_000, "shopkeeper", "latecomer", 1_000_000)),
                Map.of("shopkeeper", 0L, "latecomer", NOW - 1_000), NOW);
        assertEquals(MoneyFlows.Privacy.SILENT, s.account("latecomer").privacy());
        assertEquals(0, s.hidden());
    }

    @Test
    void aFollowedReceiverNamedByThePayerIsNotCalledPrivate() {
        // The payer's notice is the one sent when both are open, so the
        // receiver's silence here proves nothing.
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(paid(NOW - 5_000, "traderone", "tradertwo", 1_000_000)),
                Map.of("traderone", 0L, "tradertwo", 0L), NOW);
        assertEquals(MoneyFlows.Privacy.SILENT, s.account("tradertwo").privacy());
    }

    @Test
    void aPlayerWhoseOwnNoticesArriveIsOpenAndTheirTradesAreCounted() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(
                listed(NOW - 9_000, "traderone"), listed(NOW - 8_000, "traderone"),
                paid(NOW - 5_000, "traderone", "tradertwo", 1_000_000),
                listed(NOW - 4_000, "bystander")), Map.of(), NOW);
        MoneyFlows.Account one = s.account("traderone");
        assertEquals(MoneyFlows.Privacy.OPEN, one.privacy());
        assertEquals(2, one.trades());
        // Somebody who only trades is not part of the money.
        assertNull(s.account("bystander"));
    }

    @Test
    void manyPayingInAndSomePaidBackIsAHub() {
        List<Follows.Seen> seen = new ArrayList<>();
        for (int i = 0; i < 6; i++) seen.add(wasPaid(NOW - 60_000 + i * 1_000, "thehouse", "player" + i, 10_000_000));
        for (int i = 0; i < 3; i++) seen.add(paid(NOW - 30_000 + i * 1_000, "thehouse", "player" + i, 4_000_000));
        MoneyFlows.Account house = MoneyFlows.build(seen, Map.of(), NOW).account("thehouse");
        assertEquals(MoneyFlows.Shape.HUB, house.shape());
        assertEquals(60_000_000, house.in());
        assertEquals(12_000_000, house.out());
        assertEquals(48_000_000, house.net());
        assertEquals(6, house.payers());
        assertEquals(3, house.bothWays());
        assertEquals(0, house.trades());
    }

    @Test
    void manyPayingInAndLittleGoingOutIsACollector() {
        List<Follows.Seen> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) seen.add(wasPaid(NOW - 60_000 + i * 1_000, "shopkeeper", "player" + i, 1_000_000));
        assertEquals(MoneyFlows.Shape.COLLECTOR, MoneyFlows.build(seen, Map.of(), NOW).account("shopkeeper").shape());
    }

    @Test
    void moneyThatGoesOutAgainToSomeoneElsePassesThrough() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(
                wasPaid(NOW - 60_000, "middle", "source", 100_000_000),
                paid(NOW - 30_000, "middle", "drain", 95_000_000)), Map.of(), NOW);
        assertEquals(MoneyFlows.Shape.PASSES_ON, s.account("middle").shape());
        assertEquals(MoneyFlows.Shape.SENDS, s.account("source").shape());
        assertEquals(MoneyFlows.Shape.RECEIVES, s.account("drain").shape());
        // Handing the same money back to whoever sent it is not passing it on.
        MoneyFlows.Snapshot back = MoneyFlows.build(List.of(
                wasPaid(NOW - 60_000, "middle", "source", 100_000_000),
                paid(NOW - 30_000, "middle", "source", 95_000_000)), Map.of(), NOW);
        assertEquals(MoneyFlows.Shape.MIXED, back.account("middle").shape());
    }

    @Test
    void anAccountsPageListsWhoPaysThemAndWhoTheyPayTheMostFirst() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(
                wasPaid(NOW - 60_000, "middle", "small", 1_000_000),
                wasPaid(NOW - 50_000, "middle", "big", 9_000_000),
                wasPaid(NOW - 40_000, "middle", "big", 1_000_000),
                paid(NOW - 30_000, "middle", "drain", 2_000_000)), Map.of(), NOW);
        List<MoneyFlows.Edge> in = s.into("Middle");
        assertEquals(2, in.size());
        assertEquals("big", in.get(0).payer());
        assertEquals(10_000_000, in.get(0).total());
        assertEquals(2, in.get(0).count());
        assertEquals(9_000_000, in.get(0).largest());
        assertEquals(NOW - 40_000, in.get(0).lastAt());
        assertEquals(1, s.outOf("middle").size());
        assertEquals(List.of("big", "drain", "small"), s.around("middle").stream().sorted().toList());
        // The biggest mover leads the table.
        assertEquals("middle", s.accounts().get(0).name());
    }

    @Test
    void bountiesLoginsAndBrokenLinesAreLeftOut() {
        MoneyFlows.Snapshot s = MoneyFlows.build(List.of(
                new Follows.Seen(NOW - 5_000, "traderone", "bounty", 0, "tradertwo", 20_000_000),
                new Follows.Seen(NOW - 4_000, "traderone", "joined", 0, "", 0),
                paid(NOW - 3_000, "traderone", "", 5_000_000),
                paid(NOW - 2_000, "traderone", "traderone", 5_000_000)), Map.of(), NOW);
        assertEquals(0, s.payments());
        assertEquals(0, s.accounts().size());
    }
}
