package dev.doughbay.fabric;

import java.util.List;

/** Plain-language lessons with navigation targets, never configuration or trading actions. */
final class Tutorials {
    record Step(String title,String explanation,String target,String action){}
    record Course(String id,String title,List<Step> steps){}
    static final Step SETTINGS=new Step("Simple and Advanced settings",
        "Open Settings for four grouped controls: Order management, Trading style, Action pace and Order batch ceiling. Switching to Advanced view or back changes no saved setting. Custom - preserved means your existing combination is retained. Choose a group to preview the exact settings and consequences, then Apply only if you want those changes; Cancel leaves them alone. Advanced view keeps every individual control in categories. Warnings explain conflicting choices and reduced effective limits; a batch ceiling is not a guaranteed order size. Help & setup reopens the beginner route. The local and hosted browser panels use the same setting categories and explanations.","settings","Open Simple settings");
    static final Step UPDATES=new Step("Keep Tally current",
        "About shows your installed build and update status. Update checking shows a notice when a newer public build is available. With auto-update enabled, a checked download is staged and installed when the game closes; the running bot is not replaced mid-session. Review the notice and changelog. Choose when to close and reopen the game. You can tune update checks and auto-update in Settings > Advanced view > Session & data.","updates","Open update settings");
    static final Step EVASION=new Step("Choose your escape protection",
        "Safety > Evasion contains the existing nearby-player and danger controls. Review the guard, trigger distance, escape route and reconnect behavior before trading. A home route needs homes that already exist on the server. RTP may require a server menu or warm-up; a blocked escape may disconnect protectively. A tutorial does not turn the guard on, change its distances or test a teleport. Read each setting and choose what suits your server.","evasion","Open Evasion");
    static final Step STAFF=new Step("Understand Staff Watch",
        "A qualified local spectator detection means a staff member was detected spectating on the same server. It does not verify that they are watching you. Read the alert's evidence: an online entry or uncertain signal is not the same as a local spectator detection. Review detection and region-escape options under Safety > Staff Watch. Shared sightings can be old or from another region; absence of a report is not an all-clear.","staff","Open Staff Watch");
    static final Step ITEMS=new Step("Choose your items",
        "Trading > Items lets you choose the markets Tally may work with. Search for an item, inspect its list membership and use the allow, deny and watch controls deliberately. An allow list limits what can trade; deny excludes unwanted items. A tracked item is something to watch, not an instruction to buy it. Start with markets you understand. Item variants, stack sizes and current prices still matter.","items","Open Items");
    static final Step AUCTION=new Step("Tune auction trading",
        "Trading > Auction collects buying limits, listings, repricing and item settings. The same controls remain in Settings > Advanced view > Auction. Read the explanation before applying a change. Settings > Advanced view > Session & data contains shared session limits and pacing. Keep spending and listing limits within the balance and inventory room you intend to use. Opening this page does not apply a preset or start a session.","auction","Open Auction settings");
    static final Step ORDERS=new Step("Tune buy orders and delivery",
        "Trading > Orders opens order settings for slots, sizing, bids, fills, collection and delivery. The same controls remain in Settings > Advanced view > Orders. Money committed to an order is still money in use. Partly filled orders may leave stock to collect and money outstanding. Choose Order book within Orders to review current orders; do not assume a missing row means a fill or a cancellation. Local observations, your own orders and fresh live quotes decide actions.","orders","Open Orders settings");
    static final Step FLIPS=new Step("Use the Flips page",
        "Flips helps compare buying and selling opportunities. Search for the exact item and stack, inspect the available prices and activity, and review the proposed costs and resale assumptions. A displayed spread is an estimate, not guaranteed profit or a completed trade. Current availability, fees, sale speed, inventory and caps can prevent a trade. Recheck before choosing an action.","flips","Open Flips");
    static final Step SWARM=new Step("Public services retired",ProjectStatus.NOTICE,"swarm","Project status");
    static final Step SHARED=new Step("Standalone trading",ProjectStatus.NOTICE,"swarm","Project status");
    static final Step BUGS=new Step("Public support retired",ProjectStatus.NOTICE,"bugs","Project status");
    static final Step TRADE=new Step("Find AutoPilot: open Trade",
        "AutoPilot has not been removed: its tab is now called Trade. Press B to open Tally, then choose Trading > Trade, or Manage trading on the Dashboard. For guided setup, open Settings > Help & setup > 1 SET UP TRADING - START HERE: Choose limits, then Review & Start. On Trade, Continuous contains readiness, limits and session controls. If live trading is off, review Max per trade and Max per session, choose Enable live trading and confirm, then review readiness before Start. If a blocker is shown, read it rather than repeatedly starting. Tools: API key / test opens the optional API-key tools; the next button opens Discord command centre, then Web panel. These are separate setup pages, not required steps for every local trading session. Opening this lesson or its target does not enable trading or change settings.","autopilot","Open Trade");
    static final Step READY=new Step("Review, then decide to trade",
        "Check that you are on the intended server, your item choices and limits are correct, protection settings are understood and the bot reports no unresolved recovery or exposure. Open Trade to read its current readiness and any blocked-action reason. Start is your choice: finishing this tutorial does not arm trading, change caps, clear positions or resume a paused bot. Stop and investigate any blocker rather than bypassing it.","autopilot","Review Trade");
    static final List<Course> COURSES=List.of(
        new Course("setup","Quick start · two steps",List.of(AUCTION,READY)),
        new Course("trade","Find AutoPilot / Trade",List.of(TRADE,READY)),
        new Course("settings","Simple / Advanced settings",List.of(SETTINGS)),
        new Course("swarm","Project status",List.of(SWARM,SHARED)),
        new Course("protection","Evasion & Staff Watch",List.of(EVASION,STAFF)),
        new Course("items","Choose items",List.of(ITEMS,AUCTION)),
        new Course("flips","Explore Flips",List.of(FLIPS,AUCTION)),
        new Course("orders","Use buy orders",List.of(ORDERS)),
        new Course("updates","Updates & notifications",List.of(UPDATES)),
        new Course("bugs","Report a bug",List.of(BUGS)));
    private Tutorials(){}
}
