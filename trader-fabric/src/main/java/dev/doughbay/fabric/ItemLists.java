package dev.doughbay.fabric;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The edits the Items tab makes to the trade filter and the watch list. They
 * live here so the screen in game and the web panel make exactly the same
 * ones, and so a hundred ticked items are one save of each list, not a
 * hundred.
 */
final class ItemLists {
    private ItemLists() {
    }

    /**
     * Whether an item is covered by a list, the same way the trader decides it:
     * an exact entry, or a suffix rule (a {@code boots} entry covers
     * {@code netherite_boots}).
     */
    static boolean listMatches(Set<String> tokens, String bare) {
        if (tokens.contains(bare)) return true;
        for (String t : tokens) if (bare.endsWith("_" + t)) return true;
        return false;
    }

    /** Puts every id on a list, or takes every one off it; saved once, and only if something moved. */
    private static boolean edit(String key, Collection<String> ids, boolean add) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String token : Tuning.text(key).split("[,;\\s]+")) {
            String n = Tuning.normalizeItem(token);
            if (!n.isEmpty()) tokens.add(n);
        }
        boolean changed = false;
        for (String raw : ids) {
            String id = Tuning.normalizeItem(raw);
            if (id.isEmpty()) continue;
            changed |= add ? tokens.add(id) : tokens.remove(id);
        }
        if (changed) Tuning.setText(key, String.join(", ", tokens));
        return changed;
    }

    /** Never traded: on the deny list and off the allow list. */
    static void exclude(Collection<String> ids) {
        edit("items.allow", ids, false);
        edit("items.deny", ids, true);
    }

    /** May be traded: on the allow list and off the deny list. */
    static void include(Collection<String> ids) {
        edit("items.deny", ids, false);
        edit("items.allow", ids, true);
    }

    /** On neither list. */
    static void clear(Collection<String> ids) {
        edit("items.deny", ids, false);
        edit("items.allow", ids, false);
    }

    /** The watch list the config file sets, which stands until the list is first edited. */
    private static Collection<String> configTracked() {
        try {
            DoughBayConfig cfg = DoughBayClient.activeConfig();
            return cfg == null ? List.of() : cfg.trackedCommodities();
        } catch (Throwable e) {
            return List.of();   // not running inside the game
        }
    }

    static Set<String> tracked() {
        // The config file is only asked while it still has the say.
        if (Tuning.get("watch.tracked_managed") >= 0.5) return Tuning.itemSet("watch.tracked");
        return tracked(configTracked());
    }

    /**
     * The effective watch list as bare ids. Once the list has been edited
     * ({@code watch.tracked_managed}) the hot list is the whole truth, even
     * empty; before that it mirrors the config file so an untouched build shows
     * its real set.
     */
    static Set<String> tracked(Collection<String> fromConfig) {
        if (Tuning.get("watch.tracked_managed") >= 0.5) return Tuning.itemSet("watch.tracked");
        Set<String> out = new HashSet<>();
        for (String id : fromConfig) {
            String b = Tuning.normalizeItem(id);
            if (!b.isEmpty()) out.add(b);
        }
        return out;
    }

    static void seedTracked() {
        if (Tuning.get("watch.tracked_managed") >= 0.5) return;
        seedTracked(configTracked());
    }

    /**
     * On the first edit, copy the config watch list into the hot one and mark
     * it managed, so nothing is lost and later edits stick even to empty.
     */
    static void seedTracked(Collection<String> fromConfig) {
        if (Tuning.get("watch.tracked_managed") >= 0.5) return;
        LinkedHashSet<String> bare = new LinkedHashSet<>();
        for (String id : fromConfig) {
            String b = Tuning.normalizeItem(id);
            if (!b.isEmpty()) bare.add(b);
        }
        Tuning.setText("watch.tracked", String.join(", ", bare));
        Tuning.set("watch.tracked_managed", 1);
    }

    static void track(Collection<String> ids, boolean track) {
        seedTracked();
        edit("watch.tracked", ids, track);
    }

    static void track(Collection<String> ids, boolean track, Collection<String> fromConfig) {
        seedTracked(fromConfig);
        edit("watch.tracked", ids, track);
    }
}
