package dev.doughbay.fabric.web;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The control panel as a web page served by the mod itself: the pages and
 * buttons the Discord panel has, with no Discord and no token.
 *
 * <p>This page starts and stops a trader that spends real money, so who can
 * reach it is the whole design. It answers the computer it runs on, and
 * devices on the same home network only when that is switched on; never an
 * address from outside one. Nothing is shown and nothing is done without the
 * PIN the player set on the GoNuts screen. A PIN is short, so guessing is what
 * is made slow: five wrong tries lock the door for a minute, and each lock
 * after that is twice as long, up to a quarter of an hour. A button press has
 * to come from the page itself, not from another site open in the same browser.
 *
 * <p>It speaks plain HTTP, written out here rather than taken from the JDK's
 * own server: the Java runtime a launcher ships is cut down to what the game
 * needs, and that module is not promised to be in it.
 */
public final class LocalPanel implements AutoCloseable {
    /** What the page shows and what its buttons do; the Discord panel's own code stands behind it. */
    public interface Backend {
        byte[] png() throws Exception;

        String state();

        void press(String id, String value);

        void text(String id, String text);

        /** One of the panel's own pages as JSON: settings, items, rivals. Null when there is no such page. */
        default String data(String name, Map<String, String> query) {
            return null;
        }

        /** A change one of those pages asks for; the JSON that comes back says what was done. Null when there is none such. */
        default String change(String name, Map<String, String> form) {
            return null;
        }

        /** An item's picture as a PNG, or null when there is none to show. */
        default byte[] icon(String itemId) {
            return null;
        }
    }

    /** The authenticated panel's own page/assets, also used by the outbound hosted relay. */
    public static String pageHtml() { return Pages.APP; }
    public static byte[] asset(String name) {
        return switch (name) {
            case "logo" -> bundled("/assets/doughbay/textures/gui/logo.png");
            case "font" -> bundled("/doughbay/web/Monocraft.ttf");
            default -> new byte[0];
        };
    }

    static final int MAX_HEAD = 8 * 1024;
    /** Room for a watch list typed out in full, and no more. */
    static final int MAX_BODY = 64 * 1024;
    static final int MAX_SESSIONS = 16;
    static final int WRONG_TRIES = 5;
    static final long LOCK_MILLIS = 60_000L;
    static final long LOCK_MAX_MILLIS = 15 * 60_000L;
    private static final long PNG_FRESH_MILLIS = 2_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServerSocket server;
    private final Backend backend;
    private final java.util.function.Predicate<String> pinMatches;
    private final boolean lan;
    private final Logger log;
    private final ExecutorService pool;
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private int wrong;
    private int locks;
    private long lockedUntil;
    private byte[] png;
    private long pngAt;

    private LocalPanel(ServerSocket server, boolean lan, java.util.function.Predicate<String> pinMatches, Backend backend, Logger log) {
        this.server = server;
        this.lan = lan;
        this.pinMatches = pinMatches;
        this.backend = backend;
        this.log = log;
        // A page of the item grid asks for a hundred small pictures at once.
        this.pool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "doughbay-web");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Port 0 takes any free port; see {@link #port()}. The PIN is not held
     * here, only a way to ask whether what was typed is it.
     */
    public static LocalPanel start(int port, boolean lan, java.util.function.Predicate<String> pinMatches,
                                   Backend backend, Logger log) throws IOException {
        ServerSocket socket = lan ? new ServerSocket(port, 16) : new ServerSocket(port, 16, InetAddress.getLoopbackAddress());
        LocalPanel panel = new LocalPanel(socket, lan, pinMatches, backend, log);
        Thread t = new Thread(panel::acceptLoop, "doughbay-web-accept");
        t.setDaemon(true);
        t.start();
        return panel;
    }

    public int port() {
        return server.getLocalPort();
    }

    public boolean lan() {
        return lan;
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
            // closing is all that was wanted
        }
        pool.shutdownNow();
    }

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /**
     * Whether a caller's address is one this panel answers at all: this
     * computer always, the home network when that is on, the internet never.
     */
    static boolean allowed(InetAddress from, boolean lan) {
        if (from == null) return false;
        if (from.isLoopbackAddress()) return true;
        if (!lan) return false;
        if (from.isSiteLocalAddress() || from.isLinkLocalAddress()) return true;
        // fc00::/7, the private range of IPv6, which Java does not count as site-local.
        return from instanceof Inet6Address && (from.getAddress()[0] & 0xFE) == 0xFC;
    }

