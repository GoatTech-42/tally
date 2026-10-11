package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankScaledCapsTest {
    @Test
    void followingTheBankACapIsAShareOfTheBalance() {
        // 14% of a 33M bank, whatever was typed.
        assertEquals(4_620_000, AutomationSessionController.capFor(33_000_000, 14, 5_000_000, 1_000, true));
        // It grows with the bank past what was typed, and shrinks under it.
        assertEquals(70_000_000, AutomationSessionController.capFor(500_000_000, 14, 5_000_000, 1_000, true));
        assertEquals(140_000, AutomationSessionController.capFor(1_000_000, 14, 5_000_000, 1_000, true));
        // A bank of next to nothing still gets the least a trade needs.
        assertEquals(1_000, AutomationSessionController.capFor(2_000, 14, 5_000_000, 1_000, true));
    }

    @Test
    void untilTheBalanceIsReadTheTypedAmountStandsIn() {
        assertEquals(5_000_000, AutomationSessionController.capFor(-1, 14, 5_000_000, 1_000, true));
        assertEquals(5_000_000, AutomationSessionController.capFor(0, 14, 5_000_000, 1_000, true));
    }

    @Test
    void notFollowingTheBankTheTypedAmountIsTheCapWhateverTheBalance() {
        assertEquals(5_000_000, AutomationSessionController.capFor(500_000_000, 14, 5_000_000, 1_000, false));
        assertEquals(5_000_000, AutomationSessionController.capFor(1_000_000, 14, 5_000_000, 1_000, false));
        assertEquals(75_000_000, AutomationSessionController.capFor(33_000_000, 70, 75_000_000, 5_000, false));
    }
}
