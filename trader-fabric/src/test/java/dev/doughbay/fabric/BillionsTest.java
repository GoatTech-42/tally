package dev.doughbay.fabric;

import dev.doughbay.fabric.discord.DiscordBridgeMoney;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillionsTest {
    @Test
    void aBillionReadsAsABillion() {
        // The bank that prompted it, and a day's revenue.
        assertEquals("$1.01B", DiscordBridgeMoney.of(1_014_000_000L));
        assertEquals("$2.34B", DiscordBridgeMoney.of(2_342_400_000L));
        assertEquals("-$1.50B", DiscordBridgeMoney.of(-1_500_000_000L));
        // Under a billion nothing changes.
        assertEquals("$999.99M", DiscordBridgeMoney.of(999_990_000L));
        assertEquals("$896.00M", DiscordBridgeMoney.of(896_000_000L));
        assertEquals("$70.0K", DiscordBridgeMoney.of(70_000L));
        // The screen and the web panel, in their own lower case.
        assertEquals("$1.01b", Figures.money(1_014_000_000d));
        assertEquals("$896.00m", Figures.money(896_000_000d));
    }
}
