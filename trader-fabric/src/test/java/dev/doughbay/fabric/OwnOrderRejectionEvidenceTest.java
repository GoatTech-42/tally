package dev.doughbay.fabric;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnOrderRejectionEvidenceTest {
    @Test void fixedWordsNeverExportUnknownTextOrAmounts() {
        var e=new OwnOrderRejectionEvidence();e.add("minecraft:barrier","Max Orders private-player",List.of("Buy Donut++ for more slots private-home secret123 $98765"),false);e.capturedAt(1234);
        String s=e.contextLine();assertFalse(s.contains("private"));assertFalse(s.contains("secret"));assertFalse(s.contains("98765"));
        assertTrue(s.contains("own_read_reject_item_mask=1"));assertTrue(s.contains("own_read_reject_shapes=1"));
        assertTrue(ReportContextLine.valid(s,new HashSet<>()));
        assertEquals(3,OwnOrderRejectionEvidence.words("Max Orders private-player"));
        assertEquals(0,OwnOrderRejectionEvidence.words("PrivatePerson private-home secret123 98765"));
    }
    @Test void twentySevenIdenticalTilesHaveOneShapeWithoutBeingClassifiedAsOrders() {
        var e=new OwnOrderRejectionEvidence();for(int i=0;i<27;i++)e.add("minecraft:barrier","Unknown tile",List.of(),false);
        assertTrue(e.contextLine().contains("own_read_reject_shapes=1"));
        assertTrue(e.contextLine().contains("own_read_reject_parse_mask=2"));
    }
    @Test void formatAndUnsupportedQuantityAreSeparateStructuralFlags() {
        var s=OwnOrderRejectionEvidence.shape("minecraft:diamond","§aItem",List.of("1.2K requested","$100 each","64/1200 delivered"),true);
        assertEquals(16,s.itemMask());assertEquals(4|8|16|32|64|128|256|1024,s.parseMask());
        assertEquals(512,OwnOrderRejectionEvidence.shape("minecraft:paper","New\u00a0order",List.of("other"),false).parseMask());
    }
    @Test void fixedRankPunctuationIsDistinctWithoutExportingTheLabel() {
        assertEquals(2048,OwnOrderRejectionEvidence.shape("minecraft:barrier","Buy Donut+",List.of("more slots"),false).parseMask());
        assertEquals(4096,OwnOrderRejectionEvidence.shape("minecraft:barrier","Buy Donut++",List.of("more slots"),false).parseMask());
    }
    @Test void newPageStartsWithEmptyEvidenceAndDistinctShapesAreBounded() {
        var e=new OwnOrderRejectionEvidence();for(int i=0;i<100;i++)e.add("minecraft:paper","",List.of(OwnOrderRejectionEvidence.WORDS.get(i%30),OwnOrderRejectionEvidence.WORDS.get(i/30)),false);
        int count=Integer.parseInt(e.contextLine().split("own_read_reject_shapes=")[1].split(" ")[0]);assertTrue(count<=45);
        assertTrue(new OwnOrderRejectionEvidence().contextLine().contains("own_read_reject_name_mask=0 own_read_reject_lore_mask=0 own_read_reject_item_mask=0 own_read_reject_parse_mask=0 own_read_reject_shapes=0"));
    }
    @Test void diagnosticContextDoesNotAcceptArbitraryNativeText() {
        for(String k:List.of("name_mask","lore_mask","item_mask","parse_mask","shapes","at_ms")) {
            assertTrue(ReportContextLine.valid("ctx own_read_reject_"+k+"=27",new HashSet<>()));
            assertFalse(ReportContextLine.valid("ctx own_read_reject_"+k+"=private-player",new HashSet<>()));
        }
        assertFalse(ReportContextLine.valid("ctx own_read_reject_name=MaxOrders",new HashSet<>()));
    }
}
