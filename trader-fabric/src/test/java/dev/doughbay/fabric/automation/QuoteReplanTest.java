package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class QuoteReplanTest {
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static Object get(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private AutomationSessionController completeQuote(String why, long ownReadAt, long now) throws Exception {
        var driver = new AutomatedExecutionDriver();
        var controller = new AutomationSessionController(driver);
        set(controller, "policy", ContinuousAutomationPolicy.fromConfigValues(
                2, 100_000, 50_000, 5_000, 12, 80, 10, 8, 120, 1));
        set(controller, "publicCommunityEnabled", true);
        set(controller, "ownOrdersReadAt", ownReadAt);
        set(controller, "deskReadDue", false);
        Class<?> action = Class.forName(AutomationSessionController.class.getName() + "$DeskAction");
        Object quote = java.util.Arrays.stream(action.getEnumConstants()).filter(v -> v.toString().equals("QUOTE")).findFirst().orElseThrow();
        Class<?> step = Class.forName(AutomationSessionController.class.getName() + "$DeskStep");
        Constructor<?> constructor = step.getDeclaredConstructor(action, String.class, int.class, long.class, String.class, int.class, long.class);
        constructor.setAccessible(true);
        set(controller, "deskStep", constructor.newInstance(quote, "minecraft:stone", 0, 0L, why, 0, 0L));
        set(driver, "lastTerminalEvent", new AutomatedExecutionDriver.TerminalEvent(1,
                AutomatedExecutionDriver.OperationIntent.ORDER_QUOTE, AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,
                "", "", "minecraft:stone", 0, 100, "", false, now, "Quote read"));
        Method observe = controller.getClass().getDeclaredMethod("observeDeskTerminal", net.minecraft.client.Minecraft.class, long.class);
        observe.setAccessible(true); observe.invoke(controller, null, now);
        return controller;
    }
    @Test void freshOwnBookDoesNotSendACommunityQuoteThroughAnotherSlowRead() throws Exception {
        var c = completeQuote("COMMUNITY_QUOTE", 95_000, 100_000);
        assertEquals(false, get(c, "deskReadDue"), "another 90-second read would expire the 30-second quote again");
        var quotes = (Map<?, ?>) get(c, "orderQuotes");
        assertArrayEquals(new long[]{100, 100_000}, (long[]) quotes.get("minecraft:stone"));
    }
    @Test void ownTopQuoteAlsoReplansAgainstTheFreshOwnBook() throws Exception {
        assertEquals(false, get(completeQuote("your price: checking what orders pay for stone", 95_000, 100_000), "deskReadDue"));
    }
    @Test void staleOrMissingOwnBookStillRequiresARead() throws Exception {
        assertEquals(true, get(completeQuote("COMMUNITY_QUOTE", 39_999, 100_000), "deskReadDue"));
        assertEquals(true, get(completeQuote("COMMUNITY_QUOTE", 0, 100_000), "deskReadDue"));
    }
}
