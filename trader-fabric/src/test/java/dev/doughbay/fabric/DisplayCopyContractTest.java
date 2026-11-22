package dev.doughbay.fabric;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Casing contracts between display-copy producers and the code that reads their
 * strings back (matchers, HUD parsers, panel label lists, switch operands, log
 * parsers). Lowercase display passes must not change any string another path reads.
 * These guards read the sources so a casing edit on either side fails loudly.
 */
class DisplayCopyContractTest {

    private static String src(String relative) throws Exception {
        Path p = Path.of("src/main/java", relative);
        assertTrue(Files.exists(p), "missing source " + p);
        return Files.readString(p);
    }

    @Test
    void bidDialogRefusalKeepsThePrefixTheSessionControllerMatches() throws Exception {
        String driver = src("dev/doughbay/fabric/AutomatedExecutionDriver.java");
        String controller = src("dev/doughbay/fabric/automation/AutomationSessionController.java");
        assertTrue(driver.contains("\"An unexpected dialog opened while placing a bid: \""),
                "producer prefix changed");
        assertTrue(controller.contains("detail.contains(\"An unexpected dialog opened while placing a bid:\")"),
                "consumer matcher changed");
    }

    @Test
    void stopResponsesKeepTheCapitalTheHostedDashboardAccepts() throws Exception {
        String hive = src("dev/doughbay/fabric/HiveControl.java");
        String hosted = src("dev/doughbay/fabric/hosted/HostedDashboardClient.java");
        assertTrue(hive.contains("return \"Stopped by operator\""), "HiveControl stop response changed");
        assertTrue(hosted.contains("answer.startsWith(\"Stopped\")"), "hosted dashboard acceptance changed");
    }

    @Test
    void discordMoneyKeysMatchThePanelReadModelLabels() throws Exception {
        String bridge = src("dev/doughbay/fabric/discord/DiscordBridge.java");
        String model = src("dev/doughbay/fabric/discord/PanelReadModel.java");
        for (String key : new String[] {"Auction data", "Tracked listings", "Revenue 24h",
                "Payments (Hive)", "Payroll share", "Tracked cost", "Asking"}) {
            assertTrue(bridge.contains("\"" + key + "\""), "DiscordBridge missing key " + key);
            assertTrue(model.contains("\"" + key + "\""), "PanelReadModel missing label " + key);
        }
    }

    @Test
    void moneyPageTitleMatchesTheStatsImageSwitches() throws Exception {
        String bridge = src("dev/doughbay/fabric/discord/DiscordBridge.java");
        String image = src("dev/doughbay/fabric/discord/StatsImage.java");
        assertTrue(bridge.contains("new StatsImage.Page(\"Money\""), "money page title changed");
        assertTrue(image.contains("page.title().equals(\"Money\")"), "StatsImage title switch changed");
        assertTrue(image.contains("case \"Bank\",\"Profit 1h\",\"Profit 24h\""),
                "moneyHeadlineSpans operands changed");
    }

    @Test
    void staffAlarmOperandsMatchTheStaffListModuleMode() throws Exception {
        String alarm = src("dev/doughbay/fabric/staffwatch/util/staff/StaffAlarm.java");
        String list = src("dev/doughbay/fabric/staffwatch/module/impl/misc/StaffListModule.java");
        assertTrue(alarm.contains("case \"Guardian\""), "StaffAlarm sound operand changed");
        assertTrue(alarm.contains("case \"Beep\""), "StaffAlarm beep operand changed");
        assertTrue(list.contains("\"Guardian\""), "StaffListModule mode string changed");
    }

    @Test
    void analyzerPrefixesMatchThePanelReader() throws Exception {
        String analyzer = src("dev/doughbay/fabric/SettingsAnalyzer.java");
        String panel = src("dev/doughbay/fabric/AnalyzerPanel.java");
        for (String prefix : new String[] {"This account bank", "This account occupied", "Explicit tracked"}) {
            assertTrue(analyzer.contains("\"" + prefix), "SettingsAnalyzer prefix changed: " + prefix);
            assertTrue(panel.contains("\"" + prefix), "AnalyzerPanel reader changed: " + prefix);
        }
    }

    @Test
    void sessionStatusDetailsKeepThePrefixesTheHudParses() throws Exception {
        String controller = src("dev/doughbay/fabric/automation/AutomationSessionController.java");
        String driver = src("dev/doughbay/fabric/AutomatedExecutionDriver.java");
        String hud = src("dev/doughbay/fabric/DoughBayHudAlerts.java");
        assertTrue(controller.contains("\"Open listings at the cap (\""), "open-listings detail changed");
        assertTrue(controller.contains("\"Waiting before buying: \""), "waiting detail changed");
        assertTrue(controller.contains("\"Bid desk: \""), "bid desk detail changed");
        assertTrue(driver.contains("\"Switching the auction filter to \""), "switching detail changed");
        assertTrue(hud.contains("d.startsWith(\"Open listings at the cap\")"), "HUD slot reader changed");
        assertTrue(hud.contains("d.startsWith(\"Waiting before\")"), "HUD waiting reader changed");
        assertTrue(hud.contains("d.startsWith(\"Bid desk: \")"), "HUD bid desk reader changed");
        assertTrue(hud.contains("d.startsWith(\"Switching\")"), "HUD switching reader changed");
    }

    @Test
    void blockerReasonSwitchKeepsTheCapitalizedHeads() throws Exception {
        String watcher = src("dev/doughbay/fabric/MarketWatcher.java");
        for (String head : new String[] {"Only", "Last", "Volatility", "Falling", "Profit", "Confidence"})
            assertTrue(watcher.contains("case \"" + head + "\""), "reason switch operand changed: " + head);
    }

    @Test
    void pauseReasonKeysMatchTheBugReportMap() throws Exception {
        String controller = src("dev/doughbay/fabric/automation/AutomationSessionController.java");
        String log = src("dev/doughbay/fabric/BugReportLog.java");
        for (String reason : new String[] {"Recovered purchase", "Multiple distinct exact sale notices",
                "Post-sale exact coverage is internally ambiguous", "Post-sale own-listing state is ambiguous",
                "Timed out reconciling the new active listing"}) {
            assertTrue(controller.contains("\"" + reason), "pause reason producer changed: " + reason);
            assertTrue(log.contains("\"" + reason + "\""), "BugReportLog map key changed: " + reason);
        }
    }

    @Test
    void updateCheckLogLineKeepsItsShapeForLogParsers() throws Exception {
        String update = src("dev/doughbay/fabric/UpdateCheck.java");
        assertTrue(update.contains("LOGGER.info(\"Tally update check:"), "update check log line changed");
    }

    @Test
    void rivalsAndFollowsHeadsKeepTheirWireShape() throws Exception {
        String pages = src("dev/doughbay/fabric/WebPages.java");
        assertTrue(pages.contains("\"Name\", \"Listed\", \"Items\", \"Shared\", \"Asked\", \"Seen\", \"Status\", \"Bot\", \"24h\""),
                "follows heads changed");
    }
}
