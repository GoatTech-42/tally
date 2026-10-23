package dev.doughbay.fabric;

/** Window-based scaling for mod menus, independent of the player's saved HUD scale. */
public final class GoNutsUiScale {
    private GoNutsUiScale() { }
    public static int preferred(int pixelsWide,int pixelsHigh){
        int width=Math.max(1,pixelsWide),height=Math.max(1,pixelsHigh);
        int readable=(int)Math.round(Math.min(width/800d,height/480d));
        int fits=Math.max(1,Math.min(width/640,height/360));
        return Math.max(1,Math.min(readable,fits));
    }
}
