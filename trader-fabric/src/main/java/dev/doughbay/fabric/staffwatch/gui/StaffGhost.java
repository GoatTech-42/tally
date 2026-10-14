package dev.doughbay.fabric.staffwatch.gui;

import java.util.UUID;
import dev.doughbay.fabric.StaffWatchAlert;
import dev.doughbay.fabric.staffwatch.ReconnectController;
import dev.doughbay.fabric.staffwatch.WatchConfig;

/** Shared detector entry point; connection closure always precedes presentation. */
public final class StaffGhost {
    private StaffGhost() { }

    public static void watching(UUID id, String name, int others) {
        if (!WatchConfig.get().enabled) return;
        ReconnectController.INSTANCE.alert(name);
        StaffWatchAlert.show(name, others);
    }
}
