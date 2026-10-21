package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SwarmSavingsWireTest {
    private com.fasterxml.jackson.databind.node.ObjectNode performance(){
        var p=DashboardProtocol.JSON.createObjectNode().put("session","123e4567-e89b-12d3-a456-426614174000").put("startedAt",1000).put("observedAt",2000);
        p.set("discoverySavings",new SwarmReadSavings().view());p.put("credential","never send");return p;
    }
    @Test void sendsOnlyExplicitNegotiatedSchemaAndContribution(){
        var frame=DashboardProtocol.JSON.createObjectNode();var p=performance();
        SwarmSavingsWire.attach(frame,p,false,true,2000);assertFalse(frame.has("readSavings"));
        SwarmSavingsWire.attach(frame,p,true,false,2000);assertFalse(frame.has("readSavings"));
        SwarmSavingsWire.attach(frame,p,true,true,2000);assertTrue(frame.has("readSavings"));
        assertEquals(12,frame.path("readSavings").size());
        assertTrue(DashboardProtocol.hello("test","fixture","fixture").path("capabilities").toString().contains("read_savings"));
        assertFalse(frame.toString().contains("credential"));assertEquals(0,frame.path("readSavings").path("attempts").asLong());
        SwarmSavingsWire.attach(frame,p,false,true,2000);assertFalse(frame.has("readSavings"));
        assertTrue(SwarmSavingsWire.negotiated(DashboardProtocol.JSON.createObjectNode().put("readSavingsSchema",1)));
        assertFalse(SwarmSavingsWire.negotiated(DashboardProtocol.JSON.createObjectNode().put("readSavingsSchema",4294967297L)));
        assertFalse(SwarmSavingsWire.negotiated(DashboardProtocol.JSON.createObjectNode().put("readSavingsSchema","1")));
    }
    @Test void invalidOrStaleSnapshotsNeverLeakIntoTelemetry(){
        assertNull(SwarmSavingsWire.snapshot(performance().put("session","not-a-session"),2000));
        assertNull(SwarmSavingsWire.snapshot(performance().put("observedAt",900),2000));
        assertNull(SwarmSavingsWire.snapshot(performance().put("observedAt",90000),2000));
        assertNull(SwarmSavingsWire.snapshot(performance(),40000));
        var p=performance();((com.fasterxml.jackson.databind.node.ObjectNode)p.path("discoverySavings")).put("attempts",99);
        assertNull(SwarmSavingsWire.snapshot(p,2000));
        p=performance();((com.fasterxml.jackson.databind.node.ObjectNode)p.path("discoverySavings")).put("attempts",1_000_000_001L).put("fallbackReads",1_000_000_001L);
        assertNull(SwarmSavingsWire.snapshot(p,2000));
    }
    @Test void advancingClockAloneDoesNotIncreaseTelemetryFrequency(){
        var a=DashboardProtocol.JSON.createObjectNode().put("at",2000);SwarmSavingsWire.attach(a,performance(),true,true,2000);
        var b=a.deepCopy().put("at",3000);((com.fasterxml.jackson.databind.node.ObjectNode)b.get("readSavings")).put("observedAt",3000);
        assertEquals(SwarmSavingsWire.comparison(a),SwarmSavingsWire.comparison(b));
        assertEquals(2000,a.path("readSavings").path("observedAt").asLong());
        ((com.fasterxml.jackson.databind.node.ObjectNode)b.get("readSavings")).put("attempts",1).put("fallbackReads",1);
        assertNotEquals(SwarmSavingsWire.comparison(a),SwarmSavingsWire.comparison(b));
    }
}
