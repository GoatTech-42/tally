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
 * {@code diamond 80 120, golden apple 4k 5.2k x3}. After the two prices, in
 * any order:
 * <ul>
 * <li>{@code x3}: how many stacks to order at a time;</li>
 * <li>{@code singles}: sell what arrives one item at a time, never as a stack;</li>
 * <li>{@code total 1000}: stop ordering once this many have been bought;</li>
 * <li>{@code budget 2m}: stop ordering once this much has been spent on it.</li>
 * </ul>
 * And before the prices, {@code top} or {@code top+100}: do not bid the buy
 * price itself but just over the best order anybody else has up, by one step
 * or by the amount written, and never more than the buy price:
 * {@code observer top+100 600 900 x15 singles total 1000}.
 */
public final class OwnPrices {
    /** The setting the list is typed into. */
    public static final String KEY = "prices.own";
    /**
     * The most that can be written after the x. For something that stacks an
     * order is held to fifteen stacks whatever is written, since the order
     * house shows a count exactly only under a thousand; for something that
     * does not stack, a totem, this is how many of them an order asks for.
     */
    static final int MAX_STACKS = 999;
    private static final Set<String> FILLER = Set.of("buy", "sell", "at", "each", "for", "to", "stack", "stacks", "x",
            "max");
    private static final Set<String> SINGLES = Set.of("singles", "single", "peel", "individually");

    /**
     * @param itemId  the item without its namespace, as the lists in settings write it
     * @param buy     the price of an order, each; bidding over the top order, the most it may come to
     * @param stacks  stacks to order at a time; 0 leaves it to the ordinary setting
     * @param singles sold one item at a time at the sell price, and never as a stack
     * @param over    -1 to bid the buy price as written; otherwise bid this much over the best order
     *                anybody else has up, 0 meaning one step
     * @param total   stop ordering once this many have been bought; 0 for no such limit
     * @param budget  stop ordering once this much has been spent; 0 for no such limit
     */
    public record Entry(String itemId, long buy, long sell, int stacks, boolean singles, long over, int total,
                        long budget) {
        public Entry(String itemId, long buy, long sell, int stacks) {
            this(itemId, buy, sell, stacks, false, -1, 0, 0);
        }

        public Entry(String itemId, long buy, long sell, int stacks, boolean singles) {
            this(itemId, buy, sell, stacks, singles, -1, 0, 0);
        }

        /** Whether the order follows the top of the book instead of standing at the buy price. */
        public boolean top() {
            return over >= 0;
        }

        /** The entry as it is written in the list, and as {@link #parse} reads it back. */
        public String text() {
            StringBuilder out = new StringBuilder(itemId);
            if (top()) out.append(over > 0 ? " top+" + over : " top");
            out.append(' ').append(buy).append(' ').append(sell);
            if (stacks > 0) out.append(" x").append(stacks);
            if (singles) out.append(" singles");
            if (total > 0) out.append(" total ").append(total);
            if (budget > 0) out.append(" budget ").append(budget);
            return out.toString();
        }

        /**
         * What the entry asks for, in a form that changes when the player
         * changes it: what has been bought and spent is counted against this,
         * so a new price or a new total starts a new count.
         */
        public String signature() {
            return buy + "/" + sell + "/" + over + "/" + total + "/" + budget;
        }
    }

    /** @param problems what could not be read, each in words that can be shown to the player */
    public record Parsed(Map<String, Entry> entries, List<String> problems) {
    }

    private static volatile String parsedText;
    private static volatile Parsed parsed = new Parsed(Map.of(), List.of());

    private OwnPrices() {
    }

