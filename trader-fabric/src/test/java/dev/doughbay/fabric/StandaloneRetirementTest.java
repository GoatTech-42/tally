package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.CommunityClient;
import dev.doughbay.fabric.hosted.HostedDashboardClient;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneRetirementTest {
    @TempDir Path root;

    @Test void startupDoesNotReadOrWriteHostedCredentialsOrStartPublicServices() throws Exception {
        var marker=root.resolve("existing-positions-and-settings");
        Files.writeString(marker,"durable local state must survive");
        // Missing game/shared/DB paths must not be created or opened by retired startup.
        var game=root.resolve("game");
        MandatoryDiagnostics.start(game,"standalone");
        MandatoryDiagnostics.offer("ACTION",java.util.List.of("unused"),1);
        HostedDashboardClient.INSTANCE.start(game,root.resolve("shared"),root.resolve("ledger.db"),"standalone");
        CommunityClient.INSTANCE.start(game,"standalone");
        HostedDashboardClient.INSTANCE.pair("ABCDEFGH","unused");
        CommunityClient.INSTANCE.enable();
        assertFalse(ProjectStatus.publicServicesEnabled());
        assertFalse(Files.exists(game));assertFalse(Files.exists(root.resolve("shared")));
        assertFalse(Files.exists(root.resolve("ledger.db")));
        assertEquals("durable local state must survive",Files.readString(marker));
        assertFalse(HostedDashboardClient.INSTANCE.view().linked());
        assertFalse(HostedDashboardClient.INSTANCE.view().online());
        assertEquals(0,HostedDashboardClient.INSTANCE.view().retryAt());
        assertFalse(CommunityClient.INSTANCE.view().path("enabled").asBoolean());
        assertFalse((boolean)MandatoryDiagnostics.status().get("required"));
        assertEquals(0,MandatoryDiagnostics.status().get("queuedInMemory"));
        assertThrows(java.util.concurrent.ExecutionException.class,()->HostedDashboardClient.INSTANCE
                .enrollCommunity(dev.doughbay.fabric.hosted.DashboardProtocol.JSON.createObjectNode()).get(1,TimeUnit.SECONDS));
    }

    @Test void everyPublicReportEntryPointReturnsRetiredWithoutPreparingOrSending() throws Exception {
        var transport=new BugReportTransport();
        assertFalse(transport.automaticCapabilities().path("configured").asBoolean());
        assertFalse(transport.supportsLogAttachments("https://unreachable.invalid","unused").get(1,TimeUnit.SECONDS));
        // Null/invalid legacy payloads are never inspected, serialized, credentialed or sent.
        assertFalse(transport.send(null,"","","").get(1,TimeUnit.SECONDS).filed());
        assertFalse(transport.sendRelated(null,"","",347).get(1,TimeUnit.SECONDS).filed());
        assertTrue(transport.sendAutomatic(null,"").permanent());
        var response=dev.doughbay.fabric.hosted.DashboardProtocol.JSON.readTree(WebReports.change(
                Map.of("action","submit","consent","true","description","unused")));
        assertFalse(response.path("ok").asBoolean());assertTrue(response.path("note").asText().contains("retired"));
    }
}
