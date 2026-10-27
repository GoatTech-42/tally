package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.DashboardProtocol;
import java.net.URI;
import java.util.function.Consumer;

/** Browser launch is only a request: desktop launchers may return without opening a window. */
final class DashboardWebsiteAccess {
    private final Consumer<URI> launcher;
    private final Consumer<String> clipboard;
    private String message = "No browser? Copy this address.";

    DashboardWebsiteAccess(Consumer<URI> launcher, Consumer<String> clipboard) {
        this.launcher = launcher;
        this.clipboard = clipboard;
    }

    private static boolean retired() { return !ProjectStatus.publicServicesEnabled(); }

    String address() { return retired() ? "" : DashboardProtocol.PAGE.toASCIIString(); }
    String message() { return message; }

    void open() {
        if (retired()) { message = ProjectStatus.RETIRED; return; }
        try {
            launcher.accept(DashboardProtocol.PAGE);
            message = "No browser? Copy this address.";
        } catch (RuntimeException unavailable) {
            message = "Browser unavailable. Copy this address.";
        }
    }

    void copy() {
        if (retired()) { message = ProjectStatus.RETIRED; return; }
        try {
            clipboard.accept(address());
            message = "Address copied. Paste in your browser.";
        } catch (RuntimeException unavailable) {
            message = "Copy failed. Type the address above.";
        }
    }
}
