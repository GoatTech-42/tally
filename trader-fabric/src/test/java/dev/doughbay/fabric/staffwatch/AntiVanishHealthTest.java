package dev.doughbay.fabric.staffwatch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AntiVanishHealthTest {
    @Test void probeResponseWithoutKnownStaffIsDistinguishableFromNoResponse() {
        var health = new AntiVanishHealth();
        health.probeRequest();
        assertTrue(health.summary().contains("avProbeAttempts=1 avProbeReplies=0"));
        health.probeReply(35);
        health.reviewedProbe(0, 0);
        assertTrue(health.summary().contains("avProbeReplies=1 avProbeNames=35 avProbeKnownStaff=0 avProbeHiddenCandidates=0 avAlertRequests=0"));
    }

    @Test void initialPacketsQueuedEvidenceAndAlertRequestsRemainSeparate() {
        var health = new AntiVanishHealth();
        health.bundle(); health.modePacket(true); health.modePacket(false); health.queuedMode();
        health.reviewedProbe(2, 1); health.alertRequest();
        assertTrue(health.summary().contains("avBundles=1 avModePackets=2 avInitialModePacketsIgnored=1 avModesQueued=1"));
        assertTrue(health.summary().contains("avProbeKnownStaff=2 avProbeHiddenCandidates=1 avAlertRequests=1"));
    }
    @Test void distinguishMissingReplyFromUnmatchedReplyAndLocalSendFailure() {
        var health = new AntiVanishHealth();
        health.probeRequest(); health.probeSendFailed();
        health.suggestionPacket(false); health.suggestionPacket(true); health.probeReply(0);
        String summary=health.summary();
        assertTrue(summary.contains("avProbeAttempts=1 avProbeReplies=1"));
        assertTrue(summary.contains("avSuggestionPackets=2 avUnmatchedSuggestionReplies=1 avProbeSendFailures=1"));
        assertFalse(summary.contains("minecraft:msg"));
    }
}
