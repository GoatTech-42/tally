package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ListingsPerMarketLimitTest {
    @Test void sixteenIsSupportedWithoutChangingTheDefaultOrOtherCaps() {
        var setting=Tuning.SETTINGS.stream().filter(s->s.key().equals("buy.max_open_per_market")).findFirst().orElseThrow();
        assertEquals(8,setting.defaultValue());assertEquals(16,setting.clamp(16));assertEquals(16,setting.clamp(17));
        assertEquals(1,setting.clamp(0));
    }
    @Test void largeMarketLimitAlsoAcceptsSixteenAndKeepsItsExistingDefault() {
        var setting=Tuning.setting("tier.large_max_open_per_market");
        assertEquals(2,setting.defaultValue());
        assertEquals(16,setting.clamp(16));
        assertEquals(16,setting.clamp(17));
        assertEquals(1,setting.clamp(0));
    }
}
