package dev.doughbay.fabric.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.OrderMetrics;
import dev.doughbay.storage.Database;
import java.nio.file.Path;
import java.util.*;

/** Read-only browser projections of the existing account and image data. */
final class PanelReadModel {
    private static final ObjectMapper JSON = new ObjectMapper();
    private PanelReadModel() {}

    static ObjectNode report(StatsImage.Page page) {
        var out = JSON.createObjectNode().put("title", page.title()).put("subtitle", page.subtitle()).put("footer", page.footer());
        out.set("headline", JSON.valueToTree(page.headline()));
        out.set("columns", JSON.valueToTree(page.columns()));
        out.set("rows", JSON.valueToTree(page.rows()));
        out.put("slotsUsed", page.gridUsed()).put("slotsTotal", page.gridTotal());
        return out;
    }

    static List<String> currentMembers(List<String> connected, String current) {
        return OrderMetrics.currentMembers(connected, current);
    }

    /** Historical accounts stay selectable, but do not supply live auction capacity. */
    static List<OrderMetrics.Account> capacityAccounts(Path root, String selected, List<String> current) {
        return OrderMetrics.select(root, selected, current);
    }

    static com.fasterxml.jackson.databind.node.ArrayNode activity(Path ledger, List<String> current, String selected, long now) {
        var out = JSON.createArrayNode();
        var names = selected == null || selected.isBlank() ? current : List.of(selected);
        try (var db = new Database(ledger);
             var query = db.connection().prepareStatement("SELECT controller_state,detail,updated_at FROM automation_session_state WHERE lower(client)=lower(?)")) {
            for (String name : names) {
                var row = out.addObject().put("account", DiscordPrivacy.hide(name));
                query.setString(1, name);
                try (var result = query.executeQuery()) {
                    if (!result.next()) { row.put("state", "Unavailable").put("detail", "No activity received for this account").put("at", 0).put("current", false); continue; }
                    String detail = Objects.requireNonNullElse(result.getString(2), "").replaceAll("[\\p{Cntrl}]", " ");
                    long at = result.getLong(3);
                    boolean connected = current.stream().anyMatch(name::equalsIgnoreCase);
                    row.put("state", result.getString(1)).put("detail", DiscordPrivacy.hide(detail.substring(0, Math.min(280, detail.length()))))
                        .put("at", at).put("connected", connected).put("current", connected && at <= now && now-at < 120_000);
                }
            }
        } catch (Exception unavailable) {
            out.removeAll();
            for (String name : names) out.addObject().put("account", DiscordPrivacy.hide(name)).put("state", "Unavailable")
                .put("detail", "Activity could not be read; retrying").put("at", 0).put("current", false);
        }
        return out;
    }

    static int listed(Path ledger, List<String> current, String selected) {
        var names = selected == null || selected.isBlank() ? current : List.of(selected);
        int count = 0;
        try (var db = new Database(ledger); var query = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM positions WHERE status='LISTED' AND mode='REAL' AND lower(client)=lower(?)")) {
            for (String name : names) { query.setString(1,name); try (var rows=query.executeQuery()) { rows.next(); count=Math.addExact(count,rows.getInt(1)); } }
            return count;
        } catch (Exception unavailable) { return -1; }
    }
}
