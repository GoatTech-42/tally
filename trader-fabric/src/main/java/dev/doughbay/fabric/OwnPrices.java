package dev.doughbay.fabric;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The player's own price list: items they want ordered at one price and sold
 * at another, both of their choosing.
 *
 * <p>Everywhere else the mod works a price out. An item written here is the
 * one place it does not: a buy order is kept up at the buy price, what the
 * order delivers is listed at the sell price in the stacks it arrives in, and
 * the listing stays at that price until it sells. Prices are per item, as the
 * order house writes them.
 *
 * <p>Written as {@code item buy sell}, entries apart by commas:
 * {@code diamond 80 120, golden apple 4k 5.2k x3}. The optional last part is
 * how many stacks to order at a time.
 */
public final class OwnPrices {
    /** The setting the list is typed into. */
    public static final String KEY = "prices.own";
    /** An order for more than this many stacks is not what a slip of the finger should be able to ask for. */
    static final int MAX_STACKS = 15;
    private static final Set<String> FILLER = Set.of("buy", "sell", "at", "each", "for", "to", "stack", "stacks", "x");

    /**
     * @param itemId the item without its namespace, as the lists in settings write it
     * @param stacks stacks to order at a time; 0 leaves it to the ordinary setting
     */
    public record Entry(String itemId, long buy, long sell, int stacks) {
    }

    /** @param problems what could not be read, each in words that can be shown to the player */
    public record Parsed(Map<String, Entry> entries, List<String> problems) {
    }

    private static volatile String parsedText;
    private static volatile Parsed parsed = new Parsed(Map.of(), List.of());

    private OwnPrices() {
    }

    public static Parsed parse(String text) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (String raw : (text == null ? "" : text).split("[,;\\r\\n]+")) {
            String entry = raw.strip();
            if (entry.isEmpty()) continue;
            List<String> name = new ArrayList<>();
            List<Long> prices = new ArrayList<>();
            int stacks = 0;
            boolean bad = false;
            for (String token : entry.split("->|[\\s:=>@|\u2192]+")) {
                String t = token.strip().toLowerCase(Locale.ROOT);
                if (t.isEmpty() || FILLER.contains(t)) continue;
                long money = MoneyInput.parse(t);
                if (prices.size() >= 2 || (t.startsWith("x") && t.length() > 1 && t.substring(1).matches("[0-9]+"))) {
                    // Past the two prices, or written x3: how many stacks.
                    String n = t.startsWith("x") ? t.substring(1) : t;
                    if (!n.matches("[0-9]{1,3}") || stacks != 0) bad = true;
                    else stacks = Integer.parseInt(n);
                } else if (money >= 0 && !name.isEmpty()) {
                    prices.add(money);
                } else if (prices.isEmpty()) {
                    name.add(t);
                } else {
                    bad = true;
                }
            }
            // "minecraft:diamond" has come apart at its colon; the namespace is not part of the name.
            if (name.size() > 1 && name.get(0).equals("minecraft")) name.remove(0);
            String item = Tuning.normalizeItem(String.join("_", name));
            if (bad || item.isEmpty() || prices.size() != 2) {
                problems.add("\"" + entry + "\" is not an item, a buy price and a sell price");
                continue;
            }
            if (prices.get(1) <= prices.get(0)) {
                problems.add(item + ": the sell price has to be above the buy price");
                continue;
            }
            if (stacks < 0 || stacks > MAX_STACKS) {
                problems.add(item + ": at most " + MAX_STACKS + " stacks an order");
                continue;
            }
            if (entries.containsKey(item)) {
                problems.add(item + " is on the list twice; the first is used");
                continue;
            }
            entries.put(item, new Entry(item, prices.get(0), prices.get(1), stacks));
        }
        return new Parsed(java.util.Collections.unmodifiableMap(entries), List.copyOf(problems));
    }

    /** The list as it stands in the settings now; read again only when the text has changed. */
    public static Parsed current() {
        String text = Tuning.text(KEY);
        if (!text.equals(parsedText)) {
            parsed = parse(text);
            parsedText = text;
        }
        return parsed;
    }

    public static Collection<Entry> all() {
        return current().entries().values();
    }

    /** The player's prices for an item, by its id with or without the namespace; null when the mod prices it. */
    public static Entry of(String itemId) {
        if (itemId == null || itemId.indexOf('#') >= 0) return null;
        Map<String, Entry> entries = current().entries();
        return entries.isEmpty() ? null : entries.get(Tuning.normalizeItem(itemId));
    }
}
