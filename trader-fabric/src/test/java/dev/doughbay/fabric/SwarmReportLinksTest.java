package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.DashboardProtocol;
import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmReportLinksTest {
    private com.fasterxml.jackson.databind.node.ObjectNode capabilities(){var c=DashboardProtocol.JSON.createObjectNode();c.putObject("diagnosticHistory").put("supported",true).put("version",1).put("header","X-GoNuts-Swarm-Report");return c;}
    private HttpRequest request(String destination){return HttpRequest.newBuilder(URI.create(destination)).header("Idempotency-Key","fixture_retry").POST(HttpRequest.BodyPublishers.ofString("unchanged frozen body")).build();}
    @Test void tokenFrozenToReviewedEvidenceAndOptionalHeaderNeverChangesBodyOrRetryKey(){
        String evidence="ctx state=BUYING\nfixture-one",token="drt_"+"a".repeat(40);SwarmReportLinks.remember(evidence,token,1000);
        var original=request("https://gonuts.at/v1/bugs");var linked=SwarmReportLinks.attach(original,evidence,capabilities(),2000);
        assertEquals(token,linked.headers().firstValue("X-GoNuts-Swarm-Report").orElseThrow());
        assertSame(original.bodyPublisher().orElseThrow(),linked.bodyPublisher().orElseThrow());assertEquals(original.headers().firstValue("Idempotency-Key"),linked.headers().firstValue("Idempotency-Key"));
        assertSame(original,SwarmReportLinks.attach(original,evidence+"changed",capabilities(),2000));
    }
    @Test void missingCapabilityExpiryInvalidTokenAndOtherOriginsRemainNormalPublicReports(){
        String evidence="ctx state=SCANNING\nfixture-two";var original=request("https://gonuts.at/v1/bugs");
        SwarmReportLinks.remember(evidence,"sc_"+"b".repeat(32),1000);assertSame(original,SwarmReportLinks.attach(original,evidence,capabilities(),2000));
        SwarmReportLinks.remember(evidence,"drt_"+"b".repeat(40),1000);
        assertSame(original,SwarmReportLinks.attach(original,evidence,DashboardProtocol.JSON.createObjectNode(),2000));
        assertSame(original,SwarmReportLinks.attach(original,evidence,capabilities(),DiagnosticHistory.RETENTION_MS+1001));
        var foreign=request("https://example.com/v1/bugs");assertSame(foreign,SwarmReportLinks.attach(foreign,evidence,capabilities(),2000));
        var wrongPath=request("https://gonuts.at/dashboard/api/me");assertSame(wrongPath,SwarmReportLinks.attach(wrongPath,evidence,capabilities(),2000));
        var insecure=request("http://gonuts.at/v1/bugs");assertSame(insecure,SwarmReportLinks.attach(insecure,evidence,capabilities(),2000));
    }
    @Test void existingIssueEvidenceCanUseSameAuthenticatedLink(){
        String evidence="ctx state=STOPPED\nfixture-three";SwarmReportLinks.remember(evidence,"drt_"+"c".repeat(40),1000);
        assertTrue(SwarmReportLinks.attach(request("https://gonuts.at/v1/bugs/48/evidence"),evidence,capabilities(),2000).headers().firstValue("X-GoNuts-Swarm-Report").isPresent());
    }
}
