package dev.doughbay.fabric;

/** Fixed diagnostic categories only. Never returns any portion of its input. */
public final class BugReportReason {
    private BugReportReason() { }
    public static String classify(String message) {
        if (message == null || message.isBlank()) return "UNCLASSIFIED";
        String s=message.toLowerCase(java.util.Locale.ROOT);
        if(s.contains("no confirmation gui appeared"))return "CONFIRMATION_GUI_TIMEOUT";
        if(s.contains("command queue/menu blocked"))return "COMMAND_DISPATCH_TIMEOUT";
        if(s.contains("listing stack changed before dispatch"))return "LISTING_STACK_CHANGED";
        if(s.contains("listing world is unavailable"))return "LISTING_WORLD_UNAVAILABLE";
        if(s.contains("server refused the listing"))return "SERVER_LISTING_REFUSED";
        if(s.contains("sale notice") && s.contains("timed out"))return "SALE_RECONCILIATION_TIMEOUT";
        if(s.contains("new active listing") && s.contains("timed out"))return "LISTING_RECONCILIATION_TIMEOUT";
        if(s.contains("write was refused") || s.contains("write is refused"))return "PERSISTENCE_REFUSED";
        if(s.contains("durable") || s.contains("checkpoint"))return "PERSISTENCE_WAIT";
        if(s.contains("recovered purchase"))return "RECOVERED_PURCHASE_HOLD";
        if(s.contains("exposure"))return "EXPOSURE_HOLD";
        if(s.contains("inventory room") || s.contains("free slot"))return "INVENTORY_CAPACITY";
        if(s.contains("inventory") || s.contains("safe moment"))return "INVENTORY_SAFETY";
        if(s.contains("driver busy"))return "DRIVER_BUSY";
        if(s.contains("auction lane holds"))return "HELD_STOCK";
        if(s.contains("repricing"))return "REPRICING";
        if(s.contains("are bought") || s.contains("budget you gave it is spent"))return "FLIP_ALLOWANCE_EXHAUSTED";
        if(s.contains("listing(s)") && s.contains("still up"))return "LISTING_CAPACITY";
        if(s.contains("last lot") && s.contains("being sold"))return "RETAIL_STOCK_HELD";
        if(s.contains("buy cap") || s.contains("money left"))return "PURCHASE_CAP";
        if(s.contains("best order") && s.contains("would pass"))return "TOP_BID_CEILING";
        if(s.contains("over three times"))return "PRICE_SANITY";
        if(s.contains("new-purchase focus") || s.contains("market focus"))return "BUY_FOCUS";
        if(s.contains("no item by that name"))return "UNKNOWN_ITEM";
        if(s.contains("server refuses an order"))return "SERVER_REFUSED_ITEM";
        if(s.contains("restart") || s.contains("transfer"))return "WORLD_RECOVERY_HOLD";
        if(s.contains("evasion") || s.contains("staff"))return "PROTECTIVE_HOLD";
        if(s.contains("ambiguous"))return "AMBIGUOUS_EVIDENCE";
        return "UNCLASSIFIED";
    }
}
