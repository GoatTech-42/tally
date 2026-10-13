package dev.doughbay.fabric;

import java.util.Locale;

/**
 * One change at a time to the player's own price list, for the places that
 * cannot show the whole list to be edited: a single box on Discord asking for
 * a price. Each takes the list as it is written and gives back the list as it
 * should be written, or says why not, so nothing here touches the settings.
 */
public final class FlipEdits {
    /**
     * @param ok       whether the list changed
     * @param listText the list afterwards; the list as it was when it did not change
     * @param message  what happened, in words for the player
     */
    public record Result(boolean ok, String listText, String message) {
    }

    private FlipEdits() {
    }

    /** A new sell price for an item already on the list. */
    public static Result sell(String listText, String itemId, String typed) {
        OwnPrices.Entry e = entry(listText, itemId);
        if (e == null) return no(listText, name(itemId) + " is not on your Flips list");
        long price = MoneyInput.parse(typed);
        if (price <= 0) return no(listText, "Not a price: " + show(typed));
        if (price <= e.buy()) {
            return no(listText, "The sell price has to be above the buy price (" + Figures.grouped(e.buy()) + ")");
        }
        if (price == e.sell()) return no(listText, name(itemId) + " already sells at " + Figures.grouped(price) + " each");
        OwnPrices.Entry next = new OwnPrices.Entry(e.itemId(), e.buy(), price, e.stacks(), e.singles(), e.over(), e.total(), e.budget());
        return new Result(true, OwnPrices.with(listText, next, null), name(itemId) + " now sells at "
                + Figures.grouped(price) + " each (was " + Figures.grouped(e.sell()) + ")");
    }

    /** A new buy price, or a new most-to-pay for an entry that follows the top order. */
    public static Result buy(String listText, String itemId, String typed) {
        OwnPrices.Entry e = entry(listText, itemId);
        if (e == null) return no(listText, name(itemId) + " is not on your Flips list");
        long price = MoneyInput.parse(typed);
        if (price <= 0) return no(listText, "Not a price: " + show(typed));
        if (price >= e.sell()) {
            return no(listText, "The buy price has to be below the sell price (" + Figures.grouped(e.sell()) + ")");
        }
        if (price == e.buy()) return no(listText, name(itemId) + " is already bought at " + Figures.grouped(price) + " each");
        OwnPrices.Entry next = new OwnPrices.Entry(e.itemId(), price, e.sell(), e.stacks(), e.singles(), e.over(), e.total(), e.budget());
        return new Result(true, OwnPrices.with(listText, next, null), name(itemId) + " is now bought at "
                + (e.top() ? "up to " : "") + Figures.grouped(price) + " each (was " + Figures.grouped(e.buy()) + ")");
    }

    /** One entry typed out in full, "obsidian 250 900": added, or put in place of the item's entry. */
    public static Result add(String listText, String typed) {
        String line = typed == null ? "" : typed.replaceAll("[,;\\r\\n]+", " ").strip();
        if (line.isEmpty()) return no(listText, "Type an item, a buy price and a sell price, like: obsidian 250 900");
        OwnPrices.Parsed read = OwnPrices.parse(line);
        if (!read.problems().isEmpty() || read.entries().size() != 1) {
            return no(listText, read.problems().isEmpty() ? "One item at a time, like: obsidian 250 900" : read.problems().get(0));
        }
        OwnPrices.Entry e = read.entries().values().iterator().next();
        boolean was = entry(listText, e.itemId()) != null;
        return new Result(true, OwnPrices.with(listText, e, null), name(e.itemId()) + (was ? " changed: " : " added: ")
                + "buy " + Figures.grouped(e.buy()) + ", sell " + Figures.grouped(e.sell()) + " each");
    }

    /** An item taken off the list. */
    public static Result remove(String listText, String itemId) {
        if (entry(listText, itemId) == null) return no(listText, name(itemId) + " is not on your Flips list");
        return new Result(true, OwnPrices.without(listText, itemId), name(itemId) + " taken off your Flips list");
    }

    private static OwnPrices.Entry entry(String listText, String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        return OwnPrices.parse(listText).entries().get(Tuning.normalizeItem(itemId));
    }

    private static Result no(String listText, String why) {
        return new Result(false, listText == null ? "" : listText, why);
    }

    private static String name(String itemId) {
        String bare = itemId == null ? "" : Tuning.normalizeItem(itemId).replace('_', ' ');
        return bare.isEmpty() ? "That item" : bare.substring(0, 1).toUpperCase(Locale.ROOT) + bare.substring(1);
    }

    private static String show(String typed) {
        String t = typed == null ? "" : typed.strip();
        return t.isEmpty() ? "nothing was typed" : (t.length() > 40 ? t.substring(0, 40) : t);
    }
}
