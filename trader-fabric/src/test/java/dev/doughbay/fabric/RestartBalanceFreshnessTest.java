package dev.doughbay.fabric;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestartBalanceFreshnessTest {
    @Test void balanceFromBeforeTheRegionalConnectionIsNotProof() throws Exception {
        Field shown=SidebarBalance.class.getDeclaredField("shown"),read=SidebarBalance.class.getDeclaredField("readAt");
        shown.setAccessible(true);read.setAccessible(true);
        long originalShown=shown.getLong(null),originalRead=read.getLong(null),now=System.currentTimeMillis();
        try {
            shown.setLong(null,1000000L);read.setLong(null,now-1000L);
            assertTrue(SidebarBalance.current()>=0,"still fresh under the ordinary 15-second cache");
            assertFalse(SidebarBalance.freshSince(now-500L));
            read.setLong(null,now);
            assertTrue(SidebarBalance.freshSince(now-500L));
            assertFalse(SidebarBalance.freshSince(0));
        } finally {shown.setLong(null,originalShown);read.setLong(null,originalRead);}
    }
}
