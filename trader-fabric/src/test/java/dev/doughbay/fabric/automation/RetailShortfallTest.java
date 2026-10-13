package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetailShortfallTest {
    private static final long LAP = 35_000L;

    @Test
    void aLotOneShortForMinutesIsWrittenDownToWhatIsCarried() {
        RetailShortfall watch = new RetailShortfall();
        long now = 1_000_000L;
        // 127 recorded, 126 carried: it waits while that might still change.
        for (int look = 1; look < RetailShortfall.LOOKS; look++) {
            assertEquals(0, watch.due(7, 127, 126, now), "look " + look);
            now += LAP;
        }
        // The same count on the sixth look, three minutes on: the one that is missing goes.
        assertEquals(1, watch.due(7, 127, 126, now + RetailShortfall.MILLIS));
        // And the count starts again from nothing for whatever comes next.
        assertEquals(0, watch.due(7, 126, 120, now + RetailShortfall.MILLIS + LAP));
    }

    @Test
    void manyLooksInAShortTimeAreNotEnoughAndNeitherIsALongTimeWithFewLooks() {
        RetailShortfall quick = new RetailShortfall();
        for (int look = 0; look < 50; look++) assertEquals(0, quick.due(7, 64, 60, 1_000L + look));
        RetailShortfall slow = new RetailShortfall();
        assertEquals(0, slow.due(7, 64, 60, 0));
        assertEquals(0, slow.due(7, 64, 60, 60 * 60_000L));
    }

    @Test
    void aCountThatIsStillChangingStartsTheWaitAgain() {
        RetailShortfall watch = new RetailShortfall();
        long now = 0;
        for (int look = 0; look < RetailShortfall.LOOKS - 1; look++) {
            assertEquals(0, watch.due(7, 127, 100, now));
            now += LAP;
        }
        // More arrived: this is stock on its way in, not stock that is gone.
        assertEquals(0, watch.due(7, 127, 110, now + RetailShortfall.MILLIS));
        assertEquals(0, watch.due(7, 127, 110, now + RetailShortfall.MILLIS + LAP));
        // All of it there: forgotten, so a later shortfall is counted from its own start.
        assertEquals(0, watch.due(7, 127, 127, now + RetailShortfall.MILLIS + 2 * LAP));
        assertEquals(0, watch.due(7, 127, 126, now + 10 * RetailShortfall.MILLIS));
    }

    @Test
    void anInventoryWithNoneOfTheItemConcludesNothing() {
        RetailShortfall watch = new RetailShortfall();
        long now = 0;
        // A lobby, a world still loading: the lot is never written off whole, however long it lasts.
        for (int look = 0; look < 100; look++) {
            assertEquals(0, watch.due(7, 127, 0, now));
            assertEquals(0, watch.due(8, 64, -3, now));
            now += LAP;
        }
        // Each lot is counted by itself.
        RetailShortfall two = new RetailShortfall();
        for (int look = 0; look < RetailShortfall.LOOKS - 1; look++) {
            assertEquals(0, two.due(1, 10, 9, look * LAP));
        }
        assertEquals(0, two.due(2, 10, 9, RetailShortfall.MILLIS + 10 * LAP));
        assertEquals(1, two.due(1, 10, 9, RetailShortfall.MILLIS + 10 * LAP));
        two.clear(2);
        assertEquals(0, two.due(2, 10, 9, RetailShortfall.MILLIS + 20 * LAP));
    }
}
