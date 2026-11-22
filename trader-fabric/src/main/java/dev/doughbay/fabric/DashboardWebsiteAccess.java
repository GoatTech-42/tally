package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.DashboardProtocol;
import java.net.URI;
import java.util.function.Consumer;

/** Browser launch is only a request: desktop launchers may return without opening a window. */
final class DashboardWebsiteAccess {
    private final Consumer<URI> launcher;
    private final Consumer<String> clipboard;
    private String message = "no browser? copy this address.";

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
            message = "no browser? copy this address.";
        } catch (RuntimeException unavailable) {
            message = "browser unavailable. copy this address.";
        }
    }

    void copy() {
        if (retired()) { message = ProjectStatus.RETIRED; return; }
        try {
            clipboard.accept(address());
            message = "address copied. paste in your browser.";
        } catch (RuntimeException unavailable) {
            message = "copy failed. type the address above.";
        }
    }
}
