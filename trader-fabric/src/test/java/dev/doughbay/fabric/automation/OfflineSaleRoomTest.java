package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room the slot check has for booking a missing listing as sold, with the
 * bot's own buy orders moving money in and out of the bank between checks.
 */
class OfflineSaleRoomTest {
    private static final long SLACK = 1_000_000;

    @Test
    void moneyPutIntoAnOrderIsNotMoneyThatWentMissing() {
        long before = 40_000_000;
        long order = 320 * 46_200L;                 // 14,784,000 for wither skulls
        long mapsSold = 2 * 306_711L;               // two listings that sold with no notice
        long now = before - order + mapsSold;
        // Not counted, the order reads as fourteen million gone: no room, and the sales are booked as returns.
        long blind = AutomationSessionController.offlineSaleRoom(now, before, 0, 0, SLACK);
        assertTrue(blind < 0);
        // Counted, what is left over is exactly the two sales, and the tolerance on top.
        long room = AutomationSessionController.offlineSaleRoom(now, before, order, 0, SLACK);
        assertEquals(mapsSold + SLACK, room);
    }

    @Test
    void aRefundFromACancelledOrderIsNotASale() {
        long before = 20_000_000;
        long refund = AutomationSessionController.refundOnCancel(31_084, 448, 2);
        assertEquals(446 * 31_084L, refund);
        long now = before + refund;
        // Not counted, 13.9M of "unexplained" money: room to book as sold anything that was only pulled back.
        assertTrue(AutomationSessionController.offlineSaleRoom(now, before, 0, 0, SLACK) > 13_000_000);
        // Counted, as spending in reverse, there is the tolerance and nothing more.
        assertEquals(SLACK, AutomationSessionController.offlineSaleRoom(now, before, -refund, 0, SLACK));
    }

    @Test
    void aReceiptedSaleAndABuyStillCountAsTheyAlwaysDid() {
        long before = 5_000_000;
        long now = before - 200_000 + 350_000;
        assertEquals(SLACK, AutomationSessionController.offlineSaleRoom(now, before, 200_000, 350_000, SLACK));
    }

    @Test
    void oneStackInThePackStandsForOneMissingListingNotAllOfThem() {
        // Nine stacks of emeralds gone from the page and one stack in the pack: one was pulled back, eight were not.
        assertEquals(1, AutomationSessionController.stacksToVouchFor(1, 0));
        // Three in the pack, three listings excused, and no more however many are missing.
        assertEquals(3, AutomationSessionController.stacksToVouchFor(3, 0));
        // A stack a purchase is waiting to list is that purchase's, and excuses nothing.
        assertEquals(0, AutomationSessionController.stacksToVouchFor(1, 1));
        assertEquals(2, AutomationSessionController.stacksToVouchFor(3, 1));
        assertEquals(0, AutomationSessionController.stacksToVouchFor(1, 4));
        // Found only by the looser match, which takes a stack when there is exactly one candidate.
        assertEquals(1, AutomationSessionController.stacksToVouchFor(0, 0));
    }

    @Test
    void aCancelGivesBackOnlyWhatWasNotDelivered() {
        assertEquals(0, AutomationSessionController.refundOnCancel(1_000, 64, 64));
        assertEquals(0, AutomationSessionController.refundOnCancel(1_000, 64, 90));
        assertEquals(64_000, AutomationSessionController.refundOnCancel(1_000, 64, 0));
        assertEquals(64_000, AutomationSessionController.refundOnCancel(1_000, 64, -3));
        assertEquals(0, AutomationSessionController.refundOnCancel(0, 64, 0));
    }
}
