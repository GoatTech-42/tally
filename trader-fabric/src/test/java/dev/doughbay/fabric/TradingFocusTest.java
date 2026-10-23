package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TradingFocusTest {
    @Test void focusedBuyingKeepsObservationAndOwnedStockPricingAvailable() {
        String before=SettingsCode.exportPreset(Map.of(),Map.of());
        try {
            Tuning.setText("items.allow","=experience_bottle, =moss_block, =golden_apple, =enchanted_golden_apple");
            Tuning.setText("items.deny","");Tuning.setText("items.liquidate","");
            Tuning.setText("buy.focus_items","=experience_bottle, =golden_apple");Tuning.set("buy.focus",1);
            assertTrue(Tuning.buyingAllowed("minecraft:experience_bottle"));
            assertTrue(Tuning.buyingAllowed("minecraft:golden_apple"));
            assertFalse(Tuning.buyingAllowed("minecraft:enchanted_golden_apple"));
            assertFalse(Tuning.buyingAllowed("minecraft:moss_block"));
            assertTrue(Tuning.itemAllowed("minecraft:moss_block"));
            assertTrue(Tuning.priceCheckAllowed("minecraft:moss_block",false));
            assertTrue(Tuning.priceCheckAllowed("minecraft:moss_block",true));
            Tuning.setText("items.deny","experience_bottle");
            assertFalse(Tuning.buyingAllowed("minecraft:experience_bottle"));
            Tuning.setText("items.liquidate","golden_apple");
            assertFalse(Tuning.buyingAllowed("minecraft:golden_apple"));
        } finally {SettingsCode.apply(before);}
    }
    @Test void enabledEmptyFocusCannotAccidentallyBuyEverything() {
        String before=SettingsCode.exportPreset(Map.of(),Map.of());
        try {
            Tuning.setText("items.allow","");Tuning.setText("items.deny","");Tuning.setText("items.liquidate","");
            Tuning.setText("buy.focus_items","");Tuning.set("buy.focus",1);
            assertFalse(Tuning.buyingAllowed("minecraft:moss_block"));
            assertTrue(Tuning.priceCheckAllowed("minecraft:moss_block",true));
            Tuning.set("buy.focus",0);
            assertTrue(Tuning.buyingAllowed("minecraft:moss_block"));
        } finally {SettingsCode.apply(before);}
    }
    @Test void originalFocusedAndOvernightPresetsRoundTripWithoutChangingCapsOrObservation() {
        String before=SettingsCode.exportPreset(Map.of(),Map.of());
        try {
            Tuning.setText("items.allow","=experience_bottle, =moss_block");
            Tuning.setText("items.deny","");Tuning.setText("items.liquidate","");
            Tuning.set("buy.focus",0);Tuning.setText("buy.focus_items","");
            double cap=Tuning.get("buy.purchase_cap_pct"),slots=Tuning.get("slots.max");
            String original=SettingsCode.exportPreset(Map.of(),Map.of());
            String focused=SettingsCode.exportPreset(Map.of("buy.focus",1.0),Map.of("buy.focus_items","=experience_bottle"));
            String overnight=SettingsCode.exportPreset(Map.of("buy.focus",1.0),Map.of("buy.focus_items","=experience_bottle, =moss_block"));
            assertTrue(SettingsCode.apply(focused).ok());assertFalse(Tuning.buyingAllowed("minecraft:moss_block"));
            assertTrue(Tuning.itemAllowed("minecraft:moss_block"));
            assertTrue(SettingsCode.apply(overnight).ok());assertTrue(Tuning.buyingAllowed("minecraft:moss_block"));
            assertTrue(SettingsCode.apply(original).ok());assertEquals(0,Tuning.get("buy.focus"));
            assertEquals("",Tuning.text("buy.focus_items"));assertEquals(cap,Tuning.get("buy.purchase_cap_pct"));
            assertEquals(slots,Tuning.get("slots.max"));
        } finally {SettingsCode.apply(before);}
    }
}
