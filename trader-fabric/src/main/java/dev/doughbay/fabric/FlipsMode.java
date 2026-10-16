package dev.doughbay.fabric;

/** An empty manual list must never silently authorize automatic market selection. */
public final class FlipsMode {
    private FlipsMode() {}
    public static boolean only() { return Tuning.get("prices.only") >= 0.5; }
    public static String description() {
        if(!only())return "Flips + automatic markets: your list runs alongside normal trading.";
        return OwnPrices.all().isEmpty()?"Flips only: add an item to begin. General market scans are off."
                :"Flips only: trades your list. General market scans are off.";
    }
}
