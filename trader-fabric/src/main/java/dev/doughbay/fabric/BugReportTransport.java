package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Sends only an explicitly reviewed report, off the render thread. Never logs credentials or response bodies. */
public final class BugReportTransport {
    private static final URI ENDPOINT = URI.create("https://gonuts.at/v1/bugs");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public record Result(boolean filed, String message, String issueUrl, long retryAtMillis) { }
    public static boolean mayUseToken(String apiBaseUrl, String token) {
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) return false;
        try {
            URI base = URI.create(apiBaseUrl);
            return "gonuts.at".equalsIgnoreCase(base.getHost()) && base.getUserInfo() == null
                && base.getQuery() == null && base.getFragment() == null
                && ("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()));
        } catch (Exception e) { return false; }
    }
    static HttpRequest request(BugReportPayload payload, String key, String token) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) throw new IllegalArgumentException("Invalid report retry identifier");
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) throw new IllegalArgumentException("Install token unavailable");
        return HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(25))
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.ofByteArray(payload.json())).build();
    }
    public CompletableFuture<Result> send(BugReportPayload payload, String key, String apiBaseUrl, String token) {
        if (!mayUseToken(apiBaseUrl, token)) return CompletableFuture.completedFuture(new Result(false,
            "Use a GoNuts proxy install token, or submit with the GitHub fallback.", "", 0));
        try {
            return http.sendAsync(request(payload, key, token), HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(response -> {
                    try (var body = response.body()) {
                        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
                            try { body.close(); } catch (java.io.IOException ignored) { }
                        });
                        byte[] bytes = body.readNBytes(16385);
                        if (bytes.length > 16384) return new Result(false, "Unexpected receiver reply. Retry later.", "", 0);
                        return interpret(response.statusCode(), new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
                            response.headers().firstValue("Retry-After").orElse(""), System.currentTimeMillis());
                    } catch (Exception e) { return new Result(false, "Receiver reply unavailable. Retry later with this report.", "", 0); }
                }).orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(e -> new Result(false, "Connection failed. Retry this report; its retry identifier is retained.", "", 0));
        } catch (Exception e) { return CompletableFuture.completedFuture(new Result(false, "Report could not be sent. Nothing was uploaded.", "", 0)); }
    }
    static Result interpret(int code, String body, String retryAfter, long now) {
        if (code == 200 || code == 201) {
            try {
                var reply = JSON.readTree(body);
                String url = reply.path("issueUrl").asText("");
                if (!url.matches("https://github\\.com/cody-raves/Gonuts/issues/[1-9][0-9]*") || !reply.path("reportId").asText("").matches("br_[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException();
                return new Result(true, reply.path("duplicate").asBoolean(false) ? "Already reported; opening the existing issue is available." : "Report submitted. Thank you.", url, 0);
            } catch (Exception e) { return new Result(false, "Unexpected receiver reply. Retry this report later.", "", 0); }
        }
        if (code == 429) {
            long seconds = 3600;
            try { seconds = Math.max(1, Math.min(86400, Long.parseLong(retryAfter.strip()))); } catch (Exception ignored) { }
            return new Result(false, "Report limit reached. Try again after " + seconds + " seconds.", "", now + seconds * 1000);
        }
        return new Result(false, switch (code) {
            case 400 -> "The receiver rejected the form. Check its text and prepare it again.";
            case 401 -> "Install token missing or revoked. Check your GoNuts proxy setup.";
            case 413 -> "Report is too large. Shorten the description.";
            case 503 -> "Bug reporting is temporarily unavailable. Keep this report and try later.";
            case 502 -> "GitHub is unavailable. Retry this report later.";
            default -> "Receiver unavailable (HTTP " + code + "). Try later.";
        }, "", (code == 502 || code == 503 || code >= 500) ? now + 60_000 : 0);
    }
}
