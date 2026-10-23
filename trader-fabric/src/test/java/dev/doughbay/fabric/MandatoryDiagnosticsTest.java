package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;
import dev.doughbay.fabric.hosted.DashboardProtocol;
import static org.junit.jupiter.api.Assertions.*;
class MandatoryDiagnosticsTest {
 private final String key="gnd1_"+"A".repeat(43);
 @Test void privateHeaderOnlyAttachesToFixedHttpsReportDestinations(){
  for(String url:List.of("https://unrelated.example/v1/bugs","http://gonuts.at/v1/bugs","https://gonuts.at/anything","https://gonuts.at/v1/bugs/capabilities")){
   var r=HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString("{}")).build();assertTrue(MandatoryDiagnostics.attach(r,key).headers().firstValue(MandatoryDiagnostics.HEADER).isEmpty());
  }
  for(String url:List.of("https://gonuts.at/v1/bugs","https://gonuts.at/v1/bugs/267/evidence")){
   var r=HttpRequest.newBuilder(URI.create(url)).header("X-GoNuts-Swarm-Report","old-independent-capability").POST(HttpRequest.BodyPublishers.ofString("{}")).build();var attached=MandatoryDiagnostics.attach(r,key);assertEquals(key,attached.headers().firstValue(MandatoryDiagnostics.HEADER).orElseThrow());assertEquals("old-independent-capability",attached.headers().firstValue("X-GoNuts-Swarm-Report").orElseThrow());
  }
 }
 @Test void wireRequestDoesNotLeakSecretIntoBodyOrUrl(){var body=DashboardProtocol.JSON.createObjectNode().put("version",1);var r=MandatoryDiagnostics.uploadRequest(body,key);assertEquals(MandatoryDiagnostics.EVENTS,r.uri());assertFalse(r.uri().toString().contains(key));assertEquals("application/json",r.headers().firstValue("Content-Type").orElseThrow());assertEquals(key,r.headers().firstValue(MandatoryDiagnostics.HEADER).orElseThrow());assertFalse(body.toString().contains(key));assertThrows(IllegalArgumentException.class,()->MandatoryDiagnostics.uploadRequest(body,"bad"));}
 @Test void typedFaultAllowsOnlyGoNutsFramesAndNoExceptionMessages(){assertTrue(DiagnosticOutbox.valid("FAULT",List.of("ctx fault_stage=DRIVER_CLICK fault_type=IllegalStateException fault_age_ms=0 fault_count=1","  at dev.doughbay.fabric.Driver.tick(Driver.java:12)")));assertFalse(DiagnosticOutbox.valid("FAULT",List.of("ctx fault_stage=DRIVER_CLICK fault_type=IllegalStateException fault_age_ms=0 fault_count=1","error: account=private-player")));assertFalse(DiagnosticOutbox.valid("FAULT",List.of("ctx fault_stage=DRIVER_CLICK fault_type=IllegalStateException fault_age_ms=0 fault_count=1","  at unrelated.package.Secret.run(Secret.java:12)")));}
}
