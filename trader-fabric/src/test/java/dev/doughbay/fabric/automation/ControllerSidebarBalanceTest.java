package dev.doughbay.fabric.automation;
import dev.doughbay.fabric.SidebarBalance;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControllerSidebarBalanceTest {
    @Test void controllerUsesTheSameFreshScoreboardBalanceAsDiscordWithoutAnApiBalance() throws Exception {
        Field shown=SidebarBalance.class.getDeclaredField("shown"),band=SidebarBalance.class.getDeclaredField("band"),at=SidebarBalance.class.getDeclaredField("readAt");
        shown.setAccessible(true);band.setAccessible(true);at.setAccessible(true);
        long oldShown=shown.getLong(null),oldBand=band.getLong(null),oldAt=at.getLong(null);
        try {
            shown.setLong(null,1000000);band.setLong(null,1000);at.setLong(null,System.currentTimeMillis());
            assertEquals(SidebarBalance.best(-1),AutomationSessionController.observedTradingBalance(-1,-1,false));
            assertEquals(1000000,AutomationSessionController.observedTradingBalance(-1,-1,false));
            assertEquals(1000042,AutomationSessionController.observedTradingBalance(-1,1000042,false));
            assertEquals(1000000,AutomationSessionController.observedTradingBalance(-1,999999999,false));
            assertEquals(1000000,AutomationSessionController.observedTradingBalance(-1,999999999,true),"real scoreboard wins over demo feed");
            assertEquals(140000,AutomationSessionController.capFor(AutomationSessionController.observedTradingBalance(-1,-1,false),14,5000000,1000,true));
            at.setLong(null,System.currentTimeMillis()-20000);
            assertEquals(2000000,AutomationSessionController.observedTradingBalance(-1,2000000,false));
            assertEquals(-1,AutomationSessionController.observedTradingBalance(-1,999999999,true));
            assertEquals(12345,AutomationSessionController.observedTradingBalance(12345,-1,false));
        } finally { shown.setLong(null,oldShown);band.setLong(null,oldBand);at.setLong(null,oldAt); }
    }
}
