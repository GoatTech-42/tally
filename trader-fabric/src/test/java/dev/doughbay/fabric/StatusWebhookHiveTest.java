package dev.doughbay.fabric;
import dev.doughbay.storage.*;
import dev.doughbay.core.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StatusWebhookHiveTest {
    @Test void nativeJoinedMemberDoesNotDuplicateTheHiveReportWhenLegacyMultiToggleIsOff() {
        assertFalse(StatusWebhook.reportHost(true,true,false,false));
        assertTrue(StatusWebhook.reportHost(true,true,false,true));
        assertFalse(StatusWebhook.reportHost(true,false,true,false));
        assertTrue(StatusWebhook.reportHost(false,true,false,false));
        assertTrue(StatusWebhook.reportHost(true,false,false,false));
    }
    @Test void hourlyBreakdownIncludesThreeOwnersWithoutDoubleCountingOtherModesOrFutureSales() throws Exception {
        long now=200_000_000L;
        try(var db=Database.inMemory()) {
            var repo=new PositionRepository(db);
            repo.setClient("Alpha");repo.upsert(sale(1,"REAL",now-1000,10));
            repo.upsert(sale(2,"REAL",now-7_200_000,20));
            repo.setClient("Bravo");repo.upsert(sale(3,"REAL",now-2000,30));
            repo.upsert(sale(4,"PAPER",now-2000,999));
            repo.upsert(sale(5,"REAL",now+1000,999));
            repo.upsert(sale(6,"REAL",now-90_000_000,999));
            var rows=StatusWebhook.accountProfits(db.connection(),List.of("Charlie","Bravo","Alpha","ALPHA"),now);
            assertEquals(3,rows.size());
            assertEquals(new StatusWebhook.AccountProfit("Alpha",10,30),rows.get(0));
            assertEquals(new StatusWebhook.AccountProfit("Bravo",30,30),rows.get(1));
            assertEquals(new StatusWebhook.AccountProfit("Charlie",0,0),rows.get(2));
            assertEquals(40,rows.stream().mapToLong(StatusWebhook.AccountProfit::hour).sum());
            assertEquals(60,rows.stream().mapToLong(StatusWebhook.AccountProfit::day).sum());
        }
    }
    private Position sale(long id,String mode,long closed,double profit) {
        return new Position(id,mode,"minecraft:stone",StackBucket.of(1),1,100,150,1,2,closed,150,profit,PositionStatus.SOLD);
    }
}
