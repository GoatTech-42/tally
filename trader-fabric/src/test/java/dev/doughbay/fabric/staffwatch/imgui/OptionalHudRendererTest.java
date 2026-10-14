package dev.doughbay.fabric.staffwatch.imgui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OptionalHudRendererTest {
    @Test void headlessVersionsNeverInitializeOrDrawAndWarnOnlyOnce() {
        for (String missing : new String[]{null, "", "  "}) {
            for (boolean glMissing : new boolean[]{true, false}) {
                var renderer = new OptionalHudRenderer();
                var warnings = new ArrayList<String>();
                for (int frame = 0; frame < 100; frame++)
                    renderer.render(() -> glMissing ? missing : "4.6", () -> glMissing ? "4.60" : missing,
                            () -> fail("Must not initialize native ImGui"), () -> fail("Must not draw"), warnings::add);
                assertEquals(1, warnings.size());
            }
        }
    }
    @Test void normalGraphicsInitializeOnceAndDrawEveryFrame() {
        var renderer = new OptionalHudRenderer();
        var starts = new AtomicInteger();
        var frames = new AtomicInteger();
        for (int i = 0; i < 3; i++)
            renderer.render(() -> "4.6.0 NVIDIA", () -> "4.60 NVIDIA", starts::incrementAndGet,
                    frames::incrementAndGet, reason -> fail(reason));
        assertEquals(1, starts.get());
        assertEquals(3, frames.get());
    }
    @Test void originalEmptyVersionCrashAndNativeFailuresCannotEscapeOrRetry() {
        for (boolean nativeFailure : new boolean[]{true, false}) {
            var renderer = new OptionalHudRenderer();
            var attempts = new AtomicInteger();
            var warnings = new ArrayList<String>();
            for (int frame = 0; frame < 3; frame++)
                renderer.render(() -> "4.6", () -> "4.60", () -> {
                    attempts.incrementAndGet();
                    if (nativeFailure) throw new UnsatisfiedLinkError("missing native library");
                    Integer.parseInt("");
                }, () -> fail("Failed initialization must not draw"), warnings::add);
            assertEquals(1, attempts.get());
            assertEquals(1, warnings.size());
        }
    }
    @Test void graphicsProbeAndDrawFailuresAreAlsoIsolated() {
        for (boolean probeFailure : new boolean[]{true, false}) {
            var renderer = new OptionalHudRenderer();
            var warnings = new ArrayList<String>();
            for (int frame = 0; frame < 3; frame++)
                renderer.render(() -> { if (probeFailure) throw new IllegalStateException("no context"); return "4.6"; },
                        () -> "4.60", () -> {}, () -> { throw new IllegalStateException("draw failed"); }, warnings::add);
            assertEquals(1, warnings.size());
        }
    }
}