    /**
     * The price of an order that follows the top of the book: over the best
     * order standing by {@code over}, or by one step (a hundredth, at least
     * one) when {@code over} is 0. With nobody else ordering there is nothing
     * to be over and the most is bid. 0 when being top would cost more than
     * {@code most}: an order under the best one is not filled, so none is
     * placed.
     */
    public static long topBid(long standing, long over, long most) {
        if (most <= 0) return 0;
        if (standing <= 0) return most;
        long step = over > 0 ? over : Math.max(1L, (long) Math.ceil(standing * 0.01));
        long bid = standing + step;
        return bid <= most ? bid : 0;
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
            boolean singles = false;
            long over = -1;
            long total = 0;
            long budget = 0;
            String expecting = "";
            boolean bad = false;
            for (String token : entry.split("->|[\\s:=>@|→]+")) {
                String t = token.strip().toLowerCase(Locale.ROOT);
                if (t.isEmpty()) continue;
                if (!expecting.isEmpty()) {
                    // The number that "total" or "budget" was waiting for.
                    long value = MoneyInput.parse(t);
                    if (value <= 0) bad = true;
                    else if (expecting.equals("total")) total = value;
                    else budget = value;
                    expecting = "";
                    continue;
                }
                if (FILLER.contains(t)) continue;
                if (!name.isEmpty() && SINGLES.contains(t)) {
                    singles = true;
                    continue;
                }
                if (!name.isEmpty() && (t.equals("total") || t.equals("budget"))) {
                    expecting = t;
                    continue;
                }
                if (!name.isEmpty() && prices.isEmpty() && (t.equals("top") || t.startsWith("top+"))) {
                    long by = t.equals("top") ? 0 : MoneyInput.parse(t.substring(4));
                    if (by < 0 || over >= 0) bad = true;
                    else over = by;
                    continue;
                }
                long money = MoneyInput.parse(t);
                if (prices.size() >= 2 || (t.startsWith("x") && t.length() > 1 && t.substring(1).matches("[0-9]+"))) {
                    // Past the two prices, or written x3: how many stacks.
                    String n = t.startsWith("x") ? t.substring(1) : t;
                    if (!n.matches("[0-9]{1,3}") || stacks != 0) bad = true;
                    else stacks = Integer.parseInt(n);
                } else if (money >= 0 && !name.isEmpty()) {
                    prices.add(money);
                } else if (prices.isEmpty() && over < 0) {
                    name.add(t);
                } else {
                    bad = true;
                }
            }
            // "minecraft:diamond" has come apart at its colon; the namespace is not part of the name.
            if (name.size() > 1 && name.get(0).equals("minecraft")) name.remove(0);
            String item = Tuning.normalizeItem(String.join("_", name));
            if (bad || !expecting.isEmpty() || item.isEmpty() || prices.size() != 2) {
                problems.add("\"" + entry + "\" is not an item, a buy price and a sell price");
                continue;
            }
            if (prices.get(1) <= prices.get(0)) {
                problems.add(item + ": the sell price has to be above the buy price");
                continue;
            }
            if (stacks < 0 || stacks > MAX_STACKS) {
                problems.add(item + ": at most " + MAX_STACKS + " an order");
                continue;
            }
            if (total > 1_000_000) {
                problems.add(item + ": a total of at most 1,000,000");
                continue;
            }
            if (entries.containsKey(item)) {
                problems.add(item + " is on the list twice; the first is used");
                continue;
            }
            entries.put(item, new Entry(item, prices.get(0), prices.get(1), stacks, singles, over, (int) total, budget));
        }
        return new Parsed(java.util.Collections.unmodifiableMap(entries), List.copyOf(problems));
    }

    /**
     * The list with one entry put in: in place of the entry for {@code replacing}
     * when there is one, in place of its own when the item is already listed,
     * and at the end otherwise. What could not be read in the old text is not
     * carried over; it was never traded.
     */
    public static String with(String listText, Entry entry, String replacing) {
        Map<String, Entry> old = parse(listText).entries();
        String gone = replacing == null ? "" : Tuning.normalizeItem(replacing);
        List<String> out = new ArrayList<>();
        boolean placed = false;
        for (Entry e : old.values()) {
            boolean here = e.itemId().equals(gone) || e.itemId().equals(entry.itemId());
            if (!here) out.add(e.text());
            else if (!placed) {
                out.add(entry.text());
                placed = true;
            }
        }
        if (!placed) out.add(entry.text());
        return String.join(", ", out);
    }

    /** The list with an item's entry taken out. */
    public static String without(String listText, String itemId) {
        String gone = Tuning.normalizeItem(itemId);
        List<String> out = new ArrayList<>();
        for (Entry e : parse(listText).entries().values()) if (!e.itemId().equals(gone)) out.add(e.text());
        return String.join(", ", out);
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
