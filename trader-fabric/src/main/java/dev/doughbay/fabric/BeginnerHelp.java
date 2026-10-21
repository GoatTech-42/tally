package dev.doughbay.fabric;

import java.io.IOException;
import java.util.Map;

/** An explicit display-only action, local to this account. Never a full reset. */
final class BeginnerHelp {
    static void showGameUi() throws IOException {
        Tuning.setLocalBatch(Map.of("quiet.hide_containers", 0.0, "quiet.hide_hotbar", 0.0));
    }
    private BeginnerHelp() {}
}