    /**
     * The Host a browser names has to be an address or localhost. A page on
     * the internet can point its own name at an address inside the house and
     * have the browser call it "the same site"; it cannot make the browser
     * say anything but that name here.
     */
    static boolean plainHost(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.strip().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            return end > 1 && h.substring(1, end).matches("[0-9a-f:.%]+");
        }
        int colon = h.lastIndexOf(':');
        if (colon >= 0) h = h.substring(0, colon);
        return h.equals("localhost") || h.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                if (!allowed(socket.getInetAddress(), lan)) {
                    socket.close();
                    continue;
                }
                pool.execute(() -> serve(socket));
            } catch (IOException e) {
                if (!server.isClosed()) log.debug("DoughBay web panel: accept failed: {}", e.toString());
            } catch (RuntimeException e) {
                // the pool is shutting down
            }
        }
    }

    record Request(String method, String path, Map<String, String> query, Map<String, String> headers,
                   Map<String, String> form) {
        String cookie(String name) {
            String all = headers.getOrDefault("cookie", "");
            for (String part : all.split(";")) {
                String p = part.strip();
                if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
            }
            return "";
        }
    }

    /** One request off the wire, or null when it is not one this panel will read. */
    static Request read(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0 || head.size() >= MAX_HEAD) return null;
            head.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3))
                    ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) return null;
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.put(lines[i].substring(0, colon).strip().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).strip());
        }
        Map<String, String> form = new HashMap<>();
        Map<String, String> asked = new HashMap<>();
        int length;
        try {
            length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (length < 0 || length > MAX_BODY) return null;
        if (length > 0) {
            byte[] body = in.readNBytes(length);
            pairs(new String(body, StandardCharsets.UTF_8), form);
        }
        String path = first[1];
        int query = path.indexOf('?');
        if (query >= 0) {
            pairs(path.substring(query + 1), asked);
            path = path.substring(0, query);
        }
        return new Request(first[0].toUpperCase(Locale.ROOT), path, asked, headers, form);
    }

    /** name=value pairs, as a form or the end of an address carries them. */
    private static void pairs(String text, Map<String, String> into) {
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                into.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // a pair that does not decode is a pair that is not there
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            socket.setSoTimeout(5_000);
            Request request = read(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            if (request == null) {
                send(out, 400, "text/plain; charset=utf-8", "Bad request".getBytes(StandardCharsets.UTF_8), "");
                return;
            }
            respond(request, out);
        } catch (IOException e) {
            // a browser that hung up; nothing to do about it
        } catch (Exception e) {
            log.warn("DoughBay web panel: {}", e.toString());
        }
    }

    private void respond(Request r, OutputStream out) throws Exception {
        if (!plainHost(r.headers().get("host"))) {
            send(out, 403, "text/plain; charset=utf-8", "Open this page by its address.".getBytes(StandardCharsets.UTF_8), "");
            return;
        }
        // The mod's own logo and the page's font, which the door uses too: the
        // only things served to anybody who can reach the page.
        if (r.method().equals("GET") && r.path().equals("/logo.png")) {
            byte[] logo = bundled("/assets/doughbay/textures/gui/logo.png");
            send(out, logo.length == 0 ? 404 : 200, "image/png", logo, "", logo.length > 0);
            return;
        }
        if (r.method().equals("GET") && r.path().equals("/font.ttf")) {
            byte[] font = bundled("/doughbay/web/Monocraft.ttf");
            send(out, font.length == 0 ? 404 : 200, "font/ttf", font, "", font.length > 0);
            return;
        }
        boolean in = sessions.contains(r.cookie("gonuts")) && !r.cookie("gonuts").isBlank();
        if (r.method().equals("POST") && r.path().equals("/login")) {
            login(r, out);
            return;
        }
        if (r.method().equals("GET") && r.path().equals("/")) {
            send(out, 200, "text/html; charset=utf-8",
                    (in ? Pages.APP : Pages.login("")).getBytes(StandardCharsets.UTF_8), "");
            return;
        }
        if (!in) {
            send(out, 401, "text/plain; charset=utf-8", "Sign in first.".getBytes(StandardCharsets.UTF_8), "");
            return;
        }
        if (r.method().equals("GET") && r.path().equals("/panel.png")) {
            send(out, 200, "image/png", picture(), "");
            return;
        }
        if (r.method().equals("GET") && r.path().equals("/state")) {
            send(out, 200, "application/json; charset=utf-8", backend.state().getBytes(StandardCharsets.UTF_8), "");
            return;
        }
        // The panel's own pages. What they show is read here; what they
        // change has to come from the page's own script, like a button press.
        if (r.path().startsWith("/api/") && r.path().substring(5).matches("[a-z]{1,24}")) {
            String name = r.path().substring(5);
            String json = null;
            if (r.method().equals("GET")) {
                json = backend.data(name, r.query());
            } else if (r.method().equals("POST")) {
                if (!"1".equals(r.headers().get("x-gonuts"))) {
                    send(out, 403, "text/plain; charset=utf-8", "Not from the panel.".getBytes(StandardCharsets.UTF_8), "");
                    return;
                }
                json = backend.change(name, r.form());
            }
            if (json == null) {
                send(out, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8), "");
            } else {
                send(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), "");
            }
            return;
        }
        if (r.method().equals("GET") && r.path().startsWith("/item/") && r.path().endsWith(".png")) {
            String id = r.path().substring(6, r.path().length() - 4);
            byte[] icon = id.matches("([a-z0-9_.\\-]{1,40}:)?[a-z0-9_.\\-]{1,64}") ? backend.icon(id) : null;
            if (icon == null) send(out, 404, "text/plain; charset=utf-8", new byte[0], "");
            else send(out, 200, "image/png", icon, "", true);
            return;
        }
        if (r.method().equals("POST") && r.path().equals("/act")) {
            // Only the page's own script sets this header; a form on some
            // other site cannot, so it cannot press a button here.
            if (!"1".equals(r.headers().get("x-gonuts"))) {
                send(out, 403, "text/plain; charset=utf-8", "Not from the panel.".getBytes(StandardCharsets.UTF_8), "");
                return;
            }
            String id = r.form().getOrDefault("id", "");
            if (r.form().containsKey("text")) backend.text(id, r.form().get("text"));
            else backend.press(id, r.form().getOrDefault("value", ""));
            synchronized (this) {
                png = null;
            }
            send(out, 204, "text/plain; charset=utf-8", new byte[0], "");
            return;
        }
        if (r.method().equals("POST") && r.path().equals("/logout")) {
            sessions.remove(r.cookie("gonuts"));
            send(out, 303, "text/plain; charset=utf-8", new byte[0],
                    "Location: /\r\nSet-Cookie: gonuts=; Max-Age=0; HttpOnly; SameSite=Strict; Path=/\r\n");
            return;
        }
        send(out, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8), "");
    }

    private void login(Request r, OutputStream out) throws IOException {
        long now = System.currentTimeMillis();
        String verdict = tryPin(r.form().getOrDefault("code", ""), now);
        if (verdict != null) {
            send(out, 401, "text/html; charset=utf-8", Pages.login(verdict).getBytes(StandardCharsets.UTF_8), "");
            return;
        }
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        StringBuilder token = new StringBuilder();
        for (byte b : raw) token.append(String.format(Locale.ROOT, "%02x", b));
        if (sessions.size() >= MAX_SESSIONS) sessions.clear();
        sessions.add(token.toString());
        send(out, 303, "text/plain; charset=utf-8", new byte[0],
                "Location: /\r\nSet-Cookie: gonuts=" + token + "; HttpOnly; SameSite=Strict; Path=/\r\n");
    }

    /** Null when the PIN is right; otherwise what to tell the person at the door. */
    synchronized String tryPin(String typed, long now) {
        if (now < lockedUntil) {
            return "Too many wrong tries. Wait " + waitText(lockedUntil - now) + ".";
        }
        if (pinMatches.test(digits(typed))) {
            wrong = 0;
            locks = 0;
            return null;
        }
        if (++wrong >= WRONG_TRIES) {
            wrong = 0;
            long lock = lockMillis(++locks);
            lockedUntil = now + lock;
            log.warn("DoughBay web panel: {} wrong PINs in a row; locked for {}", WRONG_TRIES, waitText(lock));
            return "Too many wrong tries. Wait " + waitText(lock) + ".";
        }
        return "That is not the PIN.";
    }

    /** A minute for the first lock and twice as long for each one after, up to a quarter of an hour. */
    static long lockMillis(int locks) {
        long lock = LOCK_MILLIS;
        for (int i = 1; i < locks && lock < LOCK_MAX_MILLIS; i++) lock *= 2;
        return Math.min(LOCK_MAX_MILLIS, lock);
    }

    private static String waitText(long millis) {
        long seconds = Math.max(1, (millis + 999) / 1000);
        return seconds >= 120 ? (seconds + 59) / 60 + " min" : seconds + " s";
    }

    private static final Map<String, byte[]> BUNDLED = new ConcurrentHashMap<>();

    /** A file that ships inside the mod, read once. Only ever called with a name written in this class. */
    private static byte[] bundled(String resource) {
        return BUNDLED.computeIfAbsent(resource, name -> {
            try (InputStream in = LocalPanel.class.getResourceAsStream(name)) {
                return in == null ? new byte[0] : in.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });
    }

    private synchronized byte[] picture() throws Exception {
        long now = System.currentTimeMillis();
        if (png == null || now - pngAt > PNG_FRESH_MILLIS) {
            png = backend.png();
            pngAt = now;
        }
        return png;
    }

    private static void send(OutputStream out, int status, String type, byte[] body, String extraHeaders) throws IOException {
        send(out, status, type, body, extraHeaders, false);
    }

    /**
     * @param keep the browser may hold on to this for a day: a picture or the
     *             font, which are the same every time and are what a page of
     *             the item grid asks for by the hundred
     */
    private static void send(OutputStream out, int status, String type, byte[] body, String extraHeaders, boolean keep)
            throws IOException {
        String reason = switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 303 -> "See Other";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            default -> "Not Found";
        };
        String head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: " + (keep ? "private, max-age=86400" : "no-store") + "\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "X-Frame-Options: DENY\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self'\r\n"
                + extraHeaders
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
