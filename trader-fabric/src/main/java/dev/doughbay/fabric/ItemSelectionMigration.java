package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Retires the hidden purchase filter into the lists exposed by the Items tab. */
final class ItemSelectionMigration {
    static final String LEGACY = "items.buy_focus";
    private ItemSelectionMigration() { }

    static boolean migrate(ObjectNode root, Collection<String> configured, Collection<String> catalog) {
        if (!root.has(LEGACY)) return false;
        Set<String> focus = tokens(root.path(LEGACY).asText(""));
        root.remove(LEGACY);
        // An older client sharing the file may write the retired field back.
        // Never let that restore selections the user has since changed in Items.
        if (root.path("_itemSelectionVersion").asInt(0) >= 1) return true;
        root.put("_itemSelectionVersion", 1);
        if (focus.isEmpty()) return true;

        // Existing visible inclusions remain selected. A focus-only setup becomes
        // an explicit include list rather than accidentally enabling every market.
        Set<String> included = tokens(root.path("items.allow").asText(""));
        included.addAll(focus);
        root.put("items.allow", String.join(", ", included));

        Set<String> tracked = tokens(root.path("watch.tracked").asText(""));
        if (root.path("watch.tracked_managed").asDouble(0) < .5) {
            tracked.addAll(tokens(String.join(",", configured)));
        }
        Set<String> candidates = new LinkedHashSet<>(catalog);
        candidates.addAll(configured);
        candidates.addAll(tracked);
        for (String token : focus) {
            // Exact-match syntax belongs to Include, not to a registry item ID.
            String bare = token.startsWith("=") ? token.substring(1) : token;
            boolean matched = false;
            for (String candidate : candidates) {
                String id = Tuning.normalizeItem(candidate);
                if (Tuning.tokenCovers(token, id)) { tracked.add(id); matched = true; }
            }
            if (!matched) tracked.add(bare);
        }
        root.put("watch.tracked", String.join(", ", tracked));
        root.put("watch.tracked_managed", 1);
        // Exclusions, liquidation choices, prices, caps and guards are untouched.
        return true;
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : text.split("[,;\\s]+")) {
            String value = Tuning.normalizeItem(raw);
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }
}
