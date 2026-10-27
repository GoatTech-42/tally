package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionMapTest {
    @Test void hiddenCoordinateTextDoesNotDependOnPositionOrDigitLength() {
        String hidden = RegionMapReport.coordinates(-183927, 74, 91234, true);
        assertEquals(hidden, RegionMapReport.coordinates(1, -9, 0, true));
        assertEquals(hidden, RegionMapReport.coordinates(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, true));
        assertFalse(hidden.contains("183927")); assertFalse(hidden.contains("91234"));
        assertTrue(hidden.contains("§k"));
        assertEquals("X -183927   Y 74   Z 91234", RegionMapReport.coordinates(-183927, 74, 91234, false));
    }
    @Test void unsupportedWorldsAndDisconnectedClientsNeverReuseTheLastServer() {
        var known = RegionMapReport.locate(1, true, true, -162500, -212500);
        assertTrue(known.mapped()); assertEquals("100 · NA West", known.server());
        assertEquals(-1, RegionMapReport.locate(2, false, true, -162500, -212500).area());
        assertEquals(-1, RegionMapReport.locate(3, true, false, -162500, -212500).area());
    }
    @Test void invalidAndOutOfBoundsCoordinatesCannotBeReportedAsRealServers() {
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -225001, 225000})
            assertEquals(-1, RegionMapData.regionAt(x, 0));
        assertEquals(-1, RegionMapData.cell(-1, 0));
        assertEquals(-1, RegionMapData.cell(0, 9));
    }
    @Test void remoteSnapshotCarriesNoCoordinatesAndHighlightsWholeServer() throws Exception {
        var a = RegionMapReport.locate(1, true, true, -162500, -212500);
        var b = RegionMapReport.locate(2, true, true, -160001, -200001);
        assertEquals(a.area(), b.area());
        assertArrayEquals(RegionMapImage.render(a), RegionMapImage.render(b));
        assertArrayEquals(new String[]{"at", "area", "status"}, java.util.Arrays.stream(RegionMapReport.Snapshot.class.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new));
        byte[] png = RegionMapImage.render(a);
        assertEquals(1200, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
        Path folder = Path.of("build/region-map-preview"); Files.createDirectories(folder); Files.write(folder.resolve("web-discord.png"), png);
    }
    @Test void spectreReferenceAndEveryGeographicFamilyUseTheNumberedNineByNineMap() {
        assertEquals(25, RegionMapData.regionAt(106682, -65143)); // User-provided Spectre reference image.
        assertEquals("25 · NA East", RegionMapData.name(25));
        assertEquals("east",RegionMapData.command(25));
        assertEquals("west",RegionMapData.command(14));
        assertEquals("eu central",RegionMapData.command(61));
        assertEquals("",RegionMapData.command(-1));
        assertEquals(9, RegionMapData.GRID);
        assertEquals(81, java.util.stream.IntStream.range(0,81).map(i -> RegionMapData.cell(i%9,i/9)).distinct().count());
        for (var family : new String[]{"NA West","NA East","Asia","Oceania","EU West","EU Central"})
            assertTrue(java.util.stream.IntStream.range(0,81).map(i -> RegionMapData.cell(i%9,i/9)).anyMatch(id -> RegionMapData.name(id).endsWith(family)));
        assertFalse(java.util.stream.IntStream.range(0,81).map(i -> RegionMapData.cell(i%9,i/9)).anyMatch(id -> RegionMapData.name(id).endsWith("Europe")));
    }

    @Test void regionPaletteIsAchromaticBrightnessSteps() {
        int previous = -1;
        for (int c : RegionMapData.regionTypeColors()) {
            int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
            assertEquals(r, g, "region palette carries no hue");
            assertEquals(g, b, "region palette carries no hue");
            assertTrue(r > previous, "region types stay separated as brightness steps");
            previous = r;
        }
        for (var family : new String[]{"NA West", "NA East", "Oceania", "Asia", "EU West", "EU Central"}) {
            int c = RegionMapData.colorByName(family);
            assertEquals((c >>> 16) & 0xFF, c & 0xFF, "legend swatch carries no hue");
        }
    }
}
