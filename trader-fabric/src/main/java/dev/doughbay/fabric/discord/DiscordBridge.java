package dev.doughbay.fabric.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.execution.ExecutionResult;
import dev.doughbay.core.model.Listing;
import dev.doughbay.core.model.MarketStats;
import dev.doughbay.core.model.Opportunity;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.DoughBayClient;
import dev.doughbay.fabric.hosted.SwarmRank;
import dev.doughbay.fabric.MarketWatcher;
import dev.doughbay.fabric.OrderBook;
import dev.doughbay.fabric.Payroll;
import dev.doughbay.fabric.RivalIntel;
import dev.doughbay.fabric.Tuning;
import dev.doughbay.fabric.automation.AutomationSessionController;
import dev.doughbay.storage.Database;
import dev.doughbay.storage.PayrollRepository;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The command centre: one Discord message, edited every half minute with a
 * rendered page of the trader's state, and buttons, dropdowns and forms that
 * start, stop and tune it. Only the configured operator's presses count.
 */
public final class DiscordBridge {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final long REFRESH_MILLIS = 15_000;
    private static final long FEED_MILLIS = 5_000;

    enum Page { MONEY, STATUS, HIVE, LISTINGS, ITEMS, OPPS, ORDERS, FLIPS, RIVALS, STAFF, REGIONS, EVASION, PAYROLL, TUNE, SEARCH }

    private final Logger log;
    private final Path configDir;
    private final Path ledgerPath;
    private final AutomationSessionController session;

    /**
     * Listings actually up, as the server last reported them, falling back to
     * the ledger's own count when there is no fresh answer.
     *
     * <p>The panel in the game and the embed in Discord were reading different
     * numbers off the same bot - eighty-six against sixty-four - because one
     * had been moved onto the server's count and the other still totted up the
     * ledger. Two screens disagreeing about the same thing is worse than
     * either being wrong, so both take it from here now.
     */
    private int listedNow(AutomationSessionController.SessionSnapshot s) {
        int fromServer = session.serverListedSlots();
        return fromServer >= 0 ? fromServer : s.openListings();
    }
    private volatile DiscordConfig config;
    private volatile DiscordRest rest;
    private volatile DiscordGateway gateway;
    private volatile String applicationId = "";
    private volatile String status = "not configured";
    private volatile Page page = Page.MONEY;
    /** The Items page: which stretch of time, in what order, and best or worst first. */
    private volatile dev.doughbay.fabric.ItemBoard.Range itemsRange = dev.doughbay.fabric.ItemBoard.Range.DAY;
    private volatile dev.doughbay.fabric.ItemBoard.Sort itemsSort = dev.doughbay.fabric.ItemBoard.Sort.PROFIT;
    private volatile boolean itemsBest = true;
    /**
     * Whose figures the pages show: empty is the whole hive, otherwise one
     * account. Only meaningful with more than one client trading, and the hive
     * is the default because the first question in multi mode is what the pair
     * made together.
     */
    private volatile String account = "";
    private volatile String selectedRegion="asia";
    /** The newest hive-control reply already shown, so each is shown once. */
    private volatile long controlRepliesSeenAt = System.currentTimeMillis();
    private volatile String tuneGroup = "";
    private volatile String tuneKey = "";
    /** The entry of the player's own price list the Flips page is working on. */
    private volatile String flipKey = "";
    private volatile String searchTerm = "";
    private volatile String notice = "";
    private volatile long noticeAt;
    private final AtomicBoolean publishing = new AtomicBoolean();
    private volatile boolean refreshNow;
    private volatile String presenceText = "";
    private volatile String saleFlash = "";
    private volatile long saleFlashAt;

    private DiscordBridge(Logger log, Path configDir, Path ledgerPath, AutomationSessionController session) {
        this.log = log;
        this.configDir = configDir;
        this.ledgerPath = ledgerPath;
        this.session = session;
        this.config = DiscordConfig.load(configDir);
    }

    public static DiscordBridge start(Logger log, Path configDir, Path ledgerPath, AutomationSessionController session) {
        DiscordBridge b = new DiscordBridge(log, configDir, ledgerPath, session);
        b.connect();
        Thread t = new Thread(b::refreshLoop, "doughbay-discord");
        t.setDaemon(true);
        t.start();
        return b;
    }

    /**
     * The same panel with no Discord behind it. The local web page draws its
     * pages and presses its buttons, and keeps its own view: which page is up
     * here says nothing about which is up in the Discord channel.
     */
    public static DiscordBridge panelOnly(Logger log, Path configDir, Path ledgerPath, AutomationSessionController session) {
        return new DiscordBridge(log, configDir, ledgerPath, session);
    }

