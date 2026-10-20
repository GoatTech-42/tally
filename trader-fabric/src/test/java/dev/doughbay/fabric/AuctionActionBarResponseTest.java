package dev.doughbay.fabric;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class AuctionActionBarResponseTest {
    @Test void nativeActionBarRefusalReachesTheActiveListingDriver() throws Exception {
        var driver = DoughBayClient.executionDriver();
        Field operation = field("operation"), phase = field("phase"), refusal = field("serverRejection");
        Object oldOperation = operation.get(driver), oldPhase = phase.get(driver), oldRefusal = refusal.get(driver);
        try {
            setEnum(operation, driver, "LIST");
            setEnum(phase, driver, "WAITING_FOR_CONFIRMATION");
            refusal.set(driver, "");
            DoughBayClient.observeActionBar(Component.literal("You have too many listed items"));
            assertEquals("You have too many listed items", refusal.get(driver),
                    "native title action bars must reach the same refusal path as system chat");
        } finally {
            operation.set(driver, oldOperation); phase.set(driver, oldPhase); refusal.set(driver, oldRefusal);
        }
    }
    @Test void nativeReceiptDoesNotByItselfFinishTheListingOrClearTheTarget() throws Exception {
        var driver = DoughBayClient.executionDriver();
        Field operation = field("operation"), phase = field("phase"), receipt = field("listingReceipt");
        Object oldOperation = operation.get(driver), oldPhase = phase.get(driver), oldReceipt = receipt.get(driver);
        try {
            setEnum(operation, driver, "LIST");
            setEnum(phase, driver, "WAITING_FOR_CONFIRMATION");
            receipt.set(driver, "");
            DoughBayClient.observeActionBar(Component.literal("You listed 64 Redstone for $ 20K"));
            assertEquals("You listed 64 Redstone for $ 20K", receipt.get(driver));
            assertEquals("LIST", operation.get(driver).toString());
            assertEquals("WAITING_FOR_CONFIRMATION", phase.get(driver).toString(),
                    "the existing inventory proof must still decide completion");
        } finally {
            operation.set(driver, oldOperation); phase.set(driver, oldPhase); receipt.set(driver, oldReceipt);
        }
    }
    @Test void inactiveDriverDoesNotTurnNativeRefusalsIntoAListingOperation() throws Exception {
        var driver = DoughBayClient.executionDriver();
        Field operation = field("operation"), phase = field("phase"), refusal = field("serverRejection");
        Object oldOperation = operation.get(driver), oldPhase = phase.get(driver), oldRefusal = refusal.get(driver);
        try {
            setEnum(operation, driver, "NONE"); setEnum(phase, driver, "IDLE"); refusal.set(driver, "");
            DoughBayClient.observeActionBar(null);
            DoughBayClient.observeActionBar(Component.literal("You have too many listed items"));
            assertEquals("", refusal.get(driver));
            assertEquals("NONE", operation.get(driver).toString());
        } finally {
            operation.set(driver, oldOperation); phase.set(driver, oldPhase); refusal.set(driver, oldRefusal);
        }
    }
    private static Field field(String name) throws Exception {
        var f = AutomatedExecutionDriver.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void setEnum(Field field, Object instance, String name) throws Exception {
        for (Object value : field.getType().getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) { field.set(instance, value); return; }
        }
        fail("missing state " + name);
    }
}
