package dev.doughbay.fabric.web;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class PanelV2ContractTest {
    @Test void actualServedPageExportsTheHostedRelayFixture() throws Exception {
        assertTrue(Pages.APP.contains("<meta name=\"gonuts-panel\" content=\"v2\">"));
        assertTrue(Pages.APP.contains("[hidden] { display:none !important; }"));
        assertTrue(Pages.APP.getBytes(StandardCharsets.UTF_8).length < 256*1024);
        assertTrue(Pages.APP.contains("change('simple',{group:group.id,option:proposal.option,expected:proposal.expected})"));
        var file=Path.of("build/reports/panel-v2.html");Files.createDirectories(file.getParent());Files.writeString(file,Pages.APP);
    }
}
