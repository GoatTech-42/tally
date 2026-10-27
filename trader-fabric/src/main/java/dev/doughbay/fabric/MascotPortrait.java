package dev.doughbay.fabric;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** A still pose of the selected native character for the web and Discord panels. */
public final class MascotPortrait {
    private MascotPortrait() { }
    public static BufferedImage render(Appearance.Character character, String state) {
        var image = new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        var canvas = new MascotFace.Canvas() {
            public void fill(int l,int t,int r,int b,int color) { graphics.setColor(new Color(color,true)); graphics.fillRect(l,t,r-l,b-t); }
            public void clip(int l,int t,int r,int b) { graphics.setClip(l,t,r-l,b-t); }
            public void unclip() { graphics.setClip(null); }
        };
        try {
            MascotCharacters.draw(canvas,character,0,true);
            if (character != Appearance.Character.TALLY) {
                var mood=MascotAnimation.Mood.valueOf(state.toUpperCase(java.util.Locale.ROOT));
                MascotFace.draw(canvas,new MascotAnimation().frame(mood,1_000,0,true));
            }
        } finally { graphics.dispose(); }
        return image;
    }
}
