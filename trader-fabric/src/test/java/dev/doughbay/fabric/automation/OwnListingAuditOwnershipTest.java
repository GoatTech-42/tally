package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.storage.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class OwnListingAuditOwnershipTest {
    @Test void missingSaleKeepsOriginalPriceAndCannotStealAnotherExactOwnerRow() throws Exception {
        for (boolean reverse : new boolean[]{false,true}) {
            var missing=position(41,"minecraft:beacon",64,19_530_000);
            var owner=position(42,"minecraft:beacon",64,20_200_000);
            var f=new Fixture(reverse?List.of(owner,missing):List.of(missing,owner),
                    List.of(row("minecraft:beacon",64,20_200_000)),19_799_999);
            f.audit();
            assertEquals(List.of(owner), f.open(), "Only the native row's exact owner remains listed");
            var sale=f.persisted(41);
            assertEquals(PositionStatus.SOLD,sale.status());
            assertEquals(19_530_000,sale.salePrice());
            assertEquals(19_530_000-10_000,sale.realizedProfit());
            assertFalse(f.p.checkpoints.stream().anyMatch(c->c.position()!=null&&c.position().positionId()!=41),
                    "No replacement id, owner write-off or invented second position");
        }
    }

    @Test void differentSizeNativeRowAlsoRemainsWithItsExactOwner() throws Exception {
        var missing=position(41,"minecraft:beacon",64,19_530_000);
        var owner=position(42,"minecraft:beacon",32,20_200_000);
        var f=new Fixture(List.of(missing,owner),List.of(row("minecraft:beacon",32,20_200_000)),19_799_999);
        f.audit();assertEquals(List.of(owner),f.open());assertEquals(PositionStatus.SOLD,f.persisted(41).status());
    }

    @Test void absentEarningsNeverTurnAMissingPositionIntoProfit() throws Exception {
        var missing=position(41,"minecraft:beacon",64,19_530_000);
        var owner=position(42,"minecraft:beacon",64,20_200_000);
        var f=new Fixture(List.of(missing,owner),List.of(row("minecraft:beacon",64,20_200_000)),0);
        f.audit();assertEquals(List.of(owner),f.open());
        assertEquals(PositionStatus.CANCELLED,f.persisted(41).status());
        assertTrue(Double.isNaN(f.persisted(41).realizedProfit()));
        assertFalse(f.p.checkpoints.stream().anyMatch(c->c.position()!=null&&c.position().status()==PositionStatus.SOLD));
    }

    @Test void unownedBoundedQuantityCorrectionStillUsesNativeSizeAndPrice() throws Exception {
        var old=position(41,"minecraft:beacon",64,19_530_000);
        var f=new Fixture(List.of(old),List.of(row("minecraft:beacon",32,20_200_000)),0);
        f.audit();assertEquals(1,f.open().size());var resized=f.open().getFirst();
        assertNotEquals(old.positionId(),resized.positionId());assertEquals(32,resized.quantity());
        assertEquals(20_200_000,resized.targetPrice());assertEquals(old.purchasePrice(),resized.purchasePrice());
        assertEquals(PositionStatus.CANCELLED,f.persisted(41).status());
    }

    @Test void sameCountUnownedManualRepriceRetainsPositionIdentity() throws Exception {
        var old=position(41,"minecraft:beacon",64,19_530_000);
        var f=new Fixture(List.of(old),List.of(row("minecraft:beacon",64,20_200_000)),0);
        f.audit();assertEquals(1,f.open().size());assertEquals(41,f.open().getFirst().positionId());
        assertEquals(20_200_000,f.open().getFirst().targetPrice());
    }

    @Test void resizeCannotCrossABoxContentsIdentity() throws Exception {
        for(String recorded:List.of("minecraft:shulker_box","minecraft:shulker_box#original")){
            var old=position(41,recorded,2,100);
            var f=new Fixture(List.of(old),List.of(new AutomatedExecutionDriver.OwnListingRow(
                    "minecraft:shulker_box","minecraft:shulker_box#different",1,100,100)),0);
            f.audit();assertTrue(f.open().isEmpty());assertEquals(PositionStatus.CANCELLED,f.persisted(41).status());
            assertFalse(f.p.checkpoints.stream().anyMatch(c->c.position()!=null&&c.position().status()==PositionStatus.LISTED));
        }
    }

    static Position position(long id,String item,int quantity,long price){return new Position(id,"REAL",item,
            StackBucket.of(quantity),quantity,10_000,price,1_000,2_000,0,0,Double.NaN,PositionStatus.LISTED);}
    static AutomatedExecutionDriver.OwnListingRow row(String item,int count,long price){return new AutomatedExecutionDriver.OwnListingRow(item,item,count,price,price);}
    static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static class Fixture {
        final AutomatedExecutionDriver driver=new AutomatedExecutionDriver();
        final AutomationSessionController c=new AutomationSessionController(driver);
        final Persistence p=new Persistence();
        Fixture(List<Position> positions,List<AutomatedExecutionDriver.OwnListingRow> rows,long earnings)throws Exception{
            c.setPersistence(p);c.tick(null,null);
            set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(2,100_000,50_000,5_000,12,80,10,8,120,1));
            set(c,"sessionOpen",true);set(c,"state",AutomationSessionController.State.AUDITING_SLOTS);
            set(c,"committedSpend",positions.stream().mapToLong(Position::purchasePrice).sum());
            open().addAll(positions);set(driver,"lastOwnListings",rows);
            ((AtomicLong)get(c,"unnamedEarningsSinceAudit")).set(earnings);
            set(driver,"lastTerminalEvent",new AutomatedExecutionDriver.TerminalEvent(1,
                    AutomatedExecutionDriver.OperationIntent.AUDIT,AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,
                    "","","",0,0,"",false,100_000,"Native audit complete"));
        }
        @SuppressWarnings("unchecked") List<Position> open()throws Exception{return (List<Position>)get(c,"openListings");}
        void audit()throws Exception{Method m=c.getClass().getDeclaredMethod("observeSlotAuditTerminal",net.minecraft.client.Minecraft.class,long.class);m.setAccessible(true);m.invoke(c,null,100_000L);}
        Position persisted(long id){return p.checkpoints.stream().map(AutomationSessionCheckpoint::position).filter(Objects::nonNull).filter(x->x.positionId()==id).reduce((a,b)->b).orElseThrow();}
    }
    static class Persistence implements AutomationPersistencePort {
        final List<AutomationSessionCheckpoint> checkpoints=new ArrayList<>();
        public void start(){}
        public long submit(AutomationSessionCheckpoint c){checkpoints.add(c);return checkpoints.size();}
        public long submitManualResolution(AutomationManualResolution r,AutomationSessionCheckpoint c){throw new AssertionError("Manual resolution must not run");}
        public Status status(){return new Status(LoadState.READY,AutomationSessionRecovery.empty(),checkpoints.size(),checkpoints.size(),"Fixture ready");}
        public void close(){}
    }
}
