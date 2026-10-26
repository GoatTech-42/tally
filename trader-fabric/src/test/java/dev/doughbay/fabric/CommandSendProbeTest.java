package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandSendProbeTest {
    @Test void illegalCharactersNeverReachTheConnectionCallback() {
        for (int code : new int[]{0, 9, 10, 13, 31, 127, 167}) {
            String command = "ah search gold" + (char) code;
            assertThrows(IllegalArgumentException.class, () -> CommandSendProbe.verify(command,
                    () -> fail("Invalid command reached connection")));
        }
    }

    @Test void onlyPlainTextAndPaymentTextAreHandedOffUnchanged() {
        for (String command : new String[]{"ah search Chicken", "msg ExamplePlayer hello123", "pay ExamplePlayer 200m"}) {
            assertDoesNotThrow(() -> CommandSendProbe.verify(command, () -> CommandSendProbe.observe(command)));
        }
    }

    @Test void refusalDoesNotExposeArguments() {
        var error = assertThrows(IllegalArgumentException.class, () -> CommandSendProbe.verify(
                "pay ExamplePlayer 200m\u00a7", () -> fail("Invalid payment reached connection")));
        assertFalse(error.getMessage().contains("ExamplePlayer"));
        assertFalse(error.getMessage().contains("200m"));
    }
    @Test void voidSendReturningNormallyIsNotProofOfAPacket() {
        var error = assertThrows(IllegalStateException.class,
                () -> CommandSendProbe.verify("ah sell 15500", () -> { }));
        assertTrue(error.getMessage().contains("canceled locally"));
    }

    @Test void matchingPacketHandoffIsAcceptedButAnotherCommandIsNot() {
        assertDoesNotThrow(() -> CommandSendProbe.verify("ah sell 15500",
                () -> CommandSendProbe.observe("ah sell 15500")));
        assertThrows(IllegalStateException.class, () -> CommandSendProbe.verify("ah sell 15500",
                () -> CommandSendProbe.observe("ah")));
    }

    @Test void previousOrManualPacketsCannotProveTheNextDispatch() {
        CommandSendProbe.observe("ah sell 15500");
        assertThrows(IllegalStateException.class,
                () -> CommandSendProbe.verify("ah sell 15500", () -> { }));
        assertThrows(IllegalArgumentException.class, () -> CommandSendProbe.verify("ah", () -> {
            CommandSendProbe.observe("ah");
            throw new IllegalArgumentException("disconnected");
        }));
        assertThrows(IllegalStateException.class, () -> CommandSendProbe.verify("ah", () -> { }));
    }
}
