package dev.doughbay.fabric;

/** Navigation only: every row still edits the same underlying setting. */
public enum SettingsSection {
    AUCTION("Auction", "Auction buying, listings, repricing and item limits."),
    ORDERS("Orders", "Buy orders, fills, collection and delivery."),
    GENERAL("General", "Shared session limits, pacing, display and other settings."),
    ALL("All", "All settings together. Evasion also has its own page.");

    final String label, description;
    SettingsSection(String label,String description){this.label=label;this.description=description;}
    public boolean includes(String key,String group){return this==ALL||of(key,group)==this;}
    public static SettingsSection of(String key,String group){
        if (group.equals("Orders")||group.equals("Underdog"))return ORDERS;
        if (java.util.Set.of("Buying","Selling","Pull-backs","Boxes","Tiers","Slots","Flips",
                "Adaptive market","Rivals").contains(group))return AUCTION;
        if(key.startsWith("items.")||key.startsWith("watch.")||key.equals("prices.own")||key.equals("boxes.sweep_term"))return AUCTION;
        return GENERAL;
    }
}
