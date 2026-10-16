package dev.doughbay.fabric;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** The shared web/Discord picture. Only accepts a coarse snapshot, never raw XYZ. */
public final class RegionMapImage {
    private RegionMapImage() { }
    public static byte[] render(RegionMapReport.Snapshot snapshot) throws IOException {
        var image = new BufferedImage(1200, 870, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(12, 18, 27)); g.fillRect(0, 0, 1200, 870);
            g.setColor(new Color(44, 59, 76)); g.drawRoundRect(12, 12, 1175, 845, 18, 18);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.setColor(new Color(244, 205, 122)); g.drawString("GoNuts  /  Region Map", 32, 60);
            g.setColor(new Color(240, 238, 231)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            g.drawString("This client · " + snapshot.server(), 32, 102);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 19));
            g.setColor(new Color(182, 194, 205)); g.drawString("X ******   Y ******   Z ******", 32, 137);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            g.drawString(snapshot.status(), 32, 166);
            int cell = 64, left = 36, top = 192;
            for (int row = 0; row < RegionMapData.GRID; row++) {
                for (int col = 0; col < RegionMapData.GRID; col++) {
                    int area = RegionMapData.cell(col, row);
                    int color = area >= 0 && area == snapshot.area() ? 0xFFF0EEE7 : RegionMapData.color(area);
                    g.setColor(new Color(color, true));
                    g.fillRect(left + col * cell, top + row * cell, cell - 1, cell - 1);
                    if (area >= 0) {
                        g.setColor(new Color(16, 24, 35));
                        String label = Integer.toString(area);
                        g.drawString(label, left + col * cell + (cell - g.getFontMetrics().stringWidth(label)) / 2,
                                top + row * cell + (cell + g.getFontMetrics().getAscent()) / 2 - 3);
                    }
                }
            }
            String[] names = {"NA West", "NA East", "Oceania", "Asia", "EU West", "EU Central"};
            for (int i = 0; i < names.length; i++) {
                int y = top + 28 + i * 40;
                g.setColor(new Color(RegionMapData.colorByName(names[i]), true)); g.fillRect(660, y - 16, 16, 16);
                g.setColor(new Color(182, 194, 205)); g.drawString(names[i], 690, y);
            }
            g.setColor(new Color(240, 238, 231)); g.fillRect(660, 524, 16, 16);
            g.drawString("Your server area", 690, 540);
            g.setColor(new Color(135, 153, 170));
            g.drawString("Coordinates always hidden in web and Discord images.", 36, 805);
            g.drawString("Exact position is not plotted. Map applies to the DonutSMP Overworld.", 36, 833);
        } finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
    }
}
