package dev.doughbay.fabric.hosted;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class CommunityHistoryBootstrapTest {
 @TempDir Path root;
 static final long NOW=1_800_000_000_000L;
 static final Set<String> REQUESTED=Set.of("minecraft:diamond|64");
 private ObjectNode body(int count){
  var b=DashboardProtocol.JSON.createObjectNode().put("schema",1).put("kind","completed_sales").put("regionScope","net-donut")
      .put("generatedAt",NOW).put("asOf",NOW).put("windowStart",NOW-86_400_000).putNull("next");
  var rows=b.putArray("rows");
  for(int i=0;i<count;i++)rows.addObject().put("id","sale_abcdef"+i).put("itemKey","minecraft:diamond|64").put("quantity",64)
    .put("stack",64).put("total",6400+i*64).put("unitPrice",100+i).put("observedAt",NOW-i*1000)
    .putObject("provenance").put("kind","seller_receipt").put("independentSources",2).put("ownAccount",false);
  return b;
 }
 @Test void exactCompletedReceiptsKeepTimeQuantityPriceAndStableIds(){
  var page=CommunityHistoryBootstrap.decode(body(3),REQUESTED,NOW,0);assertEquals(3,page.sales().size());
  var sale=page.sales().getFirst();assertEquals(64,sale.itemCount());assertEquals(6400,sale.totalPrice());assertEquals(NOW,sale.soldAt());
  assertEquals(page.sales(),CommunityHistoryBootstrap.decode(body(3),REQUESTED,NOW,NOW).sales());
  assertTrue(page.sales().stream().allMatch(s->s.sellerUuid().equals(CommunityHistoryBootstrap.SOURCE)));
 }
 @Test void frozenHistoryParametersRemainIdenticalAcrossPages(){
  var first=CommunityClient.historyPath(REQUESTED,"");
  assertFalse(first.contains("since="));
  assertEquals(first+"&after=c1.snapshot.1000.signature",CommunityClient.historyPath(REQUESTED,"c1.snapshot.1000.signature"));
  var capped=body(1).put("capped",true);
  assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(capped,REQUESTED,NOW,0));
 }
 @Test void transportDelayDoesNotDiscardFreshSalesWhenAFrozenWindowEdgeAgesOut(){
  var b=body(3);
  ((ObjectNode)b.path("rows").get(0)).put("observedAt",NOW-CommunityHistoryBootstrap.WINDOW);
  var page=CommunityHistoryBootstrap.decode(b,REQUESTED,NOW+250,0);
  assertEquals(2,page.sales().size(),"an expired boundary row must not discard the fresh receipts on its page");
  assertEquals(NOW,page.asOf());
  assertEquals(NOW-CommunityHistoryBootstrap.WINDOW,page.windowStart());
  assertTrue(page.sales().stream().allMatch(s->NOW+250-s.soldAt()<=CommunityHistoryBootstrap.WINDOW));
 }
 @Test void expiredBoundaryRowsStillRequireValidReceiptEvidence(){
  var b=body(1);
  ((ObjectNode)b.path("rows").get(0)).put("observedAt",NOW-CommunityHistoryBootstrap.WINDOW).put("unitPrice",999);
  assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(b,REQUESTED,NOW+250,0));
  ((ObjectNode)b.path("rows").get(0)).put("unitPrice",100).put("observedAt",NOW-CommunityHistoryBootstrap.WINDOW-1);
  assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(b,REQUESTED,NOW+250,0));
 }
 @Test void wrongScopeFutureTimeAndOfferProvenanceCannotSeed(){
  var b=body(1);b.put("regionScope","unknown");assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(b,REQUESTED,NOW,0));
  b.put("regionScope","net-donut");((ObjectNode)b.path("rows").get(0)).put("observedAt",NOW+1);
  assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(b,REQUESTED,NOW,0));
  var offer=body(1);((ObjectNode)offer.path("rows").get(0).path("provenance")).put("kind","ask");
  assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(offer,REQUESTED,NOW,0));
 }
 @Test void inconsistentStacksAndSnapshotPaginationAreRejected(){
  var b=body(1);((ObjectNode)b.path("rows").get(0)).put("quantity",1);
  assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(b,REQUESTED,NOW,0));
  var original=body(1);assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(original,REQUESTED,NOW,NOW-1));
  original.remove("next");assertThrows(IllegalArgumentException.class,()->CommunityHistoryBootstrap.decode(original,REQUESTED,NOW,0));
 }
 @Test void ownAndPartsSensitiveRowsNeverBecomeSharedBaseItemSamples(){
  var b=body(3);((ObjectNode)b.path("rows").get(0).path("provenance")).put("ownAccount",true);
  ((ObjectNode)b.path("rows").get(1)).put("itemKey","minecraft:diamond|64#0123456789abcdef");
  ((ObjectNode)b.path("rows").get(2)).put("partsHash","0123456789abcdef");
  assertTrue(CommunityHistoryBootstrap.decode(b,REQUESTED,NOW,0).sales().isEmpty());
 }
 @Test void multiStackReceiptsAreSkippedWithoutDiscardingSupportedSales(){
  var b=body(3);((ObjectNode)b.path("rows").get(0)).put("quantity",128).put("total",12800);
  var page=CommunityHistoryBootstrap.decode(b,REQUESTED,NOW,0);
  assertEquals(2,page.sales().size());assertTrue(page.sales().stream().allMatch(s->s.itemCount()==64));
 }
 @Test void seedWarmsHistoryOnceWithoutTouchingOwnedPositionsOrInventingProfit()throws Exception{
  try(var db=new Database(root.resolve("fresh.db"))){
   var rows=CommunityHistoryBootstrap.decode(body(8),REQUESTED,NOW,0).sales();
   assertTrue(CommunityHistoryBootstrap.needsSeed(db));assertEquals(8,CommunityHistoryBootstrap.seed(db,rows,NOW,8,()->true));
   assertEquals(0,CommunityHistoryBootstrap.seed(db,rows,NOW,8,()->true));assertFalse(CommunityHistoryBootstrap.needsSeed(db));
   try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT COUNT(*),SUM(item_count),SUM(total_price) FROM transactions")){
    assertTrue(r.next());assertEquals(8,r.getInt(1));assertEquals(512,r.getInt(2));assertEquals(8*6400+64*28,r.getInt(3));
   }
   try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM positions")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
  }
 }
 @Test void insufficientStaleOrRevokedSeedFallsBackAndRollsBack()throws Exception{
  try(var db=new Database(root.resolve("blocked.db"))){
   var rows=CommunityHistoryBootstrap.decode(body(8),REQUESTED,NOW,0).sales();
   assertEquals(0,CommunityHistoryBootstrap.seed(db,rows.subList(0,2),NOW,8,()->true));
   assertEquals(0,CommunityHistoryBootstrap.seed(db,rows,NOW+900_001,8,()->true));
   var checks=new AtomicInteger();assertEquals(0,CommunityHistoryBootstrap.seed(db,rows,NOW,8,()->checks.incrementAndGet()==1));
   assertTrue(CommunityHistoryBootstrap.needsSeed(db));
   try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM transactions")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
  }
 }
 @Test void sharedReceiptsProduceNativeMarketValuationWithoutAnyLocalSweep()throws Exception{
  try(var db=new Database(root.resolve("analysis.db"))){
   var service=new dev.doughbay.engine.MarketService(dev.doughbay.core.analysis.AnalyzerConfig.defaults(),
       dev.doughbay.core.analysis.FeeConfig.zero(),dev.doughbay.core.analysis.RiskConfig.defaults(),
       new dev.doughbay.core.analysis.CommodityRegistry(dev.doughbay.core.analysis.CommodityRegistry.DEFAULT_COMMODITIES,Set.of()),null,db);
   assertTrue(service.marketStatsFromHistory(NOW,20).isEmpty());
   var fixture=body(0);var keys=new HashSet<String>();
   var items=List.of("diamond","emerald","gold_ingot","iron_ingot","copper_ingot","coal","lapis_lazuli","quartz");
   for(var item:items){
    String key="minecraft:"+item+"|64";keys.add(key);
    for(var row:body(20).path("rows")){
     var copy=(ObjectNode)row.deepCopy();copy.put("itemKey",key).put("id",copy.path("id").asText()+"_"+item);
     ((com.fasterxml.jackson.databind.node.ArrayNode)fixture.path("rows")).add(copy);
    }
   }
   var rows=CommunityHistoryBootstrap.decode(fixture,keys,NOW,0).sales();
   assertEquals(160,CommunityHistoryBootstrap.seed(db,rows,NOW,20,()->true));
   var markets=service.marketStatsFromHistory(NOW,20);
   assertEquals(8,markets.size());assertTrue(markets.stream().allMatch(m->m.sampleCount()==20&&m.hasPrices()));
  }
 }
}
