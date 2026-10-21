package dev.doughbay.fabric.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.doughbay.fabric.DoughBayClient;
import dev.doughbay.fabric.NameMask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the player's own account names out of what goes to Discord while
 * "hide name" is on: the panel's text, the picture drawn for it, its menus,
 * and the activity feed. A screenshot of the channel then shows "Account" in
 * place of a name, and a plain head in place of a skin.
 *
 * <p>Only what is shown is changed. A menu option keeps its real value, which
 * is what comes back when it is picked, so choosing an account still works;
 * and nothing the bot stores or reads is touched. Names that are not ours -
 * a buyer, a rival, somebody on the payroll - are left as they are.
 */
public final class DiscordPrivacy {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** A head every skin service has, for where one of ours would have been drawn. */
    static final String PLAIN_HEAD = "MHF_Steve";
    /** Keys whose text a person reads, under a message's components. Everything else there is an id. */
    private static final Set<String> COMPONENT_TEXT = Set.of("label", "placeholder", "description", "title");

    private DiscordPrivacy() { }

    /** This client's account and its hive siblings, when names are being hidden; empty otherwise. */
    static List<String> hidden() {
        if (!NameMask.on()) return List.of();
        return orderedNames(DoughBayClient.account(), DoughBayClient.hiveAccounts());
    }

    /** Each sender must number the same Hive roster identically, not put itself first. */
    static List<String> orderedNames(String own, List<String> roster) {
        var names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (String name : roster) if (name != null && !name.isBlank()) names.add(name);
        if (own != null && !own.isBlank()) names.add(own);
        return new ArrayList<>(names);
    }

    public static String hide(String text) {
        return hide(text, hidden());
    }

    /** What stands in for the nth of our names: "Account", or "Account 2" where there is more than one. */
    static String alias(int index, int count) {
        return count <= 1 ? "Account" : "Account " + (index + 1);
    }

    /** {@code text} with each of {@code names} replaced, whole words only and whatever the case. */
    static String hide(String text, List<String> names) {
        if (text == null || text.isEmpty() || names.isEmpty()) return text;
        String out = text;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            Pattern whole = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])",
                    Pattern.CASE_INSENSITIVE);
            out = whole.matcher(out).replaceAll(Matcher.quoteReplacement(alias(i, names.size())));
        }
        return out;
    }

    /** A skin lookup for one of our names becomes the plain head; anyone else's is left alone. */
    static String face(String name, List<String> names) {
        if (name == null) return null;
        for (String ours : names) if (ours.equalsIgnoreCase(name)) return PLAIN_HEAD;
        return name;
    }

    public static String payload(String json) {
        return payload(json, hidden());
    }

    /**
     * A message or interaction body with our names taken out of everything a
     * person reads. Text that cannot be parsed is sent as it was: a failure
     * here must not stop the panel.
     */
    static String payload(String json, List<String> names) {
        if (json == null || names.isEmpty()) return json;
        try {
            JsonNode root = MAPPER.readTree(json);
            walk(root, false, names);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return json;
        }
    }

    private static void walk(JsonNode node, boolean components, List<String> names) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            fields.forEachRemaining(entries::add);
            for (Map.Entry<String, JsonNode> e : entries) {
                String key = e.getKey();
                JsonNode value = e.getValue();
                boolean inComponents = components || key.equals("components");
                // An id is an id wherever it sits; a modal's is outside any components block.
                if (key.equals("custom_id")) continue;
                if (value.isTextual()) {
                    String text = value.asText();
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (lower.endsWith("url")) {
                        // A head fetched by name: the name is in the address.
                        object.set(key, new TextNode(headUrl(text, names)));
                    } else if (!inComponents || COMPONENT_TEXT.contains(key)) {
                        object.set(key, new TextNode(hide(text, names)));
                    }
                } else {
                    walk(value, inComponents, names);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) walk(child, components, names);
        }
    }

    private static String headUrl(String url, List<String> names) {
        String out = url;
        for (String name : names) {
            Pattern segment = Pattern.compile("(?<=/)" + Pattern.quote(name) + "(?=[/?.]|$)", Pattern.CASE_INSENSITIVE);
            out = segment.matcher(out).replaceAll(PLAIN_HEAD);
        }
        return out;
    }

    /** The picture's page with our names out of every cell and plain heads where ours would be drawn. */
    static StatsImage.Page page(StatsImage.Page page) {
        List<String> names = hidden();
        if (page == null || names.isEmpty()) return page;
        return new StatsImage.Page(hide(page.title(), names), hide(page.subtitle(), names), page.stateColor(),
                rows(page.headline(), names), page.gridUsed(), page.gridTotal(), cells(page.columns(), names),
                page.rightAlign(), rows(page.rows(), names), page.rowIcons(), page.iconColumn(),
                hide(page.footer(), names), faces(page.faces(), names), faces(page.rowFaces(), names), page.mascotFace());
    }

    private static List<String[]> rows(List<String[]> rows, List<String> names) {
        if (rows == null) return null;
        List<String[]> out = new ArrayList<>(rows.size());
        for (String[] row : rows) out.add(cells(row, names));
        return out;
    }

    private static String[] cells(String[] row, List<String> names) {
        if (row == null) return null;
        String[] out = new String[row.length];
        for (int i = 0; i < row.length; i++) out[i] = hide(row[i], names);
        return out;
    }

    private static List<String> faces(List<String> faces, List<String> names) {
        if (faces == null) return null;
        List<String> out = new ArrayList<>(faces.size());
        for (String face : faces) out.add(face(face, names));
        return out;
    }
}
