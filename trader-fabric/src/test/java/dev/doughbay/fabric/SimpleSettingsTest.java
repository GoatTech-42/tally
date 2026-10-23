package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SimpleSettingsTest {
 private Map<String,Double> values(){var m=new HashMap<String,Double>();for(var s:Tuning.SETTINGS)m.put(s.key(),s.defaultValue());return m;}
 @Test void customViewAndPreviewPreserveEveryExistingValue(){var n=values();n.put("pace.fast",1d);n.put("pace.human_steps",1d);n.put("buy.purchase_cap_pct",17d);var before=Map.copyOf(n);assertEquals("",SimpleSettings.current(SimpleSettings.group("pace"),n));var p=SimpleSettings.preview("pace","fast",n,Map.of());assertEquals(before,n);assertEquals(List.of("pace.human_steps"),p.changes().stream().map(SimpleSettings.Change::key).toList());assertEquals(17d,n.get("buy.purchase_cap_pct"));}
 @Test void bundlesNeverContainSafetyOrExposureControls(){for(var g:SimpleSettings.GROUPS)for(var o:g.options())for(var key:o.values().keySet()){assertFalse(key.startsWith("evasion."));assertFalse(key.startsWith("recovery."));assertFalse(key.startsWith("buy."));assertFalse(key.startsWith("slots."));assertNotNull(Tuning.setting(key));}}
 @Test void staleGroupSnapshotChangesWhenAnotherClientChangesARelevantValue(){var n=values();var g=SimpleSettings.group("orders");String before=SimpleSettings.fingerprint(g,n);n.put("orders.collect",1-n.get("orders.collect"));assertNotEquals(before,SimpleSettings.fingerprint(g,n));}
 @Test void previewIncludesActualConsequencesAndKeepsSourceMapUnchanged(){var n=values();n.put("buy.focus",1d);var before=Map.copyOf(n);var p=SimpleSettings.preview("batch","16",n,Map.of("buy.focus_items",""));assertEquals(before,n);assertTrue(p.notices().stream().anyMatch(x->x.id().equals("empty-focus")));assertTrue(p.notices().stream().anyMatch(x->x.id().equals("exact-count"))||n.get("orders.bid")==0);}
}
