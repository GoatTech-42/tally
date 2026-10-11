package dev.doughbay.fabric.discord;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writes a sample Items page to build/discord-items-sample.png so seven columns can be checked by eye. */
class ItemsPageSampleTest {

    @Test
    void writesSample() throws Exception {
        String[][] items = {
                {"Wither Skeleton Skull", "$50.2M", "1063", "$47.2K", "$343K", "3m", "$1.20M"},
                {"Golden Apple", "$29.3M", "1371", "$21.4K", "$293K", "36s", "$410K"},
                {"Block of Emerald", "$27.2M", "1030", "$26.4K", "$199K", "2m", "$880K"},
                {"Cobweb", "$22.2M", "1021", "$21.8K", "$188K", "2m", "-"},
                {"Deepslate Diamond Ore", "$18.5M", "748", "$24.7K", "$146K", "4m", "$2.10M"},
                {"Diamond", "$6.68M", "511", "$13.1K", "$108K", "1m", "$65.0K"},
                {"TNT", "$6.44M", "212", "$30.4K", "$256K", "54s", "-"},
                {"Black Concrete", "$5.65M", "201", "$28.1K", "$146K", "1m", "$115K"},
                {"Chiseled Polished Blackstone", "$1.25M", "55", "$22.7K", "$854K", "48s", "$64.0K"},
                {"Cracked Polished Blackstone Bricks", "$724K", "6", "$121K", "$4.52M", "24s", "-"},
                {"Block of Quartz", "$713K", "14", "$50.9K", "$843K", "1m", "-"},
                {"Bread", "$461K", "20", "$23.1K", "$1.03M", "12s", "-"},
                {"Block of Amethyst", "$378K", "8", "$47.3K", "$1.17M", "2m", "$26.0K"},
                {"Eye of Ender", "$284K", "31", "$9.2K", "$106K", "3m", "-"},
                {"Anvil", "-", "-", "-", "-", "-", "$300K"},
        };
        List<String[]> rows = new ArrayList<>(List.of(items));
        StatsImage.Page page = new StatsImage.Page("Items · by profit", "LISTING · 06:40 · bank $31.4M", "green",
                List.of(new String[] {"Profit", "$188.7M · 24 h"}, new String[] {"Sales", "7676"},
                        new String[] {"Return", "19%"}, new String[] {"Tied up", "$8.0M in 86"}),
                0, 0,
                new String[] {"Item", "Profit", "Sales", "Per sale", "Per slot-hr", "Sells in", "Tied up"},
                new boolean[] {false, true, true, true, true, true, true}, rows,
                "Listing parked (84 open); cooldown before the next buy");
        byte[] png = StatsImage.render(page);
        Path out = Path.of("build", "discord-items-sample.png");
        Files.createDirectories(out.getParent());
        Files.write(out, png);
        assertTrue(Files.size(out) > 2000);
    }
}
