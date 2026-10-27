package dev.doughbay.fabric.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Keeps the local web panel: whether it is on, who may reach it, and the PIN.
 *
 * <p>All of that is set on the GoNuts screen in game and kept in
 * {@code web-panel.json}, apart from the settings a settings code carries and
 * apart from anything the panel's own Settings page can change: a page reached
 * with the PIN must not be able to change the PIN or open itself wider. The
 * PIN is not kept at all, only a salted, stretched hash of it.
 */
public final class WebPanelHost {
    public static final int DEFAULT_PORT = 8765;
    public static final int PIN_MIN = 4;
    public static final int PIN_MAX = 8;
    private static final int STRETCH = 120_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Config(boolean enabled, boolean lan, int port, String salt, String hash) {
        boolean pinSet() {
            return !salt.isBlank() && !hash.isBlank();
        }
    }

    private final Logger log;
    private final Path file;
    private final LocalPanel.Backend backend;
    private Config config;
    private LocalPanel panel;
    private SharedPanelLease lease;
    private boolean sharedHost;
    private long nextRetry;
    private String problem = "";
    private boolean bindFailed;

    private WebPanelHost(Logger log, Path configDir, LocalPanel.Backend backend) {
        this.log = log;
        this.file = configDir.resolve("web-panel.json");
        this.backend = backend;
        this.config = load(file);
    }

    public static WebPanelHost start(Logger log, Path configDir, LocalPanel.Backend backend) {
        WebPanelHost host = new WebPanelHost(log, configDir, backend);
        host.apply();
        return host;
    }

    static Config load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                JsonNode n = MAPPER.readTree(Files.readString(file));
                int port = n.path("port").asInt(DEFAULT_PORT);
                return new Config(n.path("enabled").asBoolean(false), n.path("lan").asBoolean(false),
                        port < 1024 || port > 65535 ? DEFAULT_PORT : port,
                        n.path("salt").asText(""), n.path("hash").asText(""));
            }
        } catch (Exception ignored) {
            // an unreadable file is an unset panel: off, with no PIN
        }
        return new Config(false, false, DEFAULT_PORT, "", "");
    }

    private void save() throws Exception {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("enabled", config.enabled());
        n.put("lan", config.lan());
        n.put("port", config.port());
        n.put("salt", config.salt());
        n.put("hash", config.hash());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(n));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Why a PIN will not do, or null when it will. */
    public static String pinProblem(String pin) {
        if (pin == null || !pin.matches("[0-9]+")) return "A PIN is digits only";
        if (pin.length() < PIN_MIN || pin.length() > PIN_MAX) return "A PIN is " + PIN_MIN + " to " + PIN_MAX + " digits";
        if (pin.chars().distinct().count() == 1) return "Not the same digit " + pin.length() + " times";
        if ("0123456789".contains(pin) || "9876543210".contains(pin)) return "Not digits in a row";
        return null;
    }

    static String stretch(String pin, String saltHex) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), HexFormat.of().parseHex(saltHex), STRETCH, 256);
        return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
    }

    static boolean matches(Config c, String typed) {
        if (!c.pinSet() || typed == null || typed.isEmpty() || typed.length() > 32) return false;
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(stretch(typed, c.salt())), HexFormat.of().parseHex(c.hash()));
        } catch (Exception e) {
            return false;
        }
    }

    /** Sets the PIN and turns the panel on, which is what setting one means. Returns what went wrong, or null. */
    public synchronized String setPin(String pin) {
        String bad = pinProblem(pin);
        if (bad != null) return bad;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String saltHex = HexFormat.of().formatHex(salt);
            config = new Config(true, config.lan(), config.port(), saltHex, stretch(pin, saltHex));
            save();
        } catch (Exception e) {
            return "The PIN could not be saved: " + e.getMessage();
        }
        apply();
        return null;
    }

    /** Whether phones and other devices on the same network may reach it, or this computer alone. */
    public synchronized void setLan(boolean lan) {
        config = new Config(config.enabled(), lan, config.port(), config.salt(), config.hash());
        persistAndApply();
    }

    public synchronized void setEnabled(boolean enabled) {
        config = new Config(enabled && config.pinSet(), config.lan(), config.port(), config.salt(), config.hash());
        persistAndApply();
    }

    private void persistAndApply() {
        try {
            save();
        } catch (Exception e) {
            problem = "could not save web-panel.json: " + e.getMessage();
        }
        apply();
    }

    private synchronized void apply() {
        if (panel != null) {
            panel.close();
            panel = null;
        }
        releaseLease();
        sharedHost = false;
        problem = "";
        bindFailed = false;
        if (!config.enabled() || !config.pinSet()) return;
        Config c = config;
        try {
            lease = SharedPanelLease.acquire(file.getParent());
            if (lease == null) {
                sharedHost = true;
                return;
            }
            panel = LocalPanel.start(c.port(), c.lan(), typed -> matches(c, typed), backend, log);
            log.info("DoughBay web panel: on at port {} ({})", c.port(),
                    c.lan() ? "this computer and its home network" : "this computer only");
        } catch (Exception e) {
            releaseLease();
            bindFailed = true;
            problem = "port " + c.port() + " could not be opened (" + e.getMessage() + ")";
            log.warn("DoughBay web panel: {}", problem);
        }
    }

    public synchronized boolean pinSet() {
        return config.pinSet();
    }

    public synchronized boolean enabled() {
        return config.enabled();
    }

    public synchronized boolean lan() {
        return config.lan();
    }

    public synchronized boolean running() {
        return panel != null;
    }

    /** A remaining hive client can take over when the host closes. */
    public synchronized void tick() {
        long now = System.currentTimeMillis();
        // A shared host waits for the other client to close; a failed takeover retries the bind.
        if ((!sharedHost && !bindFailed) || now < nextRetry) return;
        nextRetry = now + 5000;
        config = load(file);
        apply();
    }

    public synchronized void close() {
        if (panel != null) { panel.close(); panel = null; }
        sharedHost = false;
        bindFailed = false;
        releaseLease();
    }

    private void releaseLease() {
        if (lease != null) {
            try { lease.close(); } catch (Exception ignored) { }
            lease = null;
        }
    }

    public synchronized String status() {
        if (!config.pinSet()) return "off: set a PIN to turn it on";
        if (!config.enabled()) return "off";
        if (sharedHost) return "one shared panel hosted by another hive client";
        if (panel == null) return "not running: " + problem;
        return config.lan() ? "on for this PC and phones on your Wi-Fi" : "on for this PC only";
    }

    /** Where to point a browser: this computer's own address first, then the ones a phone would use. */
    public synchronized List<String> addresses() {
        List<String> out = new ArrayList<>();
        if (panel == null && !sharedHost) return out;
        out.add("http://localhost:" + config.port());
        if (!config.lan()) return out;
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        out.add("http://" + a.getHostAddress() + ":" + config.port());
                    }
                }
            }
        } catch (Exception ignored) {
            // no list of addresses is not no panel; the computer's own address still works
        }
        return out;
    }
}
