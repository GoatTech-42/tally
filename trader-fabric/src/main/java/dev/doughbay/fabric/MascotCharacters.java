package dev.doughbay.fabric;

/** Native vector characters, with moving ears/leaves and the shared expressive face. */
final class MascotCharacters {
    private MascotCharacters() { }

    static void draw(MascotFace.Canvas c, Appearance.Character character, long clock, boolean still) {
        double sway = still ? 0 : Math.sin(clock / 650.0) * 3;
        ellipse(c, 128, 234, 65, 7, 0x45000000);
        if (character == Appearance.Character.MOCHI) {
            // A plum cat with tall ears, a curled tail, and lilac paws.
            ellipse(c, 203, 183, 24, 39, 0xFF8C73B6);
            ellipse(c, 202, 176, 12, 26, 0xFF352C51);
            triangle(c, 52, 104, 66 + (int) sway, 42, 112, 98, 0xFFA38ACD);
            triangle(c, 144, 98, 190 - (int) sway, 42, 205, 105, 0xFFA38ACD);
            triangle(c, 67, 94, 71 + (int) sway, 60, 97, 94, 0xFFEBADCB);
            triangle(c, 159, 94, 185 - (int) sway, 60, 192, 94, 0xFFEBADCB);
            ellipse(c, 128, 147, 84, 82, 0xFFA38ACD);
            ellipse(c, 128, 153, 74, 69, 0xFF352C51);
            ellipse(c, 83, 222, 23, 12, 0xFFCBB5E6);
            ellipse(c, 173, 222, 23, 12, 0xFFCBB5E6);
            for (int side : new int[]{-1, 1}) {
                ellipse(c, 128 + side * 68, 181, 9, 4, 0xFFBD88B8);
                c.fill(128 + side * 82 - 10, 174, 128 + side * 82 + 10, 176, 0xFFE0CFEB);
            }
        } else if (character == Appearance.Character.SPROUT) {
            // A forest creature, with a living sprout and a terracotta pot.
            c.fill(124, 54, 132, 104, 0xFF68B78B);
            ellipse(c, 107 + sway, 60, 30, 16, 0xFF88D997);
            ellipse(c, 146 + sway, 45, 27, 18, 0xFFB5EBA0);
            ellipse(c, 83, 171, 40, 26, 0xFF4C9877);
            ellipse(c, 173, 171, 40, 26, 0xFF4C9877);
            ellipse(c, 128, 143, 77, 69, 0xFF69B48C);
            ellipse(c, 128, 154, 66, 60, 0xFF183E39);
            c.fill(75, 209, 181, 225, 0xFFD89168);
            c.fill(82, 225, 174, 237, 0xFFAA644F);
            ellipse(c, 128, 214, 59, 9, 0xFFF0AE7C);
            c.fill(105, 224, 115, 234, 0xFFEEB38A);
        } else {
            // Tally: a dark counter with a swaying five-gate tally crown.
            int white = 0xFFFFFFFF, edge = 0xFF3A3A3A, body = 0xFF161616, inset = 0xFF050505, feet = 0xFF2A2A2A;
            ellipse(c, 83, 222, 23, 12, feet);
            ellipse(c, 173, 222, 23, 12, feet);
            ellipse(c, 128, 148, 82, 76, edge);
            ellipse(c, 128, 150, 78, 72, body);
            ellipse(c, 128, 154, 68, 60, inset);
            for (int i = 0; i < 4; i++) {
                int x = 96 + i * 21;
                c.fill(x - 2, 62, x + 3, 100, white);
            }
            for (int i = 0; i <= 12; i++) {
                double t = i / 12.0;
                ellipse(c, 90 + t * 76 + sway * t, 98 - t * 40, 2.4, 2.4, white);
            }
        }
    }

    private static void ellipse(MascotFace.Canvas c, double cx, double cy, double rx, double ry, int color) {
        for (int y = (int) (cy - ry); y < cy + ry; y++) {
            double dy = (y + .5 - cy) / ry;
            if (Math.abs(dy) >= 1) continue;
            int half = (int) Math.round(rx * Math.sqrt(1 - dy * dy));
            c.fill((int) cx - half, y, (int) cx + half, y + 1, color);
        }
    }

    private static void triangle(MascotFace.Canvas c, int ax, int ay, int bx, int by, int cx, int cy, int color) {
        for (int y = Math.min(ay, Math.min(by, cy)); y <= Math.max(ay, Math.max(by, cy)); y++) {
            double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
            int[] xs = {ax, bx, cx}, ys = {ay, by, cy};
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                if (ys[i] == ys[j] || y < Math.min(ys[i], ys[j]) || y > Math.max(ys[i], ys[j])) continue;
                double x = xs[i] + (y - ys[i]) * (xs[j] - xs[i]) / (double) (ys[j] - ys[i]);
                left = Math.min(left, x); right = Math.max(right, x);
            }
            if (left <= right) c.fill((int) left, y, (int) right + 1, y + 1, color);
        }
    }
}
