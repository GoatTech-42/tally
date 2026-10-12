package dev.doughbay.fabric.web;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPanelTest {
    private static final class Recorder implements LocalPanel.Backend {
        final List<String> calls = new ArrayList<>();

        @Override
        public byte[] png() {
            return new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        }

        @Override
        public String state() {
            return "{\"page\":\"Money\",\"notice\":\"\",\"components\":[]}";
        }

        @Override
        public synchronized void press(String id, String value) {
            calls.add("press " + id + " " + value);
        }

        @Override
        public synchronized void text(String id, String text) {
            calls.add("text " + id + " " + text);
        }
    }

    private static LocalPanel open(Recorder backend) throws Exception {
        return LocalPanel.start(0, false, "4827"::equals, backend, NOPLogger.NOP_LOGGER);
    }

    private static HttpResponse<String> get(LocalPanel panel, String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panel.port() + path)).GET();
        if (cookie != null) b.header("Cookie", cookie);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(LocalPanel panel, String path, String form, String cookie, boolean fromPage)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panel.port() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (cookie != null) b.header("Cookie", cookie);
        if (fromPage) b.header("X-GoNuts", "1");
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String signIn(LocalPanel panel) throws Exception {
        HttpResponse<String> r = post(panel, "/login", "code=4827", null, false);
        assertEquals(303, r.statusCode());
        String set = r.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(set.contains("HttpOnly") && set.contains("SameSite=Strict"));
        return set.substring(0, set.indexOf(';'));
    }

    @Test
    void aStrangerSeesTheDoorAndNothingBehindIt() throws Exception {
        Recorder backend = new Recorder();
        try (LocalPanel panel = open(backend)) {
            HttpResponse<String> door = get(panel, "/", null);
            assertEquals(200, door.statusCode());
            assertTrue(door.body().contains("name=\"code\""));
            assertFalse(door.body().contains("/panel.png"));
            assertEquals(401, get(panel, "/state", null).statusCode());
            assertEquals(401, get(panel, "/panel.png", null).statusCode());
            assertEquals(401, post(panel, "/act", "id=ctl%3Astop", null, true).statusCode());
            assertEquals(401, get(panel, "/state", "gonuts=").statusCode());
            assertEquals(401, get(panel, "/state", "gonuts=0000").statusCode());
            assertTrue(backend.calls.isEmpty());
        }
    }

    @Test
    void theDoorShowsTheLogoAndTheLogoIsAllAStrangerGets() throws Exception {
        try (LocalPanel panel = open(new Recorder())) {
            HttpResponse<byte[]> logo = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panel.port() + "/logo.png")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, logo.statusCode());
            assertEquals("image/png", logo.headers().firstValue("Content-Type").orElse(""));
            assertEquals((byte) 0x89, logo.body()[0]);
            assertEquals('P', logo.body()[1]);
            assertTrue(get(panel, "/", null).body().contains("/logo.png"));
            // And the page's font, which the door is set in too.
            HttpResponse<byte[]> font = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panel.port() + "/font.ttf")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, font.statusCode());
            assertEquals("font/ttf", font.headers().firstValue("Content-Type").orElse(""));
            assertTrue(font.body().length > 100_000);
            assertTrue(get(panel, "/", null).body().contains("/font.ttf"));
            // Nothing else under that name, and no other file by asking for one.
            assertEquals(401, get(panel, "/logo.png/../state", null).statusCode());
            assertEquals(401, get(panel, "/mascot_idle.png", null).statusCode());
        }
    }

    @Test
    void theRightPinOpensThePanelAndItsButtonsReachTheTrader() throws Exception {
        Recorder backend = new Recorder();
        try (LocalPanel panel = open(backend)) {
            assertEquals(401, post(panel, "/login", "code=1111", null, false).statusCode());
            String cookie = signIn(panel);
            assertTrue(get(panel, "/", cookie).body().contains("/panel.png"));
            assertEquals("{\"page\":\"Money\",\"notice\":\"\",\"components\":[]}", get(panel, "/state", cookie).body());
            assertEquals(200, get(panel, "/panel.png", cookie).statusCode());
            assertEquals(204, post(panel, "/act", "id=page%3Apick&value=ITEMS", cookie, true).statusCode());
            assertEquals(204, post(panel, "/act", "id=ctl%3Asearch&text=golden+apple", cookie, true).statusCode());
            assertEquals(List.of("press page:pick ITEMS", "text ctl:search golden apple"), backend.calls);
            // Locking the page ends the session it was opened with.
            assertEquals(303, post(panel, "/logout", "", cookie, false).statusCode());
            assertEquals(401, get(panel, "/state", cookie).statusCode());
        }
    }

    @Test
    void aPressThatDoesNotComeFromThePageIsRefused() throws Exception {
        Recorder backend = new Recorder();
        try (LocalPanel panel = open(backend)) {
            String cookie = signIn(panel);
            // Signed in, but posted the way another site's form would post it: without the page's own header.
            assertEquals(403, post(panel, "/act", "id=ctl%3Astop", cookie, false).statusCode());
            assertTrue(backend.calls.isEmpty());
        }
    }

    @Test
    void fiveWrongPinsLockTheDoorEvenAgainstTheRightOne() throws Exception {
        try (LocalPanel panel = open(new Recorder())) {
            long t = 1_000_000L;
            for (int i = 0; i < 4; i++) assertEquals("That is not the PIN.", panel.tryPin("0000", t));
            assertTrue(panel.tryPin("0000", t).startsWith("Too many wrong tries"));
            // The right PIN is not even looked at while the door is locked.
            assertNotNull(panel.tryPin("4827", t + 59_000));
            assertNull(panel.tryPin("4827", t + 60_001));
            // Typed with a dash or a space, it is the same PIN.
            assertNull(panel.tryPin("48 27", t + 60_002));
        }
    }

    @Test
    void eachLockIsTwiceAsLongAsTheLastUpToAQuarterOfAnHour() {
        assertEquals(60_000, LocalPanel.lockMillis(1));
        assertEquals(120_000, LocalPanel.lockMillis(2));
        assertEquals(240_000, LocalPanel.lockMillis(3));
        assertEquals(480_000, LocalPanel.lockMillis(4));
        assertEquals(900_000, LocalPanel.lockMillis(5));
        assertEquals(900_000, LocalPanel.lockMillis(40));
    }

    @Test
    void onlyThisComputerAndTheHomeNetworkAreAnswered() throws Exception {
        InetAddress self = InetAddress.getByName("127.0.0.1");
        InetAddress home = InetAddress.getByName("192.168.1.23");
        InetAddress office = InetAddress.getByName("10.4.0.9");
        InetAddress internet = InetAddress.getByName("8.8.8.8");
        InetAddress home6 = InetAddress.getByName("fd12:3456:789a::1");
        InetAddress internet6 = InetAddress.getByName("2001:4860:4860::8888");
        // Phones off: this computer and nothing else.
        assertTrue(LocalPanel.allowed(self, false));
        assertFalse(LocalPanel.allowed(home, false));
        // Phones on: the network it is on, and still never the internet.
        assertTrue(LocalPanel.allowed(self, true));
        assertTrue(LocalPanel.allowed(home, true));
        assertTrue(LocalPanel.allowed(office, true));
        assertTrue(LocalPanel.allowed(home6, true));
        assertFalse(LocalPanel.allowed(internet, true));
        assertFalse(LocalPanel.allowed(internet6, true));
        assertFalse(LocalPanel.allowed(null, true));
    }

    @Test
    void thePageIsOpenedByItsAddressNotByAName() {
        assertTrue(LocalPanel.plainHost("192.168.1.23:8765"));
        assertTrue(LocalPanel.plainHost("localhost:8765"));
        assertTrue(LocalPanel.plainHost("127.0.0.1"));
        assertTrue(LocalPanel.plainHost("[::1]:8765"));
        // A site that points its own name at an address in the house still has to say its name.
        assertFalse(LocalPanel.plainHost("panel.example.com:8765"));
        assertFalse(LocalPanel.plainHost("192.168.1.23.example.com"));
        assertFalse(LocalPanel.plainHost(""));
        assertFalse(LocalPanel.plainHost(null));
    }

    @Test
    void aRequestTooLargeToBeOneOfOursIsNotRead() throws Exception {
        String ok = "POST /act HTTP/1.1\r\nHost: localhost\r\nContent-Length: 12\r\n\r\nid=ctl%3Ago!";
        LocalPanel.Request r = LocalPanel.read(new ByteArrayInputStream(ok.getBytes(StandardCharsets.ISO_8859_1)));
        assertNotNull(r);
        assertEquals("POST", r.method());
        assertEquals("/act", r.path());
        assertEquals("ctl:go!", r.form().get("id"));
        String big = "POST /act HTTP/1.1\r\nHost: localhost\r\nContent-Length: 999999\r\n\r\n";
        assertNull(LocalPanel.read(new ByteArrayInputStream(big.getBytes(StandardCharsets.ISO_8859_1))));
        String endless = "GET /" + "a".repeat(LocalPanel.MAX_HEAD + 10) + " HTTP/1.1\r\n\r\n";
        assertNull(LocalPanel.read(new ByteArrayInputStream(endless.getBytes(StandardCharsets.ISO_8859_1))));
        // The query a picture is fetched with is not part of where it lives.
        String shot = "GET /panel.png?t=17 HTTP/1.1\r\nHost: localhost\r\n\r\n";
        assertEquals("/panel.png", LocalPanel.read(new ByteArrayInputStream(shot.getBytes(StandardCharsets.ISO_8859_1))).path());
    }

    @Test
    void aPinIsFourToEightDigitsAndNotOneAChildWouldGuess() {
        assertNull(WebPanelHost.pinProblem("4827"));
        assertNull(WebPanelHost.pinProblem("90210577"));
        assertNotNull(WebPanelHost.pinProblem("482"));
        assertNotNull(WebPanelHost.pinProblem("482716359"));
        assertNotNull(WebPanelHost.pinProblem("48a7"));
        assertNotNull(WebPanelHost.pinProblem(""));
        assertNotNull(WebPanelHost.pinProblem(null));
        assertNotNull(WebPanelHost.pinProblem("0000"));
        assertNotNull(WebPanelHost.pinProblem("1234"));
        assertNotNull(WebPanelHost.pinProblem("8765"));
    }

    @Test
    void thePinIsKeptAsAHashThatOnlyTheSamePinMatches() throws Exception {
        String salt = "00112233445566778899aabbccddeeff";
        String hash = WebPanelHost.stretch("4827", salt);
        assertEquals(64, hash.length());
        assertFalse(hash.contains("4827"));
        WebPanelHost.Config c = new WebPanelHost.Config(true, false, WebPanelHost.DEFAULT_PORT, salt, hash);
        assertTrue(WebPanelHost.matches(c, "4827"));
        assertFalse(WebPanelHost.matches(c, "4828"));
        assertFalse(WebPanelHost.matches(c, ""));
        // With no PIN set nothing matches, an empty one least of all.
        WebPanelHost.Config unset = new WebPanelHost.Config(true, false, WebPanelHost.DEFAULT_PORT, "", "");
        assertFalse(WebPanelHost.matches(unset, ""));
        assertFalse(WebPanelHost.matches(unset, "4827"));
    }

    @Test
    void withNoFileThePanelIsOffAndHasNoPin() {
        WebPanelHost.Config c = WebPanelHost.load(java.nio.file.Path.of("build", "no-such-web-panel.json"));
        assertFalse(c.enabled());
        assertFalse(c.lan());
        assertFalse(c.pinSet());
        assertEquals(WebPanelHost.DEFAULT_PORT, c.port());
    }
}
