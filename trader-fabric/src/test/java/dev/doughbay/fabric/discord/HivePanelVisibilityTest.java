package dev.doughbay.fabric.discord;

import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HivePanelVisibilityTest {
    @TempDir Path root;
    @Test void realPanelPickerOffersHiveBeforeAnySecondAccountIsRecognized() throws Exception {
        var panel=DiscordBridge.panelOnly(LoggerFactory.getLogger("test"),root,root.resolve("ledger.db"),
                new AutomationSessionController(new AutomatedExecutionDriver()));
        var method=DiscordBridge.class.getDeclaredMethod("components");method.setAccessible(true);
        var components=(com.fasterxml.jackson.databind.JsonNode)method.invoke(panel);
        boolean found=false;
        for(var row:components)for(var control:row.path("components"))
            if("page:pick".equals(control.path("custom_id").asText()))
                for(var option:control.path("options"))if("HIVE".equals(option.path("value").asText()))found=true;
        assertTrue(found,"The Hive page must remain discoverable before accounts have been combined");
    }
}
