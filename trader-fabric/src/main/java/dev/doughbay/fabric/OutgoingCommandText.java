package dev.doughbay.fabric;

import net.minecraft.util.StringUtil;

/** Refuses text that vanilla rejects with multiplayer.disconnect.illegal_characters. */
final class OutgoingCommandText {
    private OutgoingCommandText() { }

    static boolean valid(String command) {
        return command != null && command.codePoints().allMatch(StringUtil::isAllowedChatCharacter);
    }

    static void requireValid(String command) {
        if (!valid(command)) {
            // Never include command arguments: these can contain recipients or payment amounts.
            throw new IllegalArgumentException("Command contains a character Minecraft does not allow; remove formatting codes or control characters");
        }
    }
}
