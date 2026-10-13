package dev.doughbay.fabric;

import java.util.List;
import java.util.Locale;

/**
 * What a line in {@code send-command.txt} means when it is not a command for
 * the server.
 *
 * <p>A game run without a window has no window to close, and the only other
 * way to end it is to kill the process: no session wound up, no ledger closed,
 * and whatever was on the cursor dropped on the ground. One line in the inbox
 * closes it the way closing its window would.
 */
final class CommandInbox {
    /** The line that closes the game. Not a server command: those start with a slash or a letter. */
    static final String QUIT = "!quit";

    private CommandInbox() {
    }

    /** Whether any line of the inbox asks for the game to be closed. */
    static boolean asksToQuit(List<String> lines) {
        if (lines == null) return false;
        for (String raw : lines) {
            if (raw != null && raw.strip().toLowerCase(Locale.ROOT).equals(QUIT)) return true;
        }
        return false;
    }
}
