package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room the slot check has for booking a missing listing as sold, with the
 * bot's own buy orders moving money in and out of the bank between checks.
 */
class OfflineSaleRoomTest {
    @Test
    void moneyPutIntoAnOrderIsNotMoneyThatWentMissing() {
        long before = 40_000_000;
        long order = 320 * 46_200L;                 // 14,784,000 for wither skulls
        long mapsSold = 2 * 306_711L;               // two listings that sold with no notice
        long now = before - order + mapsSold;
        // Not counted, the order reads as fourteen million gone: no room, and the sales are booked as returns.
        long blind = AutomationSessionController.offlineSaleRoom(now, before, 0, 0);
        assertTrue(blind < 0);
        // Counted, what is left over is exactly the two sales.
        long room = AutomationSessionController.offlineSaleRoom(now, before, order, 0);
        assertEquals(mapsSold, room);
    }

    @Test
    void aRefundFromACancelledOrderIsNotASale() {
        long before = 20_000_000;
        long refund = AutomationSessionController.refundOnCancel(31_084, 448, 2);
        assertEquals(446 * 31_084L, refund);
        long now = before + refund;
        // Not counted, 13.9M of "unexplained" money: room to book as sold anything that was only pulled back.
        assertTrue(AutomationSessionController.offlineSaleRoom(now, before, 0, 0) > 13_000_000);
        // Counted as spending in reverse, the refund leaves no sale income.
        assertEquals(0, AutomationSessionController.offlineSaleRoom(now, before, -refund, 0));
    }

    @Test
    void aReceiptedSaleAndABuyStillCountAsTheyAlwaysDid() {
        long before = 5_000_000;
        long now = before - 200_000 + 350_000;
        assertEquals(0, AutomationSessionController.offlineSaleRoom(now, before, 200_000, 350_000));
    }

    @Test void zeroMovementAuditCannotSellFourteenAnchorsOnRoundingAllowanceAlone() {
        long movement = AutomationSessionController.offlineSaleRoom(100_000_000, 100_000_000, 0, 0);
        long room = AutomationSessionController.offlineSaleBudget(movement, 0);
        int sold = 0;
        for (int i = 0; i < 18 && room >= 69_000; i++) { sold++; room -= 69_000; }
        assertEquals(0, sold);
        // The old allowance alone booked fourteen sales and 205,800 of profit.
    }

    @Test void positiveCashAndEarningsAreNotDoubleCountedAndBudgetIsConsumedOnce() {
        long room = AutomationSessionController.offlineSaleBudget(132_000, 132_999);
        int sold = 0;
        for (int i = 0; i < 8 && room >= 66_000; i++) { sold++; room -= 66_000; }
        assertEquals(2, sold);
        assertEquals(999, room);
    }

    @Test void missingOrNegativeBalanceCannotCreateIncomeButServerEarningsStillCount() {
        assertEquals(0, AutomationSessionController.offlineSaleBudget(0, 0));
        assertEquals(0, AutomationSessionController.offlineSaleBudget(-264_000, 0));
        assertEquals(463_998, AutomationSessionController.offlineSaleBudget(-264_000, 463_998));
        assertEquals(66_000, AutomationSessionController.offlineSaleBudget(66_000, 0));
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
    void whatTheServerSaysWasEarnedIsReadAtTheTopOfWhatItCanMean() {
        // Two stacks of bone blocks at 44,949 each, announced as one line with no listing named.
        assertEquals(89_899, AutomationSessionController.unnamedEarnings("You earned $89.8K from auction"));
        assertTrue(AutomationSessionController.unnamedEarnings("You earned $89.8K from auction") >= 2 * 44_949);
        assertEquals(923_999, AutomationSessionController.unnamedEarnings("You earned $923K from auction"));
        assertEquals(1_199_999, AutomationSessionController.unnamedEarnings("You earned $1.1M from auction while you were away"));
        assertEquals(44_999, AutomationSessionController.unnamedEarnings("You earned $44.9K from auction got delivered 2 items"));
        assertEquals(502, AutomationSessionController.unnamedEarnings("You earned $502 from auction"));
        // Anything else is not an earnings line and stands for nothing.
        assertEquals(0, AutomationSessionController.unnamedEarnings("You earned 1 Shard for playing the server"));
        assertEquals(0, AutomationSessionController.unnamedEarnings("Somebody earned $5M from auction"));
        assertEquals(0, AutomationSessionController.unnamedEarnings(null));
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
