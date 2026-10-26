package dev.doughbay.fabric;

import dev.doughbay.core.model.NamespacedId;

/** Display labels must pass this policy before any search command is constructed. */
final class ItemSearchText {
    private ItemSearchText() { }

    static String forItem(String canonicalId, String displayName) {
        try {
            if (!NamespacedId.normalize(canonicalId).equals(canonicalId)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Item search requires a canonical item id");
        }
        return requirePlain(displayName);
    }

    static String requirePlain(String text) {
        if (!OutgoingCommandText.valid(text)) {
            // Do not echo a pack-provided label or silently turn it into a different query.
            throw new IllegalArgumentException("Item-search name refused before command construction: only A-Z, a-z, 0-9 and spaces are allowed");
        }
        return text.strip();
    }
}