    /**
     * An item's picture as a PNG, for the web panel's own pages: the flat
     * texture the panel's pictures are drawn with. Null when the game has
     * none, or has not read it within half a second of first being asked; the
     * next ask finds it.
     */
    public static byte[] itemPng(String itemId) {
        java.awt.image.BufferedImage image = ItemTextures.get(itemId);
        for (int i = 0; image == null && i < 20 && ItemTextures.pending(itemId); i++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            image = ItemTextures.get(itemId);
        }
        if (image == null) return null;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** The page on view, as the picture Discord would be sent. */
    public synchronized byte[] panelPng() throws Exception {
        if (page == Page.REGIONS) return dev.doughbay.fabric.RegionMapImage.render(dev.doughbay.fabric.RegionMapReport.snapshot());
        return StatsImage.render(renderedPage());
    }

    /** What is under the picture: the page's name, the last reply, and the controls in the shape Discord takes them. */
    public synchronized String panelState() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("page", label(page));
        root.put("notice", notice.isBlank() || System.currentTimeMillis() - noticeAt > 120_000 ? "" : notice);
        root.set("components", components());
        if (page == Page.MONEY) root.set("report", PanelReadModel.report(DiscordPrivacy.page(renderedPage())));
        var current = PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account());
        root.set("activity", PanelReadModel.activity(ledgerPath, current, account, System.currentTimeMillis()));
        // What the HUD in game shows across its top, for the page to show across its own.
        AutomationSessionController.SessionSnapshot s = session.snapshot();
        MarketWatcher watcher = DoughBayClient.watcher();
        MarketWatcher.Snapshot m = watcher == null ? null : watcher.snapshot();
        Viewed v = viewed(s, m == null ? 0 : dev.doughbay.fabric.SidebarBalance.best(m.accountBalance()));
        ObjectNode hud = root.putObject("hud");
        hud.put("state", account.isBlank() && !v.live() ? "hive" : v.state());
        hud.put("now", v.live() ? (s.detail() == null ? "" : s.detail())
                : account.isBlank() ? "Combined Hive view; choose one account for its records." : "Selected account: " + v.state());
        hud.put("listed", PanelReadModel.listed(ledgerPath, current, account));
        var capacity = dev.doughbay.fabric.OrderMetrics.capacity(capacityAccounts(), System.currentTimeMillis());
        hud.put("slots", capacity.known() == capacity.accounts() ? capacity.total() : -1);
        hud.put("slotCoverage", capacity.known() + "/" + capacity.accounts() + " current accounts reporting capacity");
        hud.put("accountCoverage", current.size() + " current · " + Math.max(0, panelAccounts().size()-current.size()) + " historical");
        hud.put("bank", v.bank() < 0 ? "unknown" : money(v.bank()));
        hud.put("trades", v.trades());
        hud.put("who", v.label());
        hud.put("clock", CLOCK.format(Instant.now()));
        Tuning.Setting st = page == Page.TUNE ? setting(tuneKey) : null;
        if (st != null) {
            ObjectNode set = root.putObject("setting");
            set.put("label", st.label());
            set.put("description", st.description());
            set.put("value", st.format(Tuning.get(st.key())));
        }
        // Through the same filter the Discord message goes through, so a hidden name is hidden here too.
        return DiscordPrivacy.payload(root.toString());
    }

    /** A button pressed or a choice made, by the id the control carries. */
    public void panelPress(String customId, String value) {
        if (customId == null || customId.isBlank()) return;
        ObjectNode d = MAPPER.createObjectNode();
        d.putObject("data").putArray("values").add(value == null ? "" : value);
        String reply = handleComponent(d, customId);
        if (reply != null) note(reply);
    }

    /** The two controls that ask for typing: an item to search for, and a value for the setting on view. */
    public void panelText(String customId, String text) {
        String modal = "ctl:search".equals(customId) ? "modal:search"
                : "tune:set".equals(customId) && setting(tuneKey) != null ? "modal:set:" + tuneKey : null;
        if (modal == null) return;
        ObjectNode d = MAPPER.createObjectNode();
        d.putObject("data").putArray("components").addObject().putArray("components").addObject()
                .put("value", text == null ? "" : text);
        handleModal(d, modal);
    }

    /** A sale to show under the bot's name for the next half minute. */
    public void noteSale(String itemKey, int quantity, long salePrice, double profit) {
        saleFlash = "Sold " + item(itemKey) + " x" + quantity + " for " + money(salePrice)
                + " (" + signed((long) profit) + ")";
        saleFlashAt = System.currentTimeMillis();
        refreshNow = true;
    }

    public String status() {
        DiscordGateway g = gateway;
        return g == null ? status : g.status() + " · " + status;
    }

    public DiscordConfig config() {
        return config;
    }

    /** Saves and applies a new configuration from the screen. */
    public synchronized void reconfigure(DiscordConfig c) {
        try {
            c.save(configDir);
        } catch (Exception e) {
            status = "could not save discord.json: " + e.getMessage();
            return;
        }
        DiscordGateway old = gateway;
        if (old != null) old.stop();
        gateway = null;
        config = c;
        connect();
        refreshNow = true;
    }

    private synchronized void connect() {
        DiscordConfig c = config;
        if (!c.complete()) {
            status = "not configured";
            return;
        }
        rest = new DiscordRest(c.token());
        gateway = new DiscordGateway(log, c.token(), rest, this::onInteraction, appId -> {
            applicationId = appId;
            refreshNow = true;
        });
        gateway.start();
        status = "starting";
    }

    private long feedPostedAt;
    private long staffCheckedAt;

    /** The elected bot host delivers staff escapes from every client's shared ledger. */
    private void postStaffAlerts(long now) {
        if (now - staffCheckedAt < 2_000L) return;
        staffCheckedAt = now;
        DiscordConfig c = config;
        if (c == null || !c.complete() || !c.hasFeed()) return;
        try (Database db = new Database(ledgerPath)) {
            if (deliverAlerts(db.connection(), c,
                    (channel, payload) -> rest.createMessage(channel, payload, null), now)) refreshNow = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Leave the record pending so a later attempt or another host can deliver it.
            log.warn("GoNuts notification pending: {}", e.toString());
        }
    }

    @FunctionalInterface
    interface AlertSender {
        String post(String channel, String payload) throws Exception;
    }

    /** Durable notifications, separate from the one editable control-panel message. */
    static boolean deliverAlerts(java.sql.Connection connection, DiscordConfig c,
                                 AlertSender sender, long now) throws Exception {
        if (c == null || !c.complete() || !c.hasFeed()) return false;
        boolean delivered = false;
        var pending = dev.doughbay.storage.StaffWatchAlerts.pending(connection);
        for (var alert : pending) {
            var body = MAPPER.createObjectNode();
            body.putObject("allowed_mentions").putArray("parse");
            var embed = body.putArray("embeds").addObject();
            embed.put("title", dev.doughbay.storage.StaffWatchAlerts.title(alert.action()));
            embed.put("color", 0xFF858C);
            embed.put("timestamp", Instant.ofEpochMilli(alert.at()).toString());
            embed.put("description", DiscordFeed.clean("Client: " + alert.client()) + "\n"
                    + DiscordFeed.clean("Staff: " + alert.who()) + "\n" + DiscordFeed.clean(alert.action()));
            String posted = sender.post(c.feedChannelId(),
                    MAPPER.writeValueAsString(body));
            if (posted == null || posted.isBlank()) break;
            dev.doughbay.storage.StaffWatchAlerts.delivered(connection, alert.id(), now);
            delivered = true;
        }
        for (var alert : dev.doughbay.storage.ConnectionAlerts.pending(connection)) {
            var body = MAPPER.createObjectNode();
            body.putObject("allowed_mentions").putArray("parse");
            var embed = body.putArray("embeds").addObject();
            embed.put("title", "GoNuts — CONNECTION LOST");
            embed.put("color", 0xFFA85C);
            embed.put("timestamp", Instant.ofEpochMilli(alert.at()).toString());
            String description = DiscordFeed.clean("Client: " + alert.client()) + "\n"
                    + DiscordFeed.clean(alert.action());
            embed.put("description", description.length() > 4000 ? description.substring(0, 4000) : description);
            String posted = sender.post(c.feedChannelId(),
                    MAPPER.writeValueAsString(body));
            if (posted == null || posted.isBlank()) break;
            dev.doughbay.storage.ConnectionAlerts.delivered(connection, alert.id(), now);
            delivered = true;
        }
        return delivered;
    }

    /** One message of queued activity lines to the feed channel, at most every five seconds. */
    private void postFeed(long now) {
        DiscordConfig c = config;
        DiscordFeed.setEnabled(c != null && c.hasFeed());
        if (c == null || !c.hasFeed() || now - feedPostedAt < FEED_MILLIS) return;
        String text = DiscordFeed.nextMessage();
        if (text.isEmpty()) return;
        feedPostedAt = now;
        try {
            var body = MAPPER.createObjectNode();
            body.put("content", text);
            body.putObject("allowed_mentions").putArray("parse");
            rest.createMessage(c.feedChannelId(), MAPPER.writeValueAsString(body), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // A feed that cannot post must never take the panel down with it.
            log.warn("DoughBay Discord feed: {}", e.toString());
        }
    }

    private void refreshLoop() {
        long last = 0;
        while (true) {
            try {
                Thread.sleep(1000);
                long now = System.currentTimeMillis();
                if (!hostCheck()) continue;
                if (gateway == null || applicationId.isEmpty()) continue;
                updatePresence(now);
                postStaffAlerts(now);
                postFeed(now);
                if (refreshNow || now - last >= REFRESH_MILLIS) {
                    refreshNow = false;
                    last = now;
                    publish();
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                status = "publish failed: " + e.getMessage();
                log.warn("DoughBay Discord: {}", e.toString());
            }
        }
    }

    private volatile long hostCheckedAt;
    private volatile boolean hosting = true;

    /**
     * Connects the bot only on the client that owns it.
     *
     * <p>Clients in a hive share a configuration folder, so they all read the
     * same token, channel and message id. Left alone they would all connect,
     * all edit the same message half a minute apart, and all answer the same
     * button press, two of them getting an error for their trouble. One hosts;
     * the others hold their peace and take over if it goes away.
     *
     * @return whether this client should be publishing
     */
    private boolean hostCheck() {
        long now = System.currentTimeMillis();
        if (now - hostCheckedAt > 20_000) {
            hostCheckedAt = now;
            boolean want = DoughBayClient.hostsDiscord();
            if (want != hosting) {
                hosting = want;
                if (want) {
                    log.info("DoughBay Discord: this client is now hosting the panel");
                    connect();
                    refreshNow = true;
                } else {
                    log.info("DoughBay Discord: another client hosts the panel; standing down");
                    DiscordGateway g = gateway;
                    if (g != null) g.stop();
                    gateway = null;
                    status = "standing by; another client hosts the panel";
                }
            }
        }
        return hosting;
    }

    /** Discord's "playing" line: what the trader is doing right now. */
    private void updatePresence(long now) {
        DiscordGateway g = gateway;
        if (g == null) return;
        String text = presenceLine(now);
        if (text.equals(presenceText)) return;
        presenceText = text;
        // 3 is "Watching"; the trader watches the auction house for a living.
        g.presence(text, 3);
    }

    private String presenceLine(long now) {
        if (!saleFlash.isBlank() && now - saleFlashAt < 30_000) return saleFlash;
        AutomationSessionController.SessionSnapshot s = session.snapshot();
        MarketWatcher watcher = DoughBayClient.watcher();
        MarketWatcher.Snapshot m = watcher == null ? null : watcher.snapshot();
        String bank = m == null ? "" : " · " + money(dev.doughbay.fabric.SidebarBalance.best(m.accountBalance()));
        String slots = " · " + listedNow(s) + "/" + (int) Tuning.get("slots.max");
        String what = switch (s.state()) {
            case STOPPED -> "stopped";
            case PAUSED -> "paused: " + shorten(s.detail(), 60);
            case SCANNING -> "the auction house";
            case BUYING -> "a buy: " + shorten(detailItem(s), 40);
            case PREPARING_LIST, LISTING, MONITORING -> "a listing: " + shorten(detailItem(s), 40);
            case REPRICING -> "a pull-back: " + shorten(detailItem(s), 40);
            case READING_ORDERS -> "the order house";
            case BID_DESK -> "the bid desk: " + shorten(s.detail().replace("Bid desk: ", ""), 45);
            case AUDITING_SLOTS -> "the slot check";
            case COOLDOWN -> "the cooldown";
            default -> shorten(s.detail(), 60);
        };
        return what + (s.state() == AutomationSessionController.State.STOPPED ? "" : slots + bank);
    }

    private static String detailItem(AutomationSessionController.SessionSnapshot s) {
        if (s.position() != null) return item(s.position().itemKey()) + " x" + s.position().quantity();
        return shorten(s.detail(), 40);
    }

    private static String shorten(String text, int max) {
        String t = text == null ? "" : text.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    // ---------------------------------------------------------------- publishing

    private void publish() throws Exception {
        if (!publishing.compareAndSet(false, true)) return;
        try {
            StatsImage.Page p = renderedPage();
            byte[] png = page == Page.REGIONS
                    ? dev.doughbay.fabric.RegionMapImage.render(dev.doughbay.fabric.RegionMapReport.snapshot()) : StatsImage.render(p);
            String payload = payload(p);
            DiscordConfig c = config;
            boolean ok = !c.messageId().isBlank() && rest.editMessage(c.channelId(), c.messageId(), payload, png);
            if (!ok) {
                String id = rest.createMessage(c.channelId(), payload, png);
                config = c.withMessageId(id);
                config.save(configDir);
            }
            status = "live · " + page.name().toLowerCase(Locale.ROOT) + " · " + CLOCK.format(Instant.now());
        } finally {
            publishing.set(false);
        }
    }

    /**
     * The accounts whose heads go in the header: the one being looked at, or
     * every one of them when the view is the hive.
     */
    /**
     * The face the robot in the header wears: the same reading of the session
     * the mascot on the game's own screen makes, so the panel and the screen
     * never disagree about the mood.
     */
    private String mascotFace() {
        AutomationSessionController.SessionSnapshot s = session.snapshot();
        if (!saleFlash.isBlank() && System.currentTimeMillis() - saleFlashAt < 30_000) return "sale";
        return switch (s.state()) {
            case STOPPED -> "sleep";
            case PAUSED -> "paused";
            case SCANNING, BUYING -> "watch";
            case COOLDOWN, MONITORING -> "idle";
            default -> "work";
        };
    }

    private List<String> faces() {
        if (!account.isBlank()) return List.of(account);
        List<String> hive = PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account());
        if (hive.size() > 1) return hive;
        String me = DoughBayClient.account();
        return me.isBlank() ? List.of() : List.of(me);
    }

    private String payload(StatsImage.Page p) {
        ObjectNode root = MAPPER.createObjectNode();
        // The picture is the message. An image inside an embed is shown at
        // about 550 pixels wide however large it is drawn and whatever the
        // reader sets their scaling to, which made the page unreadable on a
        // big screen; the same file posted as a plain attachment is shown at
        // its own size and grows with the window.
        StringBuilder head = new StringBuilder("**GoNuts · ").append(p.title()).append("**");
        String desc = notice.isBlank() || System.currentTimeMillis() - noticeAt > 120_000 ? "" : notice;
        if (!desc.isBlank()) head.append('\n').append(desc);
        if (!p.footer().isBlank()) head.append("\n-# ").append(p.footer());
        root.put("content", head.toString());
        ArrayNode attachments = root.putArray("attachments");
        ObjectNode att = attachments.addObject();
        att.put("id", 0);
        att.put("filename", "stats.png");
        root.set("components", components());
        return root.toString();
    }

    private ArrayNode components() {
        ArrayNode rows = MAPPER.createArrayNode();
        // One dropdown rather than two rows of buttons. Discord takes five
        // buttons to a row and rejects the whole message at six, so every new
        // page used to mean rearranging the rows; a select holds twenty-five
        // and takes the page count off the critical path for good.
        ArrayNode r1 = row(rows);
        ObjectNode picker = select(r1, "page:pick", "Page - " + label(page));
        ArrayNode opts = picker.putArray("options");
        for (Page pg : Page.values()) {
            if (pg == Page.SEARCH) continue;   // reached by the Search button, not a page of its own
            // Keep setup discoverable before a second account is recognized.
            option(opts, label(pg), pg.name(), pg == page);
        }
        // Which account the figures are for. Offered only when there is more
        // than one, so a single client's panel is exactly what it always was.
        List<String> accounts = panelAccounts();
        if (accounts.size() > 1 && page != Page.STAFF) {
            ArrayNode ra = row(rows);
            ObjectNode apick = select(ra, "acct:pick",
                    "Account - " + (account.isBlank() ? "all" : account));
            ArrayNode aopts = apick.putArray("options");
            option(aopts, "All accounts", "*", account.isBlank());
            for (String name : accounts) {
                boolean current = PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account()).stream().anyMatch(name::equalsIgnoreCase);
                option(aopts, name.equalsIgnoreCase(DoughBayClient.account()) ? name + " (this client)" : name + (current ? "" : " (historical)"),
                        name, name.equalsIgnoreCase(account));
            }
        }
        ArrayNode r2 = row(rows);
        if(page==Page.FLIPS)button(r2,"flips:only",dev.doughbay.fabric.FlipsMode.only()?"Flips only: ON":"Flips only: OFF",2,false);
        else button(r2, "ctl:search", "Search", 2, false);
        if(page==Page.REGIONS)button(r2,"regions:switch","Switch selected account",1,
                account.isBlank() && panelAccounts().size()>1);
        if (page == Page.STAFF) button(r2,"staff:response",
                dev.doughbay.fabric.staffwatch.WatchConfig.get().regionSwitch?"Response: Switch region":"Response: Disconnect",2,false);
        if (page == Page.FLIPS) {
            // On the Search row, which has room: the page's own row goes to
            // the list to pick from, and Discord takes five rows and no more.
            boolean none = flip() == null;
            button(r2, "flips:sell", "Sell price", 1, none);
            button(r2, "flips:buy", "Buy price", 1, none);
            button(r2, "flips:add", "Add item", 3, false);
            button(r2, "flips:remove", "Remove", 4, none);
        }
        // Discord allows five buttons to a row and silently rejects the whole
        // message with "Invalid Form Body" at six, so Start and Stop get their
        // own row rather than being crammed onto the end of the pages.
        ArrayNode controls = row(rows);
        AutomationSessionController.SessionSnapshot s = session.snapshot();
        boolean running = s.state() != AutomationSessionController.State.STOPPED && s.state() != AutomationSessionController.State.PAUSED;
        // Start and Stop reach this client's own session and nobody else's,
        // so they are held back while another account's figures are on screen
        // rather than quietly stopping the wrong trader.
        boolean mine = account.isBlank() || account.equalsIgnoreCase(DoughBayClient.account());
        boolean everyone = account.isBlank() && panelAccounts().size() > 1;
        if (everyone) {
            // "All accounts" on screen means all of them: this client
            // directly, the others through the ledger. Both stay live, since
            // one may be running while another is parked.
            button(controls, "ctl:start", "Start all", 3, false);
            button(controls, "ctl:stop", "Stop all", 4, false);
        } else if (mine) {
            button(controls, "ctl:start", s.state() == AutomationSessionController.State.PAUSED ? "Resume" : "Start", 3, running);
            button(controls, "ctl:stop", "Stop", 4, !running && s.state() != AutomationSessionController.State.PAUSED);
        } else {
            // Another account is on screen: the buttons drive that client,
            // through the shared ledger, and say so on their face. Its last
            // checkpointed state picks the label; the client itself has the
            // final word and answers back.
            dev.doughbay.fabric.HiveControl ctl = DoughBayClient.hiveControl();
            String theirs = ctl == null ? "" : ctl.stateOf(account);
            boolean theirsPaused = "PAUSED".equals(theirs);
            boolean theirsStopped = theirs.isBlank() || "STOPPED".equals(theirs);
            button(controls, "ctl:start", (theirsPaused ? "Resume " : "Start ") + account, 3, ctl == null || (!theirsPaused && !theirsStopped));
            button(controls, "ctl:stop", "Stop " + account, 4, ctl == null || theirsStopped);
        }
        // Replies to orders sent to the other clients, as they come in.
        dev.doughbay.fabric.HiveControl replies = DoughBayClient.hiveControl();
        if (replies != null && panelAccounts().size() > 1) {
            for (dev.doughbay.fabric.HiveControl.Result r : replies.repliesSince(controlRepliesSeenAt)) {
                controlRepliesSeenAt = Math.max(controlRepliesSeenAt, r.doneAt());
                if (!r.target().equalsIgnoreCase(DoughBayClient.account())) note(r.target() + ": " + r.result());
            }
        }
        if (page == Page.ITEMS) {
            // Three buttons that each step to the next choice: a dropdown
            // would take a row apiece, and there is one row to spare.
            ArrayNode ri = row(rows);
            button(ri, "items:range", "Range: " + itemsRange.label, 2, false);
            button(ri, "items:sort", "Sort: " + itemsSort.label, 2, false);
            button(ri, "items:flip", itemsBest ? "Best first" : "Worst first", 2, false);
        }
        if (page == Page.FLIPS && !dev.doughbay.fabric.OwnPrices.all().isEmpty()) {
            dev.doughbay.fabric.OwnPrices.Entry chosen = flip();
            ObjectNode fsel = select(row(rows), "flips:key", "Item on your list");
            ArrayNode fopts = fsel.putArray("options");
            int n = 0;
            for (dev.doughbay.fabric.OwnPrices.Entry e : dev.doughbay.fabric.OwnPrices.all()) {
                if (n++ >= 25) break;
                option(fopts, item(e.itemId()) + " · buy " + money(e.buy()) + " · sell " + money(e.sell()),
                        e.itemId(), chosen != null && chosen.itemId().equals(e.itemId()));
            }
        }
        if(page==Page.REGIONS) {
            ArrayNode rr=row(rows);
            ObjectNode rp=select(rr,"regions:pick","Destination: "+selectedRegion);
            ArrayNode ro=rp.putArray("options");
            for(String region:dev.doughbay.fabric.staffwatch.ManualRegionFlow.DESTINATIONS)
                option(ro,region,region,region.equals(selectedRegion));
        }
        if (page == Page.TUNE) {
            List<String> groups = new ArrayList<>(Tuning.groups());
            if (tuneGroup.isBlank() && !groups.isEmpty()) tuneGroup = groups.get(0);
            ArrayNode r3 = row(rows);
            ObjectNode gsel = select(r3, "tune:group", "Settings group");
            ArrayNode gopts = gsel.putArray("options");
            for (String g : groups) option(gopts, g, g, g.equals(tuneGroup));
            option(gopts, "Presets", "#presets", "#presets".equals(tuneGroup));
            ArrayNode r4 = row(rows);
            if ("#presets".equals(tuneGroup)) {
                ObjectNode psel = select(r4, "tune:preset", "Apply a preset");
                ArrayNode popts = psel.putArray("options");
                for (String name : Tuning.PRESETS.keySet()) option(popts, name, name, false);
            } else {
                ObjectNode ksel = select(r4, "tune:key", "Setting");
                ArrayNode kopts = ksel.putArray("options");
                int n = 0;
                for (Tuning.Setting st : Tuning.SETTINGS) {
                    if (!st.group().equals(tuneGroup) || n >= 25) continue;
                    option(kopts, st.label() + " = " + st.format(Tuning.get(st.key())), st.key(), st.key().equals(tuneKey));
                    n++;
                }
                Tuning.Setting current = setting(tuneKey);
                ArrayNode r5 = row(rows);
                boolean none = current == null;
                boolean toggle = current != null && current.kind() == Tuning.Kind.TOGGLE;
                button(r5, "tune:toggle", "Toggle", 1, none || !toggle);
                button(r5, "tune:set", "Set value", 1, none || toggle);
                button(r5, "tune:dec", "−", 2, none || toggle);
                button(r5, "tune:inc", "+", 2, none || toggle);
                button(r5, "tune:reset", "Reset", 2, none);
            }
        }
        return rows;
    }

    private static ArrayNode row(ArrayNode rows) {
        ObjectNode r = rows.addObject();
        r.put("type", 1);
        return r.putArray("components");
    }

    private static void button(ArrayNode row, String id, String label, int style, boolean disabled) {
        ObjectNode b = row.addObject();
        b.put("type", 2);
        b.put("style", style);
        b.put("label", label);
        b.put("custom_id", id);
        if (disabled) b.put("disabled", true);
    }

    private static ObjectNode select(ArrayNode row, String id, String placeholder) {
        ObjectNode s = row.addObject();
        s.put("type", 3);
        s.put("custom_id", id);
        s.put("placeholder", placeholder);
        return s;
    }

    private static void option(ArrayNode opts, String label, String value, boolean selected) {
        ObjectNode o = opts.addObject();
        o.put("label", label.length() > 100 ? label.substring(0, 100) : label);
        o.put("value", value);
        if (selected) o.put("default", true);
    }

    private static String label(Page p) {
        return switch (p) {
            case MONEY -> "Money";
            case STATUS -> "Status";
            case HIVE -> "Hive";
            case LISTINGS -> "Listings";
            case ITEMS -> "Items";
            case OPPS -> "Opps";
            case ORDERS -> "Orders";
            case RIVALS -> "Rivals";
            case EVASION -> "Evasion";
            case STAFF -> "Staff Watch";
            case REGIONS -> "Region Map";
            case PAYROLL -> "Payroll";
            case TUNE -> "Tune";
            case FLIPS -> "Flips";
            case SEARCH -> "Search";
        };
    }

    // ---------------------------------------------------------------- interactions

    private void onInteraction(JsonNode d) {
        String id = d.path("id").asText("");
        String token = d.path("token").asText("");
        int type = d.path("type").asInt(0);
        String user = d.path("member").path("user").path("id").asText(d.path("user").path("id").asText(""));
        try {
            if (!user.equals(config.operatorId())) {
                rest.respond(id, token, ephemeral("This panel answers only to its operator."));
                return;
            }
            String customId = d.path("data").path("custom_id").asText("");
            if (type == 5) {
                handleModal(d, customId);
                rest.respond(id, token, "{\"type\":6}");
                refreshNow = true;
                return;
            }
            if (type != 3) return;
            if (customId.equals("ctl:search")) {
                rest.respond(id, token, modal("modal:search", "Search the markets", "item", "Item, for example golden apple", ""));
                return;
            }
            if (customId.equals("flips:sell") || customId.equals("flips:buy")) {
                dev.doughbay.fabric.OwnPrices.Entry e = flip();
                if (e == null) {
                    rest.respond(id, token, ephemeral("Put an item on your Flips list first."));
                    return;
                }
                boolean sell = customId.equals("flips:sell");
                rest.respond(id, token, modal("modal:flips:" + (sell ? "sell:" : "buy:") + e.itemId(),
                        (sell ? "Sell price: " : "Buy price: ") + item(e.itemId()), "value",
                        sell ? "What one sells for on the auction house" : "The most to pay for one in an order",
                        String.valueOf(sell ? e.sell() : e.buy())));
                return;
            }
            if (customId.equals("flips:add")) {
                rest.respond(id, token, modal("modal:flips:add", "Add to your Flips list", "value",
                        "Item, buy price, sell price (each)", ""));
                return;
            }
            if (customId.equals("tune:set")) {
                Tuning.Setting st = setting(tuneKey);
                if (st == null) {
                    rest.respond(id, token, ephemeral("Pick a setting first."));
                    return;
                }
                rest.respond(id, token, modal("modal:set:" + st.key(), st.label(),
                        "value", st.description().length() > 100 ? st.description().substring(0, 100) : st.description(),
                        st.format(Tuning.get(st.key()))));
                return;
            }
            String reply = handleComponent(d, customId);
            rest.respond(id, token, "{\"type\":6}");
            if (reply != null) note(reply);
            refreshNow = true;
        } catch (Exception e) {
            log.warn("DoughBay Discord interaction: {}", e.toString());
        }
    }

    private synchronized String handleComponent(JsonNode d, String customId) {
        if (customId.equals("staff:response")) {
            String mode=dev.doughbay.fabric.staffwatch.WatchConfig.get().regionSwitch?"disconnect":"switch";
            String result=dev.doughbay.fabric.WebPages.change("staff",java.util.Map.of("action","response","mode",mode));
            try { return MAPPER.readTree(result).path("note").asText("Staff Watch response updated"); }
            catch(java.io.IOException e) { return "Could not update Staff Watch response"; }
        }
        JsonNode values = d.path("data").path("values");
        String value = values.isArray() && values.size() > 0 ? values.get(0).asText("") : "";
        if(customId.equals("regions:pick")) {
            if(dev.doughbay.fabric.staffwatch.ManualRegionFlow.DESTINATIONS.contains(value))selectedRegion=value;
            return "Destination: "+selectedRegion+". Press Switch selected account to move.";
        }
        if(customId.equals("regions:switch")) {
            String target=account.isBlank() && panelAccounts().size()<2?DoughBayClient.account():account;
            String result=dev.doughbay.fabric.WebPages.change("regions",java.util.Map.of("account",target,"destination",selectedRegion));
            try{return MAPPER.readTree(result).path("note").asText("Region request completed");}
            catch(java.io.IOException e){return "Could not request region switch";}
        }
        if (customId.equals("acct:pick")) {
            account = "*".equals(value) ? "" : value;
            return account.isBlank() ? "Showing every account" : "Showing " + account;
        }
        if (customId.equals("page:pick")) {
            if (!value.isBlank()) page = Page.valueOf(value);
            return null;
        }
        if (customId.startsWith("page:")) {
            page = Page.valueOf(customId.substring(5));
            return null;
        }
        switch (customId) {
            case "ctl:start" -> control(dev.doughbay.fabric.HiveControl.START);
            case "ctl:stop" -> control(dev.doughbay.fabric.HiveControl.STOP);
            case "items:range" -> itemsRange = itemsRange.next();
            case "items:sort" -> itemsSort = itemsSort.next();
            case "items:flip" -> itemsBest = !itemsBest;
            case "tune:group" -> {
                tuneGroup = value;
                tuneKey = "";
            }
            case "tune:key" -> tuneKey = value;
            case "flips:only" -> {
                String result=dev.doughbay.fabric.WebPages.change("setting",java.util.Map.of("key","prices.only","toggle","1"));
                try{return MAPPER.readTree(result).path("note").asText("Flips mode updated");}
                catch(java.io.IOException e){return "Could not update Flips mode";}
            }
            case "flips:key" -> flipKey = value;
            case "flips:remove" -> {
                dev.doughbay.fabric.OwnPrices.Entry e = flip();
                if (e != null) flipEdit(list -> dev.doughbay.fabric.FlipEdits.remove(list, e.itemId()));
            }
            case "tune:preset" -> onClient(() -> {
                int changed = Tuning.applyPreset(value);
                note("Preset " + value + " applied: " + changed + " setting(s) changed");
            });
            case "tune:toggle" -> adjust(st -> Tuning.get(st.key()) >= 0.5 ? 0 : 1);
            case "tune:dec" -> adjust(st -> Tuning.get(st.key()) - step(st));
            case "tune:inc" -> adjust(st -> Tuning.get(st.key()) + step(st));
            case "tune:reset" -> {
                Tuning.Setting st = setting(tuneKey);
                if (st != null) onClient(() -> {
                    Tuning.reset(st.key());
                    note(st.label() + " reset to " + st.format(Tuning.get(st.key())));
                });
            }
            default -> { }
        }
        return null;
    }

    private void handleModal(JsonNode d, String customId) {
        String input = "";
        for (JsonNode row : d.path("data").path("components")) {
            for (JsonNode comp : row.path("components")) {
                if (comp.has("value")) input = comp.get("value").asText("");
            }
        }
        final String text = input.strip();
        if (customId.equals("modal:search")) {
            searchTerm = text.toLowerCase(Locale.ROOT).replace(' ', '_');
            page = Page.SEARCH;
            return;
        }
        if (customId.startsWith("modal:flips:sell:")) {
            String which = customId.substring("modal:flips:sell:".length());
            flipEdit(list -> dev.doughbay.fabric.FlipEdits.sell(list, which, text));
            return;
        }
        if (customId.startsWith("modal:flips:buy:")) {
            String which = customId.substring("modal:flips:buy:".length());
            flipEdit(list -> dev.doughbay.fabric.FlipEdits.buy(list, which, text));
            return;
        }
        if (customId.equals("modal:flips:add")) {
            flipEdit(list -> dev.doughbay.fabric.FlipEdits.add(list, text));
            return;
        }
        if (customId.startsWith("modal:set:")) {
            Tuning.Setting st = setting(customId.substring("modal:set:".length()));
            if (st == null) return;
            try {
                double v = Double.parseDouble(text.replace("%", "").replace(",", "").strip());
                onClient(() -> {
                    double applied = Tuning.set(st.key(), v);
                    note(st.label() + " set to " + st.format(applied));
                });
            } catch (NumberFormatException e) {
                note("Not a number: " + text);
            }
        }
    }

    private interface Adjuster {
        double apply(Tuning.Setting st);
    }

    /** The entry the Flips page is working on: the one picked, or the first when none has been. */
    private dev.doughbay.fabric.OwnPrices.Entry flip() {
        dev.doughbay.fabric.OwnPrices.Entry picked = flipKey.isBlank() ? null : dev.doughbay.fabric.OwnPrices.of(flipKey);
        if (picked != null) return picked;
        for (dev.doughbay.fabric.OwnPrices.Entry first : dev.doughbay.fabric.OwnPrices.all()) return first;
        return null;
    }

    /** One change to the player's price list, made on the game thread and answered on the page. */
    private void flipEdit(java.util.function.Function<String, dev.doughbay.fabric.FlipEdits.Result> edit) {
        onClient(() -> {
            dev.doughbay.fabric.FlipEdits.Result r = edit.apply(Tuning.text(dev.doughbay.fabric.OwnPrices.KEY));
            if (r.ok()) Tuning.setText(dev.doughbay.fabric.OwnPrices.KEY, r.listText());
            note(r.message());
            refreshNow = true;
        });
    }

    private void adjust(Adjuster f) {
        Tuning.Setting st = setting(tuneKey);
        if (st == null) return;
        onClient(() -> {
            double applied = Tuning.set(st.key(), f.apply(st));
            note(st.label() + " → " + st.format(applied));
        });
    }

    private static double step(Tuning.Setting st) {
        double span = st.max() - st.min();
        if (st.kind() == Tuning.Kind.PERCENT) return span >= 50 ? 5 : 1;
        if (span <= 20) return 1;
        if (span <= 200) return 5;
        return Math.max(1, Math.floor(span / 40));
    }

    /**
     * Start, Resume or Stop for whichever account is on screen: this client's
     * own session directly, another client's through the shared ledger.
     */
    private void control(String command) {
        String verb = command.equals(dev.doughbay.fabric.HiveControl.STOP) ? "Stop" : "Start";
        dev.doughbay.fabric.HiveControl ctl = DoughBayClient.hiveControl();
        List<String> accounts = panelAccounts();
        if (account.isBlank() && accounts.size() > 1) {
            // Everyone: this client now, the rest by order.
            onClient(() -> note(DoughBayClient.account() + ": " + dev.doughbay.fabric.HiveControl.apply(session, command)));
            int sent = 0;
            for (String name : accounts) {
                if (name.equalsIgnoreCase(DoughBayClient.account())) continue;
                if (ctl != null && ctl.post(name, command, "operator")) sent++;
            }
            note("Sent " + verb + " to " + sent + " other client(s); their replies show here");
            refreshNow = true;
            return;
        }
        boolean mine = account.isBlank() || account.equalsIgnoreCase(DoughBayClient.account());
        if (mine) {
            onClient(() -> note(dev.doughbay.fabric.HiveControl.apply(session, command)));
            return;
        }
        if (ctl == null) {
            note("No hive to send that to");
            return;
        }
        note(ctl.post(account, command, "operator")
                ? "Sent " + verb + " to " + account + "; its reply shows here"
                : "Could not reach " + account);
        refreshNow = true;
    }

    private void onClient(Runnable r) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            Runnable action=()->{
                try { r.run(); } catch (Exception e) { note("Failed: " + e.getMessage()); }
                refreshNow=true;
            };
            if(mc.isSameThread())action.run();else mc.execute(action);
        }
    }

    private void note(String text) {
        notice = text;
        noticeAt = System.currentTimeMillis();
        log.info("DoughBay Discord: {}", text);
    }

    private static Tuning.Setting setting(String key) {
        if (key == null || key.isBlank()) return null;
        for (Tuning.Setting st : Tuning.SETTINGS) if (st.key().equals(key)) return st;
        return null;
    }

    private static String ephemeral(String text) {
        ObjectNode r = MAPPER.createObjectNode();
        r.put("type", 4);
        ObjectNode data = r.putObject("data");
        data.put("content", text);
        data.put("flags", 64);
        return r.toString();
    }

    private static String modal(String customId, String title, String field, String label, String value) {
        ObjectNode r = MAPPER.createObjectNode();
        r.put("type", 9);
        ObjectNode data = r.putObject("data");
        data.put("custom_id", customId);
        data.put("title", title.length() > 45 ? title.substring(0, 45) : title);
        ArrayNode comps = data.putArray("components");
        ObjectNode row = comps.addObject();
        row.put("type", 1);
        ObjectNode input = row.putArray("components").addObject();
        input.put("type", 4);
        input.put("custom_id", field);
        input.put("style", 1);
        input.put("label", label.length() > 45 ? label.substring(0, 45) : label);
        input.put("required", true);
        if (value != null && !value.isBlank()) input.put("value", value);
        return r.toString();
    }

    // ---------------------------------------------------------------- pages

    /**
     * The account the page is about, and the few figures that were being taken
     * from whichever client happens to be hosting the panel.
     *
     * <p>Picking an account changed the ledger queries but not the session:
     * the state in the subtitle, the bank beside it, the trade count and - most
     * visibly - the slot grid all came from the host's own controller, so both
     * accounts showed the host's slots however many each really had. Anything
     * the host cannot know first-hand comes out of the shared ledger instead.
     *
     * @param name    the account being shown, or empty for the whole hive
     * @param state   what that account's session last said it was doing
     * @param bank    its money, or the hive's together
     * @param trades  trades started in its session
     * @param listed  listings it has up
     * @param live    whether this is the client running the code, so the
     *                in-memory queue and history can be shown
     */
    private record Viewed(String name, String state, long bank, int trades, int listed, boolean live) {
        String label() {
            return name.isBlank() ? "all accounts" : name;
        }
    }

    /**
     * Reads whoever is being looked at. The host answers for itself from its
     * own controller, because that is fresher than the checkpoint it writes;
     * anybody else is read from the ledger, and the hive is added up.
     */
    private Viewed viewed(AutomationSessionController.SessionSnapshot s, long balance) {
        String me = DoughBayClient.account();
        boolean live = account.isBlank() ? panelAccounts().size() < 2 : account.equalsIgnoreCase(me);
        if (live) {
            return new Viewed(me, s.state().name(), balance, s.tradesStarted(), listedNow(s), true);
        }
        try (Database db = new Database(ledgerPath)) {
            long bank = bankInView(db, balance);
            int listed = openInView(db, s.openListings());
            if (account.isBlank()) {
                // The hive: every session's trades, and every account's slots.
                int trades = 0;
                try (PreparedStatement ps = db.connection().prepareStatement(
                        "SELECT COALESCE(SUM(trades_started),0) FROM automation_session_state WHERE client <> ''");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) trades = rs.getInt(1);
                }
                return new Viewed("", "HIVE", bank, trades, listed, false);
            }
            String state = "-";
            int trades = 0;
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT controller_state, trades_started FROM automation_session_state WHERE client=? COLLATE NOCASE")) {
                ps.setString(1, account);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        state = rs.getString(1);
                        trades = rs.getInt(2);
                    }
                }
            }
            return new Viewed(account, state, bank, trades, listed, false);
        } catch (Exception e) {
            return new Viewed(account, "UNAVAILABLE", -1, -1, -1, false);
        }
    }

    private synchronized StatsImage.Page renderedPage() {
        return buildPage().withFaces(faces()).withMascot(mascotFace());
    }

    private StatsImage.Page buildPage() {
        AutomationSessionController.SessionSnapshot s = session.snapshot();
        MarketWatcher watcher = DoughBayClient.watcher();
        MarketWatcher.Snapshot m = watcher == null ? null : watcher.snapshot();
        long balance = m == null ? 0 : dev.doughbay.fabric.SidebarBalance.best(m.accountBalance());
        String stateColor = switch (s.state()) {
            case PAUSED -> "gold";
            case STOPPED -> "red";
            default -> "green";
        };
        Viewed v = viewed(s, balance);
        int liveListed = PanelReadModel.listed(ledgerPath,
                PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account()), account);
        v = new Viewed(v.name(), v.state(), v.bank(), v.trades(), liveListed, v.live());
        // "SCANNING · 10:45 · bank $8.7m" on its own, as it always read; with a
        // hive, whose figures these are comes first, because that is the thing
        // it is now possible to be wrong about.
        String head = account.isBlank() && !v.live()
                ? "hive of " + PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account()).size()
                : v.state();
        String subtitle = v.label() + " · " + head + " · " + CLOCK.format(Instant.now()) + " · bank " + (v.bank() < 0 ? "unknown" : money(v.bank()));
        String footer = v.live() ? (s.detail().length() > 110 ? s.detail().substring(0, 110) : s.detail())
                : "Selected account scope. Missing or stale observations stay unknown; host values are not substituted.";
        // The slot grid belongs to whoever is being looked at, so one account's
        // allowance for one account and everybody's for the hive.
        var capacity = dev.doughbay.fabric.OrderMetrics.capacity(capacityAccounts(), System.currentTimeMillis());
        int slots = liveListed >= 0 && capacity.known() == capacity.accounts() ? capacity.total() : 0;
        return switch (page) {
            case MONEY -> moneyPage(s, v, stateColor, subtitle, footer, slots);
            case STATUS -> statusPage(s, v, stateColor, subtitle, footer);
            case HIVE -> hivePage(stateColor, subtitle, footer);
            case LISTINGS -> listingsPage(v, stateColor, subtitle, footer, slots);
            case ITEMS -> itemsPage(stateColor, subtitle, footer);
            case OPPS -> oppsPage(m, stateColor, subtitle, footer);
            case ORDERS -> ordersPage(stateColor, subtitle, footer);
            case RIVALS -> rivalsPage(stateColor, subtitle, footer);
            case EVASION -> evasionPage(stateColor, subtitle, footer);
            case STAFF -> staffPage();
            case REGIONS -> regionPage();
            case PAYROLL -> payrollPage(stateColor, subtitle, footer);
            case TUNE -> tunePage(stateColor, subtitle, footer);
            case FLIPS -> flipsPage(stateColor, subtitle, footer);
            case SEARCH -> searchPage(m, stateColor, subtitle, footer);
        };
    }

    /**
     * The account filter as a piece of SQL, or nothing at all.
     *
     * <p>Positions have carried an owner since the hive existed, so scoping a
     * page to one account is a where-clause rather than a second table. An
     * empty selection means the hive when multiple accounts are active, and
     * the current account otherwise. Old accounts may still have ledger rows.
     */
    private String scope() {
        return ledgerScope().sql();
    }

    private DiscordAccountScope ledgerScope() {
        return DiscordAccountScope.resolve(account, DoughBayClient.account(), panelAccounts().size());
    }

    /** Binds the account filter, if there is one, and answers the next free index. */
    private int bindScope(PreparedStatement ps, int index) throws Exception {
        return ledgerScope().bind(ps, index);
    }

    /** A statement with the account filter already bound as its first parameter. */
    private PreparedStatement scoped(Database db, String sql) throws Exception {
        PreparedStatement ps = db.connection().prepareStatement(sql);
        bindScope(ps, 1);
        return ps;
    }

    private record Window(int sales, long revenue, long profit, int bought, long paid) {
        /** Profit as a share of what came in; the number that says whether the flips are worth doing. */
        String margin() {
            return revenue > 0 ? String.format(Locale.ROOT, "%.1f%%", profit * 100.0 / revenue) : "-";
        }

        /** What payroll took out of that profit. */
        String payrollShare() {
            return profit > 0 ? String.format(Locale.ROOT, "%.0f%%", paid * 100.0 / profit) : "-";
        }
    }

    /**
     * Realized profit in each of the last N whole hours, oldest first, as the
     * CSV the panel's sparkline draws. One hour is one bar, so the row is the
     * "Profit 1h" figure as it stood at each hour back - where it is against
     * where it has been, which the single number cannot show.
     */
    private String hourlyProfitCsv(Database db, int hours) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COALESCE(SUM(realized_profit),0) FROM positions "
                        + "WHERE mode='REAL' AND status='SOLD' AND closed_at > ? AND closed_at <= ?" + scope())) {
            for (int i = hours - 1; i >= 0; i--) {
                long to = now - i * 3_600_000L;
                ps.setLong(1, to - 3_600_000L);
                ps.setLong(2, to);
                bindScope(ps, 3);
                long p = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) p = (long) rs.getDouble(1);
                }
                if (sb.length() > 0) sb.append(',');
                sb.append(p);
            }
        } catch (Exception e) {
            return "0";
        }
        return sb.toString();
    }

    private Window window(Database db, long from) throws Exception {
        int sales = 0;
        long revenue = 0;
        long profit = 0;
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(sale_price),0), COALESCE(SUM(realized_profit),0) FROM positions "
                        + "WHERE mode='REAL' AND status='SOLD' AND closed_at > ?" + scope())) {
            ps.setLong(1, from);
            bindScope(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                sales = rs.getInt(1);
                revenue = rs.getLong(2);
                profit = (long) rs.getDouble(3);
            }
        }
        // Bought and paid come from different tables and different clocks, but
        // they belong on the same line as the sales: bought against sold says
        // whether the book is growing, and paid against profit says what is
        // actually left. The hourly webhook report carries both, and the Money
        // page is where people look for them the rest of the time.
        int bought = 0;
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM positions WHERE mode='REAL' AND purchased_at > ?" + scope())) {
            ps.setLong(1, from);
            bindScope(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) bought = rs.getInt(1);
            }
        }
        long paid = 0;
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COALESCE(SUM(amount),0) FROM payroll_payments "
                        + "WHERE status NOT IN ('FAILED','UNPAID','CARRIED') AND requested_at > ?")) {
            ps.setLong(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) paid = rs.getLong(1);
            }
        } catch (Exception ignored) {
            // no payroll table yet on a fresh ledger; the rest of the page still stands
        }
        return new Window(sales, revenue, profit, bought, paid);
    }

    private StatsImage.Page moneyPage(AutomationSessionController.SessionSnapshot s, Viewed v,
            String color, String subtitle, String footer, int slots) {
        List<String[]> head = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        // Who made each sale, so the hive's list of them says so. Left empty
        // for a single account, where every row would carry the same face.
        List<String> sellers = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (Database db = new Database(ledgerPath)) {
            Window h1 = window(db, now - 3_600_000L);
            Window h6 = window(db, now - 6 * 3_600_000L);
            Window d1 = window(db, now - 24 * 3_600_000L);
            head.add(new String[] {"Bank", v.bank() < 0 ? "unknown" : money(v.bank()), "gold"});
            head.add(new String[] {"Profit 1h", money(h1.profit()), "green"});
            head.add(new String[] {"Profit 24h", money(d1.profit()), "green"});
            head.add(new String[] {"Margin 1h", h1.margin(), "green"});
            head.add(new String[] {"~spark", "Profit/h · 8h|" + hourlyProfitCsv(db, 8), "green"});
            head.add(new String[] {"Sales 1h", h1.sales() + " sold · " + h1.bought() + " bought"});
            head.add(new String[] {"Sales 6h", String.valueOf(h6.sales()) + " · " + money(h6.profit())});
            head.add(new String[] {"Sales 24h", d1.sales() + " sold · " + d1.bought() + " bought"});
            head.add(new String[] {"Revenue 24h", money(d1.revenue()) + " · margin " + d1.margin()});
            if (account.isBlank()) {
                head.add(new String[] {"Payments (Hive)", money(h1.paid()) + " 1h · " + money(d1.paid()) + " 24h", "gold"});
                head.add(new String[] {"Payroll share", h1.payrollShare() + " 1h · " + d1.payrollShare() + " 24h"});
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(purchase_price),0), COALESCE(SUM(target_price),0) FROM positions "
                            + "WHERE mode='REAL' AND status='LISTED'" + scope())) {
                bindScope(ps, 1);
                ResultSet rs = ps.executeQuery();
                rs.next();
                head.add(new String[] {"Listed", v.listed() < 0 ? "unknown" : String.valueOf(v.listed())});
                head.add(new String[] {"Tracked cost", money(rs.getLong(2))});
                head.add(new String[] {"Asking", money(rs.getLong(3))});
            }
            head.add(new String[] {"Trades", v.trades() < 0 ? "unknown" : v.trades() + " this session"});
            head.addAll(OrderMetricsPresentation.compact(orderAccounts(), now));
            head.addAll(SwarmRank.rows(System.currentTimeMillis()));
            if (v.live() && session.quietMarket()) head.add(new String[] {"Mode", "quiet: smaller, steadier trades", "gold"});
            AutomationSessionController.WarmUp warm = session.warmUp();
            if (v.live() && !warm.ready()) {
                head.add(new String[] {"Warm-up", warm.marketsReady() + "/" + warm.marketsNeeded() + " markets priced", "gold"});
            }
            // "x0.96" was the model's own multiplier, which means nothing to
            // anybody reading the page. Two player counts do: the network is
            // what the one auction house serves, and the shard is who can walk
            // up to us. The multiplier still colours the row.
            dev.doughbay.fabric.PopulationModel pop = DoughBayClient.population();
            if (pop != null && pop.players() > 0) {
                String counts = pop.networkPlayers() > 0
                        ? String.format(Locale.ROOT, "%,d network · %,d on this shard",
                                pop.networkPlayers(), pop.players())
                        : String.format(Locale.ROOT, "%,d on this shard", pop.players());
                head.add(new String[] {"Players", counts,
                        pop.factorNow() >= 1.1 ? "green" : pop.factorNow() <= 0.9 ? "red" : "dim"});
            }
            String rivals = rivalSummary();
            if (!rivals.isEmpty()) head.add(new String[] {"Rivals", rivals});
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT item_key, quantity, purchase_price, sale_price, realized_profit, closed_at, client "
                            + "FROM positions WHERE mode='REAL' AND status='SOLD'" + scope()
                            + " ORDER BY closed_at DESC LIMIT 12")) {
                bindScope(ps, 1);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rows.add(new String[] {CLOCK.format(Instant.ofEpochMilli(rs.getLong(6))), item(rs.getString(1)) + " x" + rs.getInt(2),
                            money(rs.getLong(3)), money(rs.getLong(4)), signed((long) rs.getDouble(5))});
                    icons.add(rs.getString(1));
                    sellers.add(rs.getString(7));
                }
            }
        } catch (Exception e) {
            rows.add(new String[] {"", "ledger unavailable: " + e.getMessage(), "", "", ""});
        }
        StatsImage.Page money = new StatsImage.Page("Money", subtitle, color, PanelReadModel.moneyHeadline(head,slots,v.live()), v.listed(), slots,
                new String[] {"Time", "Last sales", "Cost", "Sold", "Profit"}, new boolean[] {false, false, true, true, true},
                rows, icons, 1, footer);
        boolean hive = account.isBlank() && panelAccounts().size() > 1;
        return hive ? money.withRowFaces(sellers) : money;
    }

    /**
     * Every account trading against this ledger, side by side.
     *
     * <p>Two clients working the same auction house are one business with two
     * purses, and the question is both what they made together and which of
     * them is carrying it. Everything here comes out of the shared ledger, so
     * whichever client happens to be hosting the panel can answer for all of
     * them; only its own balance is first-hand, and the others come from the
     * heartbeat each client writes every twenty seconds.
     */
    private StatsImage.Page hivePage(String color, String subtitle, String footer) {
        List<String[]> head = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        long totalBank = 0;
        long totalProfit = 0;
        int totalSales = 0;
        long totalProfit24 = 0;
        int totalSales24 = 0;
        int totalListed = 0;
        long totalAsk = 0;
        try (Database db = new Database(ledgerPath)) {
            List<String> accounts = new ArrayList<>(panelAccounts());
            if (accounts.isEmpty()) {
                String me = DoughBayClient.account();
                if (!me.isBlank()) accounts.add(me);
            }
            for (String name : accounts) {
                long bank = clientBalance(db, name);
                if (name.equalsIgnoreCase(DoughBayClient.account())) {
                    MarketWatcher w = DoughBayClient.watcher();
                    MarketWatcher.Snapshot snap = w == null ? null : w.snapshot();
                    if (snap != null && snap.accountBalance() > 0) bank = dev.doughbay.fabric.SidebarBalance.best(snap.accountBalance());
                }
                int listed = 0;
                long ask = 0;
                try (PreparedStatement ps = db.connection().prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(target_price),0) FROM positions "
                                + "WHERE mode='REAL' AND status='LISTED' AND client=?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            listed = rs.getInt(1);
                            ask = rs.getLong(2);
                        }
                    }
                }
                int sales1 = 0;
                long profit1 = 0;
                int sales24 = 0;
                long profit24 = 0;
                try (PreparedStatement ps = db.connection().prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(realized_profit),0) FROM positions "
                                + "WHERE mode='REAL' AND status='SOLD' AND closed_at > ? AND client=?")) {
                    ps.setLong(1, now - 3_600_000L);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            sales1 = rs.getInt(1);
                            profit1 = (long) rs.getDouble(2);
                        }
                    }
                }
                try (PreparedStatement ps = db.connection().prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(realized_profit),0) FROM positions "
                                + "WHERE mode='REAL' AND status='SOLD' AND closed_at > ? AND client=?")) {
                    ps.setLong(1, now - 24 * 3_600_000L);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            sales24 = rs.getInt(1);
                            profit24 = (long) rs.getDouble(2);
                        }
                    }
                }
                rows.add(new String[] {name, clientSession(db, name), bank >= 0 ? money(bank) : "?",
                        listed + " · " + money(ask), money(profit1) + " (" + sales1 + ")",
                        money(profit24) + " (" + sales24 + ")"});
                if (bank > 0) totalBank += bank;
                totalListed += listed;
                totalAsk += ask;
                totalProfit += profit1;
                totalSales += sales1;
                totalProfit24 += profit24;
                totalSales24 += sales24;
            }
            int currentCount = PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account()).size();
            head.add(new String[] {"Accounts", currentCount + " current · " + Math.max(0,accounts.size()-currentCount) + " historical", currentCount > 1 ? "green" : "dim"});
            head.add(new String[] {"Hive bank", money(totalBank), "gold"});
            head.add(new String[] {"Listed", totalListed + " · asking " + money(totalAsk)});
            head.add(new String[] {"Profit 1h", money(totalProfit) + " · " + totalSales + " sold", "green"});
            head.add(new String[] {"Profit 24h", money(totalProfit24) + " · " + totalSales24 + " sold", "green"});
            head.addAll(SwarmRank.rows(System.currentTimeMillis()));
            head.add(new String[] {"Viewing", account.isBlank() ? "all accounts" : account});
            head.add(new String[] {"Panel host", DoughBayClient.account() + " (this client)"});
        } catch (Exception e) {
            rows.add(new String[] {"", "", "ledger unavailable: " + e.getMessage(), "", "", ""});
        }
        if (rows.isEmpty()) rows.add(new String[] {"", "", "no clients are reporting yet", "", "", ""});
        int accountsSeen = Math.max(1, rows.size());
        return new StatsImage.Page("Hive", subtitle, color, head,
                totalListed, accountsSeen * (int) Tuning.get("slots.max"),
                new String[] {"Account", "Doing", "Bank", "Listed · asking", "1h profit", "24h profit"},
                new boolean[] {false, false, true, true, true, true}, rows, footer);
    }

    /** What one client's heartbeat last said it was worth, or -1. */
    private long clientBalance(Database db, String name) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT balance FROM api_clients WHERE name=? COLLATE NOCASE AND seen_at>? ORDER BY seen_at DESC LIMIT 1")) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis() - 90_000);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * What one client's session says it is doing, and how long ago it said so.
     *
     * <p>The checkpoint has been keyed by account since the two of them stopped
     * overwriting each other, which is what makes it possible to answer this
     * for a client that is not the one running this code.
     */
    private String clientSession(Database db, String name) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT controller_state, updated_at FROM automation_session_state WHERE client=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "-";
                String state = rs.getString(1).toLowerCase(Locale.ROOT).replace('_', ' ');
                // A checkpoint that stopped moving is the one thing this page
                // can say that the client itself cannot: it has gone quiet.
                long age = System.currentTimeMillis() - rs.getLong(2);
                return age > 300_000 ? state + " (" + ago(age) + " ago)" : state;
            }
        } catch (Exception e) {
            return "-";
        }
    }

    /**
     * The money on show: this client's own balance when it is the one being
     * looked at, and the hive's together when the view is the hive.
     *
     * <p>Only this client knows its own balance first-hand; the others' come
     * from their heartbeats, a few seconds old at worst and the only figure
     * there is.
     */
    private long bankInView(Database db, long ownBalance) {
        String me = DoughBayClient.account();
        if (!account.isBlank()) {
            if (account.equalsIgnoreCase(me)) return ownBalance;
            long theirs = clientBalance(db, account);
            return theirs;
        }
        if (panelAccounts().size() < 2) return ownBalance;
        // One balance per account, the freshest. A restart leaves the old
        // session's heartbeat row behind under a new client id until it ages
        // out, so summing every row counted each account twice - the hive of
        // two read like a hive of four, and the bank line doubled. Keyed by
        // name, the stale row is the same account and simply loses to the new.
        java.util.Map<String, Long> latest = new java.util.HashMap<>();
        java.util.Map<String, Long> latestAt = new java.util.HashMap<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT name, balance, seen_at FROM api_clients WHERE balance >= 0 AND name <> '' AND seen_at>" + (System.currentTimeMillis()-90_000));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nm = rs.getString(1).toLowerCase(Locale.ROOT);
                long at = rs.getLong(3);
                if (!latestAt.containsKey(nm) || at > latestAt.get(nm)) {
                    latestAt.put(nm, at);
                    latest.put(nm, rs.getLong(2));
                }
            }
        } catch (Exception ignored) {
            // one unreadable heartbeat should not blank the bank line
        }
        if (latest.isEmpty()) return ownBalance;
        // This client's own reading is fresher than the one it last wrote.
        latest.put(me.toLowerCase(Locale.ROOT), ownBalance);
        var members = new java.util.HashSet<String>();
        for (String member : panelAccounts()) members.add(member.toLowerCase(Locale.ROOT));
        members.add(me.toLowerCase(Locale.ROOT));
        latest.keySet().retainAll(members);
        long total = 0;
        for (long b : latest.values()) total += b;
        return total;
    }

    /**
     * Listings on show. This client counts its own from the session it is
     * running; anybody else's, and the hive's together, come from the ledger.
     */
    private int openInView(Database db, int own) {
        if (!account.isBlank() && account.equalsIgnoreCase(DoughBayClient.account())) return own;
        if (account.isBlank() && panelAccounts().size() < 2) return own;
        try (PreparedStatement ps = scoped(db,
                "SELECT COUNT(*) FROM positions WHERE mode='REAL' AND status='LISTED'" + scope());
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : own;
        } catch (Exception e) {
            return -1;
        }
    }

    /** The one-line rival summary the hourly report carries. */
    private static String rivalSummary() {
        try {
            dev.doughbay.fabric.RivalIntel intel = DoughBayClient.rivalIntel();
            if (intel == null) return "";
            String status = intel.shortStatus();
            if (status == null || status.isBlank()) return "quiet";
            // The status names itself ("rivals 12/12") and the row is already
            // labelled Rivals; printed together it read "Rivals rivals 12/12".
            return status.regionMatches(true, 0, "rivals ", 0, 7) ? status.substring(7) : status;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * What it is doing, what it is about to do, and what it has just done.
     *
     * <p>Every other page answers "how much money". This one answers "is it
     * working", which is the question actually asked when something looks
     * wrong, and the one that took a log file to answer until now.
     */
    private StatsImage.Page statusPage(AutomationSessionController.SessionSnapshot s, Viewed v,
            String color, String subtitle, String footer) {
        List<String[]> head = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (!v.live()) {
            head.add(new String[]{"Scope", v.label()});
            head.add(new String[]{"Doing", v.state()});
            head.add(new String[]{"Live queue", "Not available for this selection from the host", "gold"});
            return new StatsImage.Page("Status", subtitle, color, head, 0, 0,
                    new String[]{"Account", "Status"}, new boolean[]{false, false},
                    List.<String[]>of(new String[]{v.label(), "Select the account's own panel for live queue history."}), footer);
        }

        // The queue and the history live in this client's memory and nowhere
        // else, so this page can only ever be about the client hosting the
        // panel. Say so rather than let it read as the account that is picked.
        String me = DoughBayClient.account();
        if (!me.isBlank() && panelAccounts().size() > 1) {
            head.add(new String[] {"Client", me + " (hosts the panel)",
                    v.live() || account.isBlank() ? "dim" : "gold"});
            if (!v.live() && !account.isBlank()) {
                head.add(new String[] {"Note", account + "'s queue is not visible from here", "gold"});
            }
        }
        head.add(new String[] {"Doing", s.state().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                s.active() ? "green" : "gold"});
        java.util.List<String> planned = java.util.List.of();
        try {
            planned = session.plannedWork();
        } catch (Throwable ignored) {
            // a status page must never be the thing that breaks
        }
        head.add(new String[] {"Queued", planned.isEmpty() ? "nothing" : planned.size() + " step(s)"});
        if (!s.detail().isBlank()) {
            head.add(new String[] {"Detail", s.detail().length() > 64 ? s.detail().substring(0, 64) : s.detail()});
        }
        head.add(new String[] {"Since", ago(now - s.updatedAtMillis())});
        // The stall watch, said out loud whether or not it is worried: a guard
        // that only speaks when unhappy cannot be told from one that is dead.
        String watch = DoughBayClient.livenessVerdict();
        head.add(new String[] {"Stall watch", watch,
                watch.startsWith("still") ? "gold" : "dim"});
        head.add(new String[] {"Listings", listedNow(s) + " up"});
        head.add(new String[] {"Trades", s.tradesStarted() + " this session"});
        if (s.unresolvedExposure()) head.add(new String[] {"Exposure", "unresolved; nothing may run", "red"});

        // The queue first, in the order it will happen, then what already
        // happened underneath it. One feed, reading forwards from now.
        for (int i = 0; i < planned.size() && rows.size() < 12; i++) {
            String w = planned.get(i);
            rows.add(new String[] {"next", (i == 0 ? "now" : "+" + i), w.length() > 74 ? w.substring(0, 74) : w});
        }
        try {
            List<AutomationSessionController.Action> actions = session.recentActions();
            for (int i = actions.size() - 1; i >= 0 && rows.size() < 26; i--) {
                AutomationSessionController.Action a = actions.get(i);
                String what = a.detail().isBlank() ? a.state().toLowerCase(Locale.ROOT).replace('_', ' ') : a.detail();
                if (what.length() > 74) what = what.substring(0, 74);
                rows.add(new String[] {CLOCK.format(Instant.ofEpochMilli(a.at())), ago(now - a.at()), what});
            }
        } catch (Throwable t) {
            rows.add(new String[] {"", "", "history unavailable: " + t});
        }
        if (rows.isEmpty()) rows.add(new String[] {"", "", "nothing queued and nothing recorded yet"});

        return new StatsImage.Page("Status", subtitle, color, head, 0, 0,
                new String[] {"When", "Ago", "Queue, then what it just did"},
                new boolean[] {false, true, false}, rows, footer);
    }

    /** "4m" or "2h 10m", for how long ago something was. */
    private static String ago(long millis) {
        long mins = Math.max(0, millis / 60_000L);
        if (mins < 60) return mins + "m";
        return (mins / 60) + "h " + (mins % 60) + "m";
    }

    private StatsImage.Page listingsPage(Viewed v, String color, String subtitle, String footer, int slots) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        long now = System.currentTimeMillis();
        int listed = v.listed();
        try (Database db = new Database(ledgerPath);
             PreparedStatement ps = scoped(db,
                     "SELECT item_key, quantity, purchase_price, target_price, listed_at FROM positions "
                             + "WHERE mode='REAL' AND status='LISTED'" + scope() + " ORDER BY listed_at DESC LIMIT 18");
             ResultSet rs = ps.executeQuery()) {
            long cost = 0;
            long ask = 0;
            int n = 0;
            while (rs.next()) {
                long age = Math.max(0, now - rs.getLong(5)) / 60_000;
                rows.add(new String[] {item(rs.getString(1)) + " x" + rs.getInt(2), money(rs.getLong(3)), money(rs.getLong(4)),
                        signed(rs.getLong(4) - rs.getLong(3)), age + " min"});
                icons.add(rs.getString(1));
                cost += rs.getLong(3);
                ask += rs.getLong(4);
                n++;
            }
            head.add(new String[] {"Open", listed + " of " + slots});
            head.add(new String[] {"Newest " + n, "cost " + money(cost)});
            head.add(new String[] {"Asking", money(ask), "gold"});
        } catch (Exception e) {
            rows.add(new String[] {"ledger unavailable: " + e.getMessage(), "", "", "", ""});
        }
        return new StatsImage.Page("Listings", subtitle, color, head, listed, slots,
                new String[] {"Item", "Cost", "Ask", "Margin", "Up for"}, new boolean[] {false, true, true, true, true},
                rows, icons, footer);
    }

    private StatsImage.Page oppsPage(MarketWatcher.Snapshot m, String color, String subtitle, String footer) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        if (m != null) {
            head.add(new String[] {"Signals", String.valueOf(m.opportunities().size())});
            head.add(new String[] {"Markets", String.valueOf(m.markets().size())});
            head.add(new String[] {"Feed", m.status().length() > 30 ? m.status().substring(0, 30) : m.status()});
            int n = 0;
            for (Opportunity o : m.opportunities()) {
                if (n++ >= 14) break;
                Listing l = o.listing();
                rows.add(new String[] {item(l.itemId()) + " x" + l.itemCount(), money(o.buyPrice()), money(o.recommendedSellPrice()),
                        signed((long) o.expectedNetProfit()), String.format(Locale.ROOT, "%.0f%%", o.expectedRoiPercent()),
                        String.format(Locale.ROOT, "%.0f%%", o.confidence() * 100)});
                icons.add(l.itemId());
            }
        }
        return new StatsImage.Page("Opportunities", subtitle, color, head, 0, 0,
                new String[] {"Item", "Buy", "Sell", "Profit", "ROI", "Conf"}, new boolean[] {false, true, true, true, true, true},
                rows, icons, footer);
    }

    private StatsImage.Page ordersPage(String color, String subtitle, String footer) {
        OrderBook book = DoughBayClient.orderBook();
        return ordersPageFor(orderAccounts(), book == null ? -1 : book.orders().size(),
                book == null ? 0 : book.readAt(), System.currentTimeMillis(), color, subtitle);
    }

    static StatsImage.Page ordersPageFor(List<dev.doughbay.fabric.OrderMetrics.Account> accounts,
            int marketCount, long marketAt, long now, String color, String subtitle) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        head.add(new String[] {"Market book", marketCount < 0 ? "off" : marketCount + " observed orders"});
        head.add(new String[] {"Book read", marketAt == 0 ? "never" : CLOCK.format(Instant.ofEpochMilli(marketAt))});
        head.addAll(OrderMetricsPresentation.summary(accounts, now));
        for (var owner : accounts) {
            var report = owner.report();
            if (!dev.doughbay.fabric.OrderMetrics.fresh(report, now)) {
                rows.add(new String[]{owner.name(), "unavailable / stale", "", "", "", "unknown"});
                icons.add(""); continue;
            }
            for (var r : report.rows()) {
                if (rows.size() >= 40) break;
                rows.add(new String[]{owner.name(), item(r.itemId()), String.valueOf(r.requested()), String.valueOf(r.delivered()), money(r.unitPrice()),
                        r.alreadyCancelled() ? "cancelled" : r.remaining() == 0 ? "filled" : r.remaining() + " to go"});
                icons.add(r.itemId());
            }
            if (report.rows().isEmpty()) { rows.add(new String[]{owner.name(), "No observed orders", "", "", "", ""}); icons.add(""); }
        }
        return new StatsImage.Page("Orders", subtitle, color, head, 0, 0,
                new String[] {"Account", "Your order", "Asked", "Delivered", "Each", "State"}, new boolean[] {false, false, true, true, true, false},
                rows, icons, 1, "First 40 rows; select an account for its orders. Filled = delivered, not sold. Counts are observed transitions, not reconstructed history.");
    }

    private List<String> panelAccounts() {
        return dev.doughbay.fabric.OrderMetrics.members(ledgerPath, DoughBayClient.hiveAccounts(), DoughBayClient.account(),
                dev.doughbay.fabric.HiveSetup.active() || Tuning.get("multi.enabled") >= .5);
    }

    private List<dev.doughbay.fabric.OrderMetrics.Account> orderAccounts() {
        return capacityAccounts();
    }

    private List<dev.doughbay.fabric.OrderMetrics.Account> capacityAccounts() {
        return PanelReadModel.capacityAccounts(ledgerPath.toAbsolutePath().getParent(), ledgerScope().account(),
                PanelReadModel.currentMembers(DoughBayClient.hiveAccounts(), DoughBayClient.account()));
    }

    /** Who came near, when, and what the guard did about it - newest first. */
    private StatsImage.Page staffPage() {
        return staffPageFor(dev.doughbay.fabric.StaffWatchReport.snapshot(),System.currentTimeMillis(),DoughBayClient.account(),ZoneId.systemDefault());
    }
    private StatsImage.Page regionPage() {
        var view = dev.doughbay.fabric.RegionMapReport.snapshot();
        return new StatsImage.Page("Region Map", "This client", "green",
                List.<String[]>of(new String[]{"Mapped server", view.server()}), 0, 0,
                new String[]{"Status"}, new boolean[]{false}, List.<String[]>of(new String[]{view.status()}),
                "Map: panel host. Switch controls move the selected account. Coordinates hidden.");
    }
    static StatsImage.Page staffPageFor(dev.doughbay.fabric.StaffWatchReport.Snapshot snapshot,long now,String host,ZoneId zone) {
        List<String[]> head = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        head.add(new String[]{"Detector", snapshot.status(now)});
        head.add(new String[]{"Watched",dev.doughbay.fabric.StaffActivityMetrics.durationLabel(snapshot.watchedMillis())});
        head.add(new String[]{"Clock",zone.getId()});
        for (var row : snapshot.current(now)) rows.add(new String[]{"Now", dev.doughbay.fabric.staffwatch.util.NameFilter.apply(row.name()),
                row.presence().equals("SPECTATOR") ? "Spectator observed" : row.presence().equals("VANISHED") ? "Hidden from TAB" : "Online in player list",
                row.observer()+(row.sameRegion()?" (local signal)":"")});
        for(var summary:snapshot.summaries().stream().limit(8).toList()) {
            rows.add(new String[]{"History",dev.doughbay.fabric.staffwatch.util.NameFilter.apply(summary.name()),
                dev.doughbay.fabric.StaffActivityMetrics.durationLabel(summary.observedMillis())+" detected",summary.intervals()+" intervals; last "+Instant.ofEpochMilli(summary.lastSeen()).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d HH:mm"))});
            rows.add(new String[]{"Usual hours",dev.doughbay.fabric.staffwatch.util.NameFilter.apply(summary.name()),summary.usualHours(),"Observed, not predicted"});
            rows.add(new String[]{"First seen",dev.doughbay.fabric.staffwatch.util.NameFilter.apply(summary.name()),Instant.ofEpochMilli(summary.firstSeen()).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d HH:mm")),"In retained 30-day history"});
        }
        for (var sighting : snapshot.history().stream().limit(8).toList()) rows.add(new String[]{
                Instant.ofEpochMilli(sighting.at()).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                dev.doughbay.fabric.staffwatch.util.NameFilter.apply(sighting.name()), dev.doughbay.fabric.StaffWatchReport.label(sighting.event()), sighting.observer()});
        if (rows.isEmpty()) rows.add(new String[]{"", "No staff observations", "", ""});
        return new StatsImage.Page("Staff Watch", host + " · " + Instant.ofEpochMilli(now).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                "green", head, 0, 0,
                new String[]{"Time", "Staff", "Observation", "Observer / history"},
                new boolean[]{false, false, false, false}, rows,
                "Passive hive observations. Gaps excluded; overlap merged. Absence does not prove offline.");
    }

    private StatsImage.Page evasionPage(String color, String subtitle, String footer) {
        List<String[]> head = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        dev.doughbay.fabric.EvasionGuard ev = DoughBayClient.evasionGuard();
        int mode = (int) Tuning.get("evasion.escape");
        boolean armed = ev != null && ev.enabled();
        head.add(new String[] {"Guard", ev == null ? "off" : ev.status(), !armed ? "red" : ev.triggered() ? "gold" : "green"});
        head.add(new String[] {"Pause ring", (int) Tuning.get("evasion.radius_blocks") + " blocks"});
        head.add(new String[] {"Escape ring", (int) Tuning.get("evasion.disconnect_blocks") + " blocks"});
        head.add(new String[] {"On contact", mode == 0 ? "pause only" : mode == 1 ? "teleport away" : "leave the server"});
        // Every client's events come from the shared ledger, so the panel
        // shows the hive and not just the client that hosts it; the account
        // picker narrows it to one. Sightings are the outer ring speaking and
        // are listed, but only a pause or an escape counts as a trigger.
        long now = System.currentTimeMillis();
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm:ss");
        String scope = account.isBlank() ? "" : account;
        try (Database db = new Database(ledgerPath)) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT COUNT(*) FROM evasion_events WHERE at > ? AND action <> 'sighted' AND action NOT LIKE 'move-on%' "
                            + "AND (? = '' OR lower(client) = lower(?))")) {
                ps.setLong(1, now - 24 * 3_600_000L);
                ps.setString(2, scope);
                ps.setString(3, scope);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) head.add(new String[] {"Triggered 24h", String.valueOf(rs.getInt(1))});
                }
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT who, COUNT(*) FROM evasion_events WHERE at > ? AND who <> '' "
                            + "AND (? = '' OR lower(client) = lower(?)) GROUP BY who ORDER BY 2 DESC LIMIT 1")) {
                ps.setLong(1, now - 7 * 24 * 3_600_000L);
                ps.setString(2, scope);
                ps.setString(3, scope);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) head.add(new String[] {"Most seen 7d", rs.getString(1) + " x" + rs.getInt(2), "gold"});
                }
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT at, client, who, distance, action FROM evasion_events "
                            + "WHERE (? = '' OR lower(client) = lower(?)) ORDER BY at DESC LIMIT 14")) {
                ps.setString(1, scope);
                ps.setString(2, scope);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String when = Instant.ofEpochMilli(rs.getLong(1)).atZone(ZoneId.systemDefault()).format(tf);
                        double blocks = rs.getDouble(4);
                        String action = rs.getString(5);
                        // Keep the outcome readable in the narrow history column. The full
                        // destination and cooldown remain in the dedicated notification.
                        if (action.startsWith("Staff Watch:")) action = dev.doughbay.storage.StaffWatchAlerts.historyLabel(action);
                        rows.add(new String[] {when, rs.getString(2), rs.getString(3),
                                blocks > 0 ? String.format(Locale.ROOT, "%.0f", blocks) : "", action});
                    }
                }
            }
        } catch (Exception e) {
            rows.add(new String[] {"", "", "could not read the ledger", "", e.getMessage()});
        }
        if (rows.isEmpty()) rows.add(new String[] {"", "", "nobody has come near", "", ""});
        return new StatsImage.Page("Evasion", subtitle, color, head, 0, 0,
                new String[] {"Time", "Client", "Who", "Blocks", "What happened"},
                new boolean[] {false, false, false, true, false}, rows, footer);
    }

    private StatsImage.Page rivalsPage(String color, String subtitle, String footer) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        RivalIntel intel = DoughBayClient.rivalIntel();
        RivalIntel.Snapshot r = intel == null ? RivalIntel.Snapshot.empty("off") : intel.snapshot();
        long now = System.currentTimeMillis();
        head.add(new String[] {"Rivals", r.rivals().size() + " tracked · " + r.activeNow() + " active"});
        head.add(new String[] {"Bots", String.valueOf(r.likelyBots())});
        head.add(new String[] {"Underdog", Tuning.get("underdog.enabled") >= 0.5 ? r.shadows().size() + " shadowed" : "off"});
        int n = 0;
        for (RivalIntel.Rival rv : r.rivals()) {
            if (n++ >= 10) break;
            rows.add(new String[] {rv.name(), String.valueOf(rv.sales()), money(rv.revenue()), String.format(Locale.ROOT, "%.0f%%", rv.averageMarginPercent()),
                    rv.status(now)});
        }
        for (RivalIntel.ShadowMarket sm : r.shadows()) {
            if (n++ >= 16) break;
            rows.add(new String[] {"↳ " + item(sm.itemId()) + " x" + sm.count(), sm.flips() + " flips", money(sm.medianBuy()) + "→" + money(sm.medianSale()),
                    String.format(Locale.ROOT, "%.0f%%", sm.marginPercent()), "shadow of " + sm.rival()});
        }
        return new StatsImage.Page("Rivals", subtitle, color, head, 0, 0,
                new String[] {"Name", "Sales", "Revenue", "Margin", "Status"}, new boolean[] {false, true, true, true, false}, rows, footer);
    }

    /** What each item has earned, from the ledger: the item board of the Performance tab, top fifteen. */
    private StatsImage.Page itemsPage(String color, String subtitle, String footer) {
        dev.doughbay.fabric.LedgerStatsProvider ledger = DoughBayClient.ledgerStats();
        dev.doughbay.fabric.ItemBoard.Board b = ledger == null
                ? dev.doughbay.fabric.ItemBoard.Board.empty(itemsRange) : ledger.board(itemsRange);
        List<String[]> head = new ArrayList<>();
        head.add(new String[] {"Profit", money(b.profit()) + " · " + itemsRange.label});
        head.add(new String[] {"Sales", String.valueOf(b.sales())});
        head.add(new String[] {"Return", b.cost() <= 0 ? "n/a" : String.format(Locale.ROOT, "%.0f%%", b.returnPercent())});
        head.add(new String[] {"Tied up", money(b.openCost()) + " in " + b.open()});
        List<String[]> rows = new ArrayList<>();
        int n = 0;
        for (dev.doughbay.fabric.ItemBoard.Row r : b.sorted(itemsSort, itemsBest)) {
            if (n++ >= 15) break;
            boolean any = r.sales() > 0;
            long took = r.medianSellMillis() / 1000;
            String sellsIn = took <= 0 ? "-" : took < 60 ? took + "s" : took < 3_600 ? took / 60 + "m"
                    : String.format(Locale.ROOT, "%.1fh", took / 3_600.0);
            rows.add(new String[] {r.name(), any ? money(r.profit()) : "-", any ? String.valueOf(r.sales()) : "-",
                    any ? money(r.perSale()) : "-", any ? money(r.perSlotHour()) : "-", sellsIn,
                    r.openCost() > 0 ? money(r.openCost()) : "-"});
        }
        return new StatsImage.Page("Items · by " + itemsSort.label.toLowerCase(Locale.ROOT)
                + (itemsBest ? "" : ", worst first"), subtitle, color, head, 0, 0,
                new String[] {"Item", "Profit", "Sales", "Per sale", "Per slot-hr", "Sells in", "Tied up"},
                new boolean[] {false, true, true, true, true, true, true}, rows, footer);
    }

    private StatsImage.Page payrollPage(String color, String subtitle, String footer) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        Payroll payroll = DoughBayClient.payroll();
        long now = System.currentTimeMillis();
        if (payroll != null) {
            StringBuilder rules = new StringBuilder();
            for (PayrollRepository.Rule rule : payroll.rules()) {
                if (rules.length() > 0) rules.append(", ");
                rules.append(rule.name()).append(' ').append(String.format(Locale.ROOT, "%.0f%%", rule.percent()));
            }
            long day = 0;
            int count = 0;
            int n = 0;
            for (PayrollRepository.Payment p : payroll.payments()) {
                if (p.requestedAt() > now - 24 * 3_600_000L && !"FAILED".equals(p.status())) {
                    day += p.amount();
                    count++;
                }
                if (n++ < 12) rows.add(new String[] {CLOCK.format(Instant.ofEpochMilli(p.requestedAt())), p.name(), money(p.amount()), p.status()});
            }
            head.add(new String[] {"Rules", rules.length() == 0 ? "none" : rules.toString()});
            head.add(new String[] {"Paid 24h", money(day) + " in " + count, "gold"});
            head.add(new String[] {"Status", payroll.status().length() > 30 ? payroll.status().substring(0, 30) : payroll.status()});
        }
        return new StatsImage.Page("Payroll", subtitle, color, head, 0, 0,
                new String[] {"Time", "To", "Amount", "Status"}, new boolean[] {false, false, true, false}, rows, footer);
    }

    /** The player's own price list: what each item is bought and sold at, and how it stands. */
    private StatsImage.Page flipsPage(String color, String subtitle, String footer) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        dev.doughbay.fabric.OwnPrices.Entry chosen = flip();
        List<AutomationSessionController.OwnPriceView> views = session.ownPriceViews();
        head.add(new String[] {"On your list", views.isEmpty() ? "nothing yet: press Add item" : views.size() + " item(s)", "gold"});
        head.add(new String[] {"Trading", dev.doughbay.fabric.FlipsMode.description()});
        if (chosen != null) {
            head.add(new String[] {"Selected", item(chosen.itemId()) + ": buy " + money(chosen.buy()) + ", sell " + money(chosen.sell())});
        }
        for (AutomationSessionController.OwnPriceView v : views) {
            dev.doughbay.fabric.OwnPrices.Entry e = v.entry();
            if (rows.size() >= 14) break;
            rows.add(new String[] {(chosen != null && chosen.itemId().equals(e.itemId()) ? "▶ " : "") + item(e.itemId()),
                    (e.top() ? "top " : "") + money(e.buy()), money(e.sell()),
                    e.singles() ? "singles" : e.stacks() > 0 ? "x" + e.stacks() : "",
                    v.marketUnit() > 0 ? money(Math.round(v.marketUnit())) : "-",
                    String.valueOf(v.onOrder()), String.valueOf(v.listedUnits())});
            icons.add("minecraft:" + e.itemId());
        }
        return new StatsImage.Page("Flips", subtitle, color, head, 0, 0,
                new String[] {"Item", "Buy", "Sell", "Size", "Market", "Order", "Listed"},
                new boolean[] {false, true, true, false, true, true, true}, rows, icons, footer);
    }

    private StatsImage.Page tunePage(String color, String subtitle, String footer) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        String group = tuneGroup.isBlank() ? Tuning.groups().get(0) : tuneGroup;
        head.add(new String[] {"Group", "#presets".equals(group) ? "Presets" : group, "gold"});
        Tuning.Setting current = setting(tuneKey);
        head.add(new String[] {"Selected", current == null ? "pick one below" : current.label() + " = " + current.format(Tuning.get(current.key()))});
        head.add(new String[] {"Presets", String.join(", ", Tuning.PRESETS.keySet())});
        if ("#presets".equals(group)) {
            for (var e : Tuning.PRESETS.entrySet()) rows.add(new String[] {e.getKey(), e.getValue().size() + " settings", ""});
        } else {
            for (Tuning.Setting st : Tuning.SETTINGS) {
                if (!st.group().equals(group)) continue;
                rows.add(new String[] {(st.key().equals(tuneKey) ? "▶ " : "") + st.label(), st.format(Tuning.get(st.key())),
                        Tuning.isDefault(st.key()) ? "default" : "changed"});
            }
        }
        return new StatsImage.Page("Tune", subtitle, color, head, 0, 0,
                new String[] {"Setting", "Value", ""}, new boolean[] {false, true, false}, rows, footer);
    }

    private StatsImage.Page searchPage(MarketWatcher.Snapshot m, String color, String subtitle, String footer) {
        List<String[]> rows = new ArrayList<>();
        List<String[]> head = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        head.add(new String[] {"Search", searchTerm.isBlank() ? "press Search" : searchTerm, "gold"});
        if (m != null && !searchTerm.isBlank()) {
            int n = 0;
            for (MarketStats st : m.markets()) {
                if (!st.itemKey().contains(searchTerm) || !st.hasPrices()) continue;
                if (n++ >= 12) break;
                rows.add(new String[] {item(st.itemKey()) + " x" + st.bucket().exactCount(), money((long) st.quickSalePrice()), money((long) st.weightedMedian()),
                        String.format(Locale.ROOT, "%.1f/h", st.salesPerHour()), String.format(Locale.ROOT, "%.0f%%", st.confidence() * 100)});
                icons.add(st.itemKey());
            }
            long lowest = 0;
            int asks = 0;
            for (Listing l : m.activeListings()) {
                if (!l.itemId().contains(searchTerm)) continue;
                asks++;
                if (lowest == 0 || l.totalPrice() < lowest) lowest = l.totalPrice();
            }
            head.add(new String[] {"Asks up", String.valueOf(asks)});
            head.add(new String[] {"Lowest ask", asks == 0 ? "none" : money(lowest)});
        }
        return new StatsImage.Page("Markets", subtitle, color, head, 0, 0,
                new String[] {"Market", "Quick sale", "Median", "Sales", "Conf"}, new boolean[] {false, true, true, true, true},
                rows, icons, footer);
    }

    // ---------------------------------------------------------------- formatting

    static String money(long v) {
        long a = Math.abs(v);
        String s;
        // A bank of 1014.00M is a billion, and reads as one.
        if (a >= 1_000_000_000L) s = String.format(Locale.ROOT, "$%.2fB", a / 1_000_000_000.0);
        else if (a >= 1_000_000) s = String.format(Locale.ROOT, "$%.2fM", a / 1_000_000.0);
        else if (a >= 10_000) s = String.format(Locale.ROOT, "$%.1fK", a / 1_000.0);
        else s = String.format(Locale.ROOT, "$%,d", a);
        return v < 0 ? "-" + s : s;
    }

    static String signed(long v) {
        return v >= 0 ? "+" + money(v) : money(v);
    }

    static String item(String key) {
        String s = key.indexOf('#') >= 0 ? key.substring(0, key.indexOf('#')) : key;
        s = s.startsWith("minecraft:") ? s.substring(10) : s;
        return s.replace('_', ' ');
    }
}
