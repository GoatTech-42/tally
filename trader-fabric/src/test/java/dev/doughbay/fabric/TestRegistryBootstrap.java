package dev.doughbay.fabric;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * 1.21.11 guards every BuiltInRegistries touch behind Bootstrap.bootStrap();
 * a single test that reaches an item/component class before bootstrapping
 * poisons the shared test-worker JVM with ExceptionInInitializerError.
 * Bootstrapping at launcher-session open guarantees registries are ready
 * before any test class initializes. Idempotent across forked workers.
 */
public class TestRegistryBootstrap implements LauncherSessionListener {
    @Override public void launcherSessionOpened(LauncherSession session) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
}
