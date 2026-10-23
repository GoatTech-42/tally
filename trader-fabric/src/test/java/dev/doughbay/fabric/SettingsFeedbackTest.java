package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class SettingsFeedbackTest {
 @Test void strictHistoryWarningNeedsBothRestrictions(){
  var n=new HashMap<String,Double>(Map.of("orders.enabled",1d,"orders.bid",1d,"orders.bid_min_samples",15d,"orders.bid_on_real_trades",0d));
  assertTrue(ids(n,Map.of()).contains("history-wait"));n.put("orders.bid_on_real_trades",1d);assertFalse(ids(n,Map.of()).contains("history-wait"));
  n.put("orders.bid_on_real_trades",0d);n.put("orders.bid_min_samples",6d);assertFalse(ids(n,Map.of()).contains("history-wait"));
 }
 @Test void focusIsExplainedWithoutPretendingTrackingAllowsBuying(){
  assertTrue(ids(Map.of("buy.focus",1d),Map.of()).contains("empty-focus"));
  var notices=SettingsFeedback.evaluate(Map.of("buy.focus",1d),Map.of("buy.focus_items","=diamond"));
  assertTrue(notices.stream().anyMatch(n->n.id().equals("focus-filter")&&n.effect().contains("Tracking an item alone")));
  assertFalse(ids(Map.of("buy.focus",0d),Map.of()).contains("empty-focus"));
 }
 @Test void previewUsesProposedClampedValueAndNeverSaves()throws Exception{
  String before=SettingsCode.exportPreset(Map.of(),Map.of());
  try{
   Tuning.set("buy.focus",0);Tuning.setText("buy.focus_items","");String saved=SettingsCode.exportPreset(Map.of(),Map.of());
   var preview=new ObjectMapper().readTree(WebPages.data("settingcheck",Map.of("key","buy.focus","value","1")));
   assertTrue(preview.path("ok").asBoolean());assertTrue(preview.path("notices").toString().contains("empty-focus"));
   assertDoesNotThrow(()->SettingsFeedback.preview("buy.focus_items","=diamond"));
   assertEquals(saved,SettingsCode.exportPreset(Map.of(),Map.of()));assertEquals(0,Tuning.get("buy.focus"));
   assertFalse(new ObjectMapper().readTree(WebPages.data("settingcheck",Map.of("key","buy.focus","value","invalid"))).path("ok").asBoolean());
  }finally{SettingsCode.apply(before);}
 }
 @Test void webAndHeadlessExposeSameRulesAndEveryCategory()throws Exception{
  var j=new ObjectMapper().readTree(WebPages.data("settings",Map.of()));
  assertEquals(new ObjectMapper().valueToTree(SettingsFeedback.current()),j.path("notices"));
  assertEquals(SettingsSection.values().length,j.path("sections").size());
  var keys=new HashSet<String>();
  for(var group:j.path("groups"))for(var row:group.path("settings")){assertTrue(keys.add(row.path("key").asText()));assertDoesNotThrow(()->SettingsSection.valueOf(row.path("section").asText()));}
  assertEquals(Tuning.SETTINGS.size(),keys.size());
  for(var row:j.path("lists"))assertDoesNotThrow(()->SettingsSection.valueOf(row.path("section").asText()));
 }
 @Test void categoriesSeparateMoneyItemsSafetyAndDisplay(){
  assertEquals(SettingsSection.CAPITAL,SettingsSection.of("buy.purchase_cap_pct","Buying"));
  assertEquals(SettingsSection.ITEMS,SettingsSection.of("buy.focus_items","Items"));
  assertEquals(SettingsSection.ORDERS,SettingsSection.of("orders.bid_stacks","Orders"));
  assertEquals(SettingsSection.SAFETY,SettingsSection.of("evasion.enabled","Evasion"));
  assertEquals(SettingsSection.DISPLAY,SettingsSection.of("hud.panel","HUD"));
 }
 @Test void ordinaryBatchCeilingDoesNotClaimRivalLotsAreBulk(){
  var ids=ids(Map.of("orders.enabled",1d,"orders.bid",1d,"orders.bid_stacks",16d,"orders.depth_share_pct",5d,"underdog.enabled",1d),Map.of());
  assertTrue(ids.containsAll(Set.of("exact-count","depth-sizing","rival-lots")));
 }
 @Test void scanningOffDoesNotFalselyClaimBiddingIsDisabled(){
  var notices=SettingsFeedback.evaluate(Map.of("orders.enabled",0d,"orders.bid",1d,"orders.collect",0d),Map.of());
  assertTrue(notices.stream().filter(n->n.id().equals("orders-off")).allMatch(n->n.effect().contains("can still manage and place orders")));
  assertTrue(notices.stream().anyMatch(n->n.id().equals("collection-off")&&n.effect().contains("still collect")));
 }
 @Test void confidenceIsNotLabeledAsSalesPerHour(){
  assertEquals("0.2",Tuning.setting("orders.bid_min_confidence").format(.2));
  assertEquals("0.25/h",Tuning.setting("orders.bid_min_sales_per_hour").format(.25));
 }
 private Set<String> ids(Map<String,Double> n,Map<String,String> t){return new HashSet<>(SettingsFeedback.evaluate(n,t).stream().map(SettingsFeedback.Notice::id).toList());}
}
