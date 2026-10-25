package dev.doughbay.fabric;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReportContextLineTest {
    @Test void ownBookEvidenceAcceptsOnlyClosedTypedFields() {
        assertTrue(ReportContextLine.valid("ctx own_orders_complete=false own_read_unparsed_count=3",new HashSet<>()));
        assertFalse(ReportContextLine.valid("ctx own_orders_complete=unknown",new HashSet<>()));
        assertFalse(ReportContextLine.valid("ctx own_read_unparsed_count=nativeTooltipText",new HashSet<>()));
        assertFalse(ReportContextLine.valid("ctx own_order_raw_text=anything",new HashSet<>()));
        assertFalse(ReportContextLine.valid("ctx own_read_unparsed_count=-2",new HashSet<>()));
    }
}
