package dev.doughbay.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A passive cockpit view. Highlight the entire server, never a player's exact position. */
final class RegionMapPanel {
    private String selected="asia";
    void init(int x,int y,int width,java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> add) {
        int left=Math.min(170,Math.max(80,width/2-4));
        add.accept(CockpitButton.builder(net.minecraft.network.chat.Component.literal("Region: "+selected),b->{
            var regions=dev.doughbay.fabric.staffwatch.ManualRegionFlow.DESTINATIONS;
            selected=regions.get((regions.indexOf(selected)+1)%regions.size());
            b.setMessage(net.minecraft.network.chat.Component.literal("Region: "+selected));
        }).bounds(x,y+74,left,20).build());
        add.accept(CockpitButton.builder(net.minecraft.network.chat.Component.literal("Switch this client"),b->{
            dev.doughbay.fabric.staffwatch.ManualRegionTransfer.request(selected);
        }).bounds(x+left+6,y+74,Math.min(160,width-left-6),20).build());
    }

    void render(GuiGraphics g, Font font, int x, int y, int width, int bottom) {
        var client = Minecraft.getInstance();
        var view = RegionMapReport.snapshot();
        g.drawString(font, "REGION MAP", x, y + 3, UiTheme.GOLD);
        g.drawString(font, UiTheme.fit(font, "This client · " + view.server(), width), x, y + 22, UiTheme.TEXT);
        String coords = NameMask.on() ? RegionMapReport.coordinates(0, 0, 0, true)
                : client.player == null || client.getConnection() == null ? "XYZ unavailable"
                : RegionMapReport.coordinates(client.player.getX(), client.player.getY(), client.player.getZ(), false);
        g.drawString(font, UiTheme.fit(font, coords, width), x, y + 39, UiTheme.SECONDARY);
        g.drawString(font, UiTheme.fit(font, view.status(), width), x, y + 55, UiTheme.MUTED);
        g.drawString(font, UiTheme.fit(font, dev.doughbay.fabric.staffwatch.ManualRegionTransfer.status(), width), x, y+103, UiTheme.SECONDARY);
        boolean wide = width >= 580;
        int available = Math.min(width - (wide ? 195 : 0), bottom - y - (wide ? 145 : 198));
        int cell = Math.max(1, Math.min(48, available / RegionMapData.GRID));
        int size = cell * RegionMapData.GRID;
        int gx = x + (wide ? 0 : Math.max(0, (width - size) / 2)), gy = y + 122;
        if (available < RegionMapData.GRID) return;
        g.fill(gx - 2, gy - 2, gx + size + 2, gy + size + 2, UiTheme.EDGE);
        for (int row = 0; row < RegionMapData.GRID; row++) {
            for (int col = 0; col < RegionMapData.GRID; col++) {
                int area = RegionMapData.cell(col, row);
                int color = area >= 0 && area == view.area() ? UiTheme.TEXT : RegionMapData.color(area);
                int px = gx + col * cell, py = gy + row * cell;
                g.fill(px, py, px + cell, py + cell, color);
                if (cell >= 5) g.fill(px, py, px + cell, py + 1, UiTheme.EDGE);
                if (cell >= 5) g.fill(px, py, px + 1, py + cell, UiTheme.EDGE);
                if (area >= 0 && cell >= 16) {
                    String label = Integer.toString(area);
                    g.drawString(font, label, px + (cell - font.width(label)) / 2, py + (cell - 8) / 2, 0xFF0A0A0A);
                }
            }
        }
        int lx = wide ? gx + size + 18 : x, ly = wide ? gy : gy + size + 12;
        String[] names = {"NA West", "NA East", "Oceania", "Asia", "EU West", "EU Central"};
        for (int i = 0; i < names.length; i++) {
            int px = lx + (wide ? 0 : (i % 3) * (width / 3));
            int py = ly + (wide ? i * 20 : (i / 3) * 16);
            g.fill(px, py, px + 7, py + 7, RegionMapData.colorByName(names[i]));
            g.drawString(font, names[i], px + 12, py, UiTheme.SECONDARY);
        }
        g.drawString(font, UiTheme.fit(font, "White = your server area · exact position is not plotted", width), x, bottom - 13, UiTheme.MUTED);
    }
}
