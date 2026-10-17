package dev.doughbay.fabric.automation;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
class BugReportContextTest {
    void set(Object c,String n,Object v)throws Exception{Field f=c.getClass().getDeclaredField(n);f.setAccessible(true);f.set(c,v);}
    @Test void currentPauseAndEvidenceAgesSurviveWithoutCopyingPrivateDetails()throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        set(c,"state",AutomationSessionController.State.PAUSED);
        set(c,"detail","Timed out reconciling the sale notice with active listings account-name secret 123,456");
        set(c,"ownOrdersReadAt",95000L);set(c,"saleNoticeObservedAtMillis",90000L);
        String s=c.bugReportContext(100000);
        assertTrue(s.contains("reason_category=SALE_RECONCILIATION_TIMEOUT"));
        assertTrue(s.contains("own_orders_age_ms=5000"));assertTrue(s.contains("sale_notice_age_ms=10000"));
        assertTrue(s.contains("persistence_state=UNAVAILABLE"));
        assertFalse(s.contains("account-name"));assertFalse(s.contains("secret"));assertFalse(s.contains("123,456"));
        assertEquals(95000L,get(c,"ownOrdersReadAt"),"capture must not refresh evidence");
    }
    Object get(Object c,String n)throws Exception{Field f=c.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(c);}
    @Test void missingAndFutureEvidenceAgesAreUnknown()throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());set(c,"ownOrdersReadAt",200000L);
        assertTrue(c.bugReportContext(100000).contains("own_orders_age_ms=-1"));
        assertTrue(c.bugReportContext(100000).contains("sale_notice_age_ms=-1"));
    }
    @Test void aResumeRefusalHasItsOwnFixedReasonRatherThanGenericPaused() {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        assertFalse(c.resumeAfterEvasion().ok());
        assertTrue(c.bugReportContext(System.currentTimeMillis()).contains("last_resume_gate=NO_RESUMABLE_SESSION"));
    }
}
