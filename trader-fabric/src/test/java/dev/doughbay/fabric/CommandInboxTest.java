package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandInboxTest {
    @Test
    void oneLineClosesTheGameAndNoServerCommandDoes() {
        assertTrue(CommandInbox.asksToQuit(List.of("!quit")));
        assertTrue(CommandInbox.asksToQuit(List.of("# closing for an update", "  !QUIT  ")));
        // Commands for the server are never taken for it, whatever they say.
        assertFalse(CommandInbox.asksToQuit(List.of("/quit", "quit", "pay someone 5 !quit", "!quit now")));
        assertFalse(CommandInbox.asksToQuit(List.of()));
        assertFalse(CommandInbox.asksToQuit(null));
        assertFalse(CommandInbox.asksToQuit(Arrays.asList("", null)));
    }
}
