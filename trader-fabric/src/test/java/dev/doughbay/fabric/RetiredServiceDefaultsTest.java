package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retired project relays (gonuts.at and its last known address) must never
 * receive a saved key, an order sweep or a market query: an install upgraded
 * from a pre-retirement config falls back to DonutSMP direct, while an
 * owner-run proxy keeps working untouched.
 */
final class RetiredServiceDefaultsTest {

    private static final String DONUT = dev.doughbay.api.DonutApiConfig.DEFAULT_BASE_URL;

    @TempDir
    Path dir;

    @Test
    void retiredRelayAddressesResolveToDonutSmpDirect() {
        for (String retired : new String[] {
                "https://gonuts.at", "https://GONUTS.AT/v1",
                "https://api.gonuts.at", "http://147.189.172.152:8790"}) {
            assertEquals(DONUT, DoughBayConfig.resolveApiBaseUrl(retired), retired);
        }
    }

    @Test
    void spacePaddedRetiredAddressesResolveToDonutSmpDirectImmediately() {
        // A padded saved source must not slip through as an "owner proxy"
        // until the next reload strips it: the fallback applies at once.
        for (String retired : new String[] {
                " https://gonuts.at ", "\thttps://api.gonuts.at\n", " http://147.189.172.152:8790 "}) {
            assertEquals(DONUT, DoughBayConfig.resolveApiBaseUrl(retired), retired);
        }
    }

    @Test
    void spacePaddedOwnerProxiesAreStrippedAndKept() {
        assertEquals("http://localhost:8123",
                DoughBayConfig.resolveApiBaseUrl(" http://localhost:8123 "));
        assertEquals("https://gonuts.at.example.invalid",
                DoughBayConfig.resolveApiBaseUrl(" https://gonuts.at.example.invalid "));
    }

    @Test
    void feedUploaderDropsAPaddedRetiredDestination() {
        assertEquals("", new FeedUploader(" https://gonuts.at ", "key").baseUrl());
    }

    @Test
    void missingAndBlankAddressesResolveToDonutSmpDirect() {
        assertEquals(DONUT, DoughBayConfig.resolveApiBaseUrl(null));
        assertEquals(DONUT, DoughBayConfig.resolveApiBaseUrl("  "));
    }

    @Test
    void ownerRunProxiesAndLookalikeNamesAreUntouched() {
        assertEquals("http://localhost:8123",
                DoughBayConfig.resolveApiBaseUrl("http://localhost:8123"));
        assertEquals("https://hive.example.test",
                DoughBayConfig.resolveApiBaseUrl("https://hive.example.test"));
        assertEquals("https://gonuts.at.example.invalid",
                DoughBayConfig.resolveApiBaseUrl("https://gonuts.at.example.invalid"));
    }

    @Test
    void savingARetiredSourcePersistsDonutSmpDirectInstead() throws Exception {
        DoughBayConfig.saveApiBaseUrl(dir, "https://gonuts.at");
        JsonNode root = new ObjectMapper().readTree(
                Files.readString(dir.resolve("config.json")));
        assertEquals(DONUT, root.get("apiBaseUrl").asText());

        DoughBayConfig.saveApiBaseUrl(dir, "https://hive.example.test");
        root = new ObjectMapper().readTree(
                Files.readString(dir.resolve("config.json")));
        assertEquals("https://hive.example.test", root.get("apiBaseUrl").asText());
    }

    @Test
    void feedUploaderDropsARetiredDestinationButKeepsItsOwnServer() {
        assertEquals("", new FeedUploader("https://gonuts.at", "key").baseUrl());
        assertEquals("", new FeedUploader("https://api.gonuts.at", "key").baseUrl());
        assertEquals("https://hive.example.test",
                new FeedUploader("https://hive.example.test/", "key").baseUrl());
    }

    @Test
    void marketQueriesStayLocalWhenTheSavedSourceIsRetired() {
        MarketQueryClient retired = new MarketQueryClient("https://gonuts.at", true);
        assertEquals("", retired.baseUrl());
        assertFalse(retired.useServer());

        MarketQueryClient own = new MarketQueryClient("https://hive.example.test", true);
        assertEquals("https://hive.example.test", own.baseUrl());
        assertTrue(own.useServer());
    }
}
