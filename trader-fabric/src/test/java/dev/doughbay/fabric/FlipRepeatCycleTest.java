package dev.doughbay.fabric;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class FlipRepeatCycleTest {
    @TempDir Path root;
    static final String ITEM="minecraft:stone";
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @BeforeEach void configure() throws Exception {
        Path file=root.resolve("local.json");
        Files.writeString(file,"{\"prices.only\":1,\"prices.own\":\"stone top+100 1000 2000 x1\",\"hive.split_markets\":0,\"buy.scale_with_bank\":0,\"orders.bid\":1,\"orders.bid_slots\":8}");
        Tuning.loadLocalFrom(file);
    }
    @AfterEach void restore() { Tuning.loadLocalFrom(null); }
    static void set(Object c,String n,Object v)throws Exception { Field f=c.getClass().getDeclaredField(n);f.setAccessible(true);f.set(c,v); }
    static Object get(Object c,String n)throws Exception { Field f=c.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(c); }
    static void invoke(Object c,String name,Class<?>[] types,Object... args)throws Exception { Method m=c.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(c,args); }
    static MarketWatcher.Snapshot snapshot(long now) {
        return new MarketWatcher.Snapshot(List.of(),List.of(),List.of(),List.of(),Map.of(),Map.of(),null,"",1000000,
                "KEYLESS: Flips only",null,now,now,false);
    }
    AutomationSessionController controller(long now)throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(1000,1000000,100000,1,1,80,1,8,120,1));
        set(c,"lastSnapshot",snapshot(now));set(c,"ownOrdersReadAt",now-1000);
        ((Map<String,long[]>)get(c,"orderQuotes")).put(ITEM,new long[]{100,now});
        return c;
    }
    List<String> plan(AutomationSessionController c,long now)throws Exception {
        invoke(c,"planDesk",new Class[]{MarketWatcher.Snapshot.class,long.class},snapshot(now),now);
        return ((Collection<?>)get(c,"deskQueue")).stream().map(Object::toString).toList();
    }
    @Test void freshQuotePlansTheFirstOrderAndAnotherAfterTheCollectedOrderIsGone()throws Exception {
        long now=1000000;var c=controller(now);
        assertTrue(plan(c,now).stream().anyMatch(s->s.contains("action=PLACE")));
        // A completed, collected order may remain visible; it must not prohibit replenishment.
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,64,100000)));
        ((Map<String,Integer>)get(c,"collectedByKey")).put(ITEM+"|200|64",64);
        assertTrue(plan(c,now+1).stream().anyMatch(s->s.contains("action=PLACE")));
    }
    @Test void cancelledOrderWithNothingOwedDoesNotBlockItsReplacement()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,100,0,100000,true)));
        assertTrue(plan(c,now).stream().anyMatch(s->s.contains("action=PLACE")),
                "a cancelled row cannot fill again and must not reserve the market forever");
    }
    @Test void collectedCancelledRowDoesNotQueueAnotherCollectAndAllowsReplenishment()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,100,32,100000,true)));
        ((Map<String,Integer>)get(c,"collectedByKey")).put(ITEM+"|100|64",32);
        var steps=plan(c,now);
        assertTrue(steps.stream().anyMatch(s->s.contains("action=PLACE")));
        assertFalse(steps.stream().anyMatch(s->s.contains("action=COLLECT")));
        assertEquals(0L,amountReserved(c));
    }
    @Test void cancelledGoodsStillOwedAreCollectedBeforeAnotherOrderIsPlanned()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,100,16,100000,true)));
        var steps=plan(c,now);
        assertEquals(1,steps.stream().filter(s->s.contains("action=COLLECT")).count());
        assertTrue(steps.stream().anyMatch(s->s.contains("count=64")),"collection accounting keeps the original order key");
        assertFalse(steps.stream().anyMatch(s->s.contains("action=PLACE")));
    }
    @Test void activeOrderStillReservesItsMarketAndUnfilledMoney()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,100,16,100000)));
        assertFalse(plan(c,now).stream().anyMatch(s->s.contains("action=PLACE")));
        assertEquals(4800L,amountReserved(c));
    }
    @Test void cancelledRowsDoNotConsumeAllTheBidSlots()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",Collections.nCopies(8,new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,100,0,100000,true)));
        assertTrue(plan(c,now).stream().anyMatch(s->s.contains("action=PLACE")));
    }
    @Test void cancelledCollectionFailuresRespectRetryLimitAndKeepUncollectedStockReserved()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,100,16,100000,true)));
        ((Map<String,Integer>)get(c,"collectAttempts")).put(ITEM+"|100|64",4);
        var steps=plan(c,now);
        assertFalse(steps.stream().anyMatch(s->s.contains("action=COLLECT")));
        assertFalse(steps.stream().anyMatch(s->s.contains("action=PLACE")));
        assertEquals(16,((List<AutomatedExecutionDriver.OwnOrderRow>)get(c,"ownOrders")).getFirst().delivered());
        assertTrue(((Map<?,?>)get(c,"collectedByKey")).isEmpty(),"failure never pretends the goods were collected");
    }
    long amountReserved(AutomationSessionController c)throws Exception {
        Method m=c.getClass().getDeclaredMethod("bidsOut");m.setAccessible(true);return (long)m.invoke(c);
    }
    void completeNext(AutomationSessionController c, AutomatedExecutionDriver.OperationIntent intent,
                      String item,long price,long sequence,long now)throws Exception {
        Object next=((Deque<?>)get(c,"deskQueue")).removeFirst();set(c,"deskStep",next);
        var driver=(AutomatedExecutionDriver)get(c,"driver");
        set(driver,"lastTerminalEvent",new AutomatedExecutionDriver.TerminalEvent(sequence,intent,
                AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,"","",item,64,price,"",false,now,"fixture"));
        invoke(c,"observeDeskTerminal",new Class[]{net.minecraft.client.Minecraft.class,long.class},null,now);
    }
    @Test void quoteForAnotherFlipCannotRecreateAnOrderJustPlacedAfterTheOwnBookRead()throws Exception {
        Path file=root.resolve("two-flips.json");
        Files.writeString(file,"{\"prices.only\":1,\"prices.own\":\"stone top+100 1000 2000 x1, diamond top+100 1000 2000 x1\",\"hive.split_markets\":0,\"buy.scale_with_bank\":0,\"orders.bid\":1,\"orders.bid_slots\":8}");
        Tuning.loadLocalFrom(file);
        long now=1000000;var c=controller(now);
        var steps=plan(c,now);
        assertTrue(steps.getFirst().contains("action=PLACE")&&steps.getFirst().contains(ITEM));
        assertTrue(steps.get(1).contains("action=QUOTE")&&steps.get(1).contains("minecraft:diamond"));
        // PLACE succeeds only after the driver has reread Your Orders and
        // verified the new row there. Simulate that native confirmation page.
        set(get(c,"driver"),"lastPlacementOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,0,100000)));
        completeNext(c,AutomatedExecutionDriver.OperationIntent.PLACE_ORDER,ITEM,200,1,now+1);
        completeNext(c,AutomatedExecutionDriver.OperationIntent.ORDER_QUOTE,"minecraft:diamond",100,2,now+2);
        var replanned=((Collection<?>)get(c,"deskQueue")).stream().map(Object::toString).toList();
        assertFalse(replanned.stream().anyMatch(s->s.contains("action=PLACE")&&s.contains(ITEM)),
                "the native read predates our successful placement; it cannot prove that market is still empty");
        assertTrue(replanned.stream().anyMatch(s->s.contains("action=PLACE")&&s.contains("minecraft:diamond")));
        assertEquals(12800L,amountReserved(c));
        assertEquals(now+1,get(c,"ownOrdersReadAt"));
    }
    @Test void receiptOnlyPlacementCannotRefreshTheOldBookOrReplanDuplicateBids()throws Exception {
        long now=1000000;var c=controller(now);plan(c,now);
        // The native cache could be an old page even though an exact chat receipt proves placement.
        set(get(c,"driver"),"lastOwnOrders",List.of());
        completeNext(c,AutomatedExecutionDriver.OperationIntent.PLACE_ORDER,ITEM,200,1,now+1);
        assertEquals(0L,get(c,"ownOrdersReadAt"));
        assertEquals(true,get(c,"deskReadDue"));
        invoke(c,"replanQuotedOrders",new Class[]{long.class},now+2);
        assertTrue(((Collection<?>)get(c,"deskQueue")).isEmpty());
        assertEquals(true,get(c,"deskReadDue"));
    }
    @Test void historicalCompletedOrCancelledRowsCannotCertifyAnActivePlacementBook() {
        var completed=new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,64,100000);
        var cancelled=new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,0,100000,true);
        assertNull(AutomatedExecutionDriver.activePlacementBook(List.of(completed,cancelled),ITEM,64,200));
        var active=new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,8,100000);
        assertNull(AutomatedExecutionDriver.activePlacementBook(List.of(active),ITEM,32,200));
        assertNull(AutomatedExecutionDriver.activePlacementBook(List.of(active),ITEM,64,201));
        var verified=AutomatedExecutionDriver.activePlacementBook(List.of(completed,active),ITEM,64,200);
        assertEquals(List.of(completed,active),verified);
        assertThrows(UnsupportedOperationException.class,()->verified.add(cancelled));
    }
    @Test void aCollectionRetryDoesNotSkipPastTheOnlyMatchingOrderRow()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,64,100000)));
        ((Map<String,Integer>)get(c,"collectAttempts")).put(ITEM+"|200|64",1);
        var steps=plan(c,now);
        assertTrue(steps.stream().anyMatch(s->s.contains("action=COLLECT")&&s.contains("ordinal=0")),
                "one failed attempt must not mean the next try starts at nonexistent row 2");
    }
    @Test void confirmedCollectionClearsOldFailuresForTheNextLotOfTheSameFlip()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",Collections.nCopies(4,new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,64,100000)));
        ((Map<String,Integer>)get(c,"collectedByKey")).put(ITEM+"|200|64",192);
        ((Map<String,Integer>)get(c,"collectAttempts")).put(ITEM+"|200|64",3);
        plan(c,now);
        // Physical resale is outside this queue/accounting fixture.
        ((Set<String>)get(c,"setAsideItems")).add(ITEM);
        completeNext(c,AutomatedExecutionDriver.OperationIntent.COLLECT_ORDER,ITEM,200,1,now+1);
        assertEquals(0,((Map<String,Integer>)get(c,"collectAttempts")).getOrDefault(ITEM+"|200|64",0));
        assertEquals(256,((Map<String,Integer>)get(c,"collectedByKey")).get(ITEM+"|200|64"));
    }
    @Test void skippingAnAlreadyCollectedHistoricalRowDoesNotSpendTheFailureBudget()throws Exception {
        long now=1000000;var c=controller(now);
        set(c,"ownOrders",Collections.nCopies(2,new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,64,100000)));
        ((Map<String,Integer>)get(c,"collectedByKey")).put(ITEM+"|200|64",64);
        plan(c,now);
        failNextEmpty(c,1,now+1);
        assertEquals(0,((Map<String,Integer>)get(c,"collectAttempts")).getOrDefault(ITEM+"|200|64",0));
        assertTrue(((Collection<?>)get(c,"deskQueue")).stream().anyMatch(s->s.toString().contains("ordinal=1")));
    }
    void failNextEmpty(AutomationSessionController c,long sequence,long now)throws Exception {
        Object next=((Deque<?>)get(c,"deskQueue")).removeFirst();set(c,"deskStep",next);
        // Terminal identities increase even when several old rows are skipped.
        set(get(c,"driver"),"lastTerminalEvent",new AutomatedExecutionDriver.TerminalEvent(sequence,
                AutomatedExecutionDriver.OperationIntent.COLLECT_ORDER,AutomatedExecutionDriver.TerminalOutcome.ABORTED,
                "","",ITEM,0,0,"",false,now,"Nothing arrived from the order"));
        invoke(c,"observeDeskTerminal",new Class[]{net.minecraft.client.Minecraft.class,long.class},null,now);
    }
    @Test void sixSuccessiveLotsCanSkipAllHistoricalRowsWithoutPermanentlyExhaustingRetries()throws Exception {
        long now=1000000,sequence=0;var c=controller(now);
        ((Set<String>)get(c,"setAsideItems")).add(ITEM);
        for(int lot=0;lot<6;lot++) {
            set(c,"ownOrders",Collections.nCopies(lot+1,new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,64,200,64,100000)));
            assertTrue(plan(c,now).stream().anyMatch(s->s.contains("action=COLLECT")),"lot "+lot);
            for(int old=0;old<lot;old++)failNextEmpty(c,++sequence,++now);
            completeNext(c,AutomatedExecutionDriver.OperationIntent.COLLECT_ORDER,ITEM,200,++sequence,++now);
            assertEquals((lot+1)*64,((Map<String,Integer>)get(c,"collectedByKey")).get(ITEM+"|200|64"));
            assertEquals(0,((Map<String,Integer>)get(c,"collectAttempts")).getOrDefault(ITEM+"|200|64",0));
        }
    }
}
