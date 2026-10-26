package dev.doughbay.fabric;

/** Owner policy: outgoing command text is ASCII letters, digits and spaces only. */
final class OutgoingCommandText {
    private OutgoingCommandText() { }

    static boolean valid(String command) {
        return command != null && !command.isBlank() && command.chars().allMatch(c ->
                c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                || c >= '0' && c <= '9' || c == ' ');
    }

    static void requireValid(String command) {
        if (!valid(command)) {
            // Never include command arguments: these can contain recipients or payment amounts.
            throw new IllegalArgumentException("Command refused: only ASCII A-Z, a-z, 0-9 and spaces are allowed");
        }
    }
}
