package dev.doughbay.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The item behind a name as the server prints it in chat.
 *
 * <p>A follow notice names an item the way a player reads it, and in the
 * plural when there is more than one: "128 Ghast Tears", "16 Blocks of Gold",
 * "3 Minecarts with Chests", "192 Bottles o' Enchanting". Everything else in
 * the mod knows items by id, so the name is turned back into one: the exact
 * name first, then its plausible singulars, against the names the client
 * itself shows. A name that matches nothing stays unresolved; it is never
 * guessed at.
 */
final class ItemNames {
    private static Map<String, String> byName;

    private ItemNames() { }

    /** The item id for a shown name, singular or plural; null when no item is called that. */
    static String resolve(String shown) {
        if (shown == null || shown.isBlank()) return null;
        return resolve(shown, names());
    }

    static String resolve(String shown, Map<String, String> names) {
        if (shown == null || shown.isBlank()) return null;
        for (String candidate : candidates(shown.strip())) {
            String id = names.get(candidate.toLowerCase(Locale.ROOT));
            if (id != null) return id;
        }
        return null;
    }

    /** The name itself and the singulars it could be the plural of, most likely first. */
    static List<String> candidates(String shown) {
        Set<String> out = new LinkedHashSet<>();
        out.add(shown);
        // The counted word is the one before the preposition: "Blocks of
        // Gold", "Carrots on a Stick", and both halves of "Minecarts with Chests".
        for (String joiner : new String[] {" of ", " with ", " o' ", " on a "}) {
            int at = shown.indexOf(joiner);
            if (at <= 0) continue;
            String head = shown.substring(0, at);
            String tail = shown.substring(at + joiner.length());
            for (String h : singulars(head)) {
                out.add(h + joiner + tail);
                for (String t : singulars(tail)) out.add(h + joiner + t);
            }
        }
        out.addAll(singulars(shown));
        return new ArrayList<>(out);
    }

    /** What the last word could be the plural of: "Torches" is a Torch, "Shelves" a Shelf, "Berries" a Berry. */
    private static List<String> singulars(String phrase) {
        List<String> out = new ArrayList<>();
        String lower = phrase.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ies") && phrase.length() > 3) out.add(phrase.substring(0, phrase.length() - 3) + "y");
        if (lower.endsWith("ves") && phrase.length() > 3) {
            out.add(phrase.substring(0, phrase.length() - 3) + "f");
            out.add(phrase.substring(0, phrase.length() - 3) + "fe");
        }
        if (lower.endsWith("es") && phrase.length() > 2) out.add(phrase.substring(0, phrase.length() - 2));
        if (lower.endsWith("s") && phrase.length() > 1) out.add(phrase.substring(0, phrase.length() - 1));
        return out;
    }

    private static synchronized Map<String, String> names() {
        if (byName != null) return byName;
        Map<String, String> built = new HashMap<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null) continue;
            String name;
            try {
                name = new ItemStack(item).getHoverName().getString().strip();
            } catch (RuntimeException e) {
                continue;
            }
            indexNames(built, id.toString(), item.getDescriptionId(), name);
        }
        // Before the language has loaded every name is a key and the table is
        // empty: leave it unbuilt so the next call tries again.
        if (built.size() < 100) return built;
        byName = built;
        return built;
    }

    /** Donut sends English names even when the client uses another language or a resource pack. */
    static void indexNames(Map<String, String> names, String id, String translationKey, String localizedName) {
        String english = net.minecraft.locale.Language.DEFAULT_INSTANCE.getOrDefault(translationKey, "");
        if (!english.isBlank()) names.putIfAbsent(english.strip().toLowerCase(Locale.ROOT), id);
        if (localizedName != null && !localizedName.isBlank()
                && !localizedName.contains("item.") && !localizedName.contains("block.")) {
            names.putIfAbsent(localizedName.strip().toLowerCase(Locale.ROOT), id);
        }
    }
}
