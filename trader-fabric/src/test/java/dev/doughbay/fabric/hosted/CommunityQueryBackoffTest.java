package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityQueryBackoffTest {
    @Test void retryAfterDefersQueriesUntilDeadlineWithoutShorteningOtherCooldowns() {
        var b=new CommunityQueryBackoff();b.defer(1000,"120");
        assertFalse(b.allowed(120999));assertTrue(b.allowed(121000));
        b.defer(2000,"1");assertEquals(121000,b.until());
        b.defer(100000,"60");assertEquals(160000,b.until());
    }
    @Test void malformedAndHostileHeadersHaveBoundedSafeDeadlines() {
        var malformed=new CommunityQueryBackoff();malformed.defer(1000,"garbage");assertEquals(61000,malformed.until());
        var excessive=new CommunityQueryBackoff();excessive.defer(1000,"999999");assertEquals(3601000,excessive.until());
        var negative=new CommunityQueryBackoff();negative.defer(1000,"-5");assertEquals(2000,negative.until());
        var overflow=new CommunityQueryBackoff();overflow.defer(Long.MAX_VALUE-1,"60");assertEquals(Long.MAX_VALUE,overflow.until());
    }
    @Test void onlyMarketAndSnapshotReadsShareTheQueryBudget() {
        for(String p:new String[]{"market/minecraft%3Adiamond","market?items=minecraft%3Adiamond%7C64&region=net-donut","markets","markets?region=net-donut","orders/last-read","orders/snapshot?page=1"})assertTrue(CommunityQueryBackoff.query("GET",p));
        assertFalse(CommunityQueryBackoff.query("POST","observations"));
        assertFalse(CommunityQueryBackoff.query("GET","me"));
        assertFalse(CommunityQueryBackoff.query("GET","capabilities"));
        assertTrue(CommunityQueryBackoff.query("GET","sales/history?region=net-donut"));
    }
}
