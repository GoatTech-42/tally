package dev.doughbay.fabric;

import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

class FlipsOnlyModeTest {
    @TempDir Path root;
    double previous;String list;
    @BeforeEach void setup() throws Exception {
        previous=Tuning.get("prices.only");list=Tuning.text(OwnPrices.KEY);
        Path local=root.resolve("local.json");Files.writeString(local,"{\"prices.only\":1,\"prices.own\":\"\"}");
        Tuning.loadLocalFrom(local);
    }
    @AfterEach void restore() { Tuning.set("prices.only",previous);Tuning.setText(OwnPrices.KEY,list);Tuning.loadLocalFrom(null); }
    @Test void emptyListNeverFallsBackToAutomaticTrading() throws Exception {
        assertTrue(OwnPrices.all().isEmpty());
        Method gate=AutomationSessionController.class.getDeclaredMethod("ownOnly");gate.setAccessible(true);
        assertEquals(true,gate.invoke(null));assertTrue(FlipsMode.description().contains("add an item"));
    }
    @Test void generalOrderScanAlreadyRunningIsStoppedAfterTheToggle() throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        Field state=AutomationSessionController.class.getDeclaredField("state");state.setAccessible(true);
        state.set(c,AutomationSessionController.State.READING_ORDERS);
        c.tick(null,null);
        assertEquals(AutomationSessionController.State.SCANNING,c.snapshot().state());
        assertTrue(c.snapshot().detail().contains("General market scans are off"));
    }
    @Test void emptyListStillSuppressesBackgroundAuctionPriceSweep() throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        Method sweep=AutomationSessionController.class.getDeclaredMethod("nextKeylessSweep",MarketWatcher.Snapshot.class,long.class);
        sweep.setAccessible(true);assertNull(sweep.invoke(c,null,Long.MAX_VALUE/2));
    }
    @Test void normalModeRemainsExplicitlyAvailable() throws Exception {
        Tuning.set("prices.only",0);
        Method gate=AutomationSessionController.class.getDeclaredMethod("ownOnly");gate.setAccessible(true);
        assertEquals(false,gate.invoke(null));assertTrue(FlipsMode.description().contains("alongside normal trading"));
    }
}
