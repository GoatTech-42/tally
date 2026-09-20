package dev.doughbay.fabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Who pays whom, read from the payment notices the server sends about
 * followed players.
 *
 * <p>A payment has two people in it, and a notice about either of them names
 * both. So following one side is enough to learn of the other, followed or
 * not. As observed on 2026-10-11 the server sends one notice for a payment,
 * not two: where both sides are followed and both are open it is the payer's
 * ("X paid Y"). The receiver's ("Y was paid by X") is what arrives when the
 * payer's own is not there to send - so that wording, about a payer who was
 * already being followed, is the mark of a payer whose payments are private.
 *
 * <p>What this shows is who moves money to whom, how much and how often. It
 * does not show why. The shapes named here describe a flow and nothing else:
 * money going one way can be a sale for real money, a gift, a shop, or a
 * player moving funds to their own second account.
 */
public final class MoneyFlows {
    /** The same payment told from both sides would arrive as two notices this close together. */
    static final long PAIR_MILLIS = 3_000L;
    /** This many different people on one side of an account is "many". */
    static final int MANY = 5;
    /** Money in before an account that sends the same on is worth naming. */
    static final long LARGE = 50_000_000L;

    private MoneyFlows() { }

    /** How the money around one account moves. A description of the flow, not of the reason for it. */
    public enum Shape {
        HUB("hub, pays back", "Many players pay in and some of the same players are paid back. "
                + "A game of chance or a middleman looks like this; the payments do not say which."),
        COLLECTOR("collects", "Many players pay in and little goes back out: a shop, a service, "
                + "donations, or a place money is gathered."),
        PAYS_MANY("pays many", "Pays many players and takes little in: wages, prizes or payouts."),
        PASSES_ON("passes it on", "About as much goes out as came in, and to other players: money passing through."),
        RECEIVES("receives", "Only seen receiving. Nothing has been seen going back."),
        SENDS("sends", "Only seen sending. Nothing has been seen coming back."),
        MIXED("both ways", "Money moves both ways.");

        public final String label;
        /** A sentence for the account's own page. */
        public final String reading;

        Shape(String label, String reading) {
            this.label = label;
            this.reading = reading;
        }
    }

    /** What is known of whether a player lets followers see their own trades and payments. */
    public enum Privacy {
        /** Their own notices arrive. */
        OPEN("open", "their own notices arrive"),
        /** Followed, and a payment of theirs arrived told by the other side: their own are hidden. */
        HIDDEN("private", "followed, yet a payment of theirs came told by the other side: their own are hidden"),
        /** Followed, named by others, and nothing of their own has arrived. Not proof of anything. */
        SILENT("silent", "followed, and nothing of their own has arrived yet"),
        /** Known only from other people's notices. */
        NOT_FOLLOWED("not followed", "known only from other players' notices");

        public final String label;
        public final String reading;

        Privacy(String label, String reading) {
            this.label = label;
            this.reading = reading;
        }
    }

    /** Everything one player paid another. */
    public record Edge(String payer, String receiver, int count, long total, long largest, long lastAt) { }

    /**
     * One player's side of it.
     *
     * @param bothWays how many of the people who pay them they also pay
     * @param trades   listings, orders, purchases and sales seen from them; only means something when open
     * @param exposed  money of theirs seen only because the other side's notice named them
     */
    public record Account(String name, long in, long out, int paymentsIn, int paymentsOut, int payers, int payees,
                          int bothWays, int trades, Privacy privacy, long exposed, long lastAt, Shape shape) {
        public long moved() {
            return in + out;
        }

        public long net() {
            return in - out;
        }
    }

    /**
     * @param hidden      players shown to be private whose payments are seen anyway
     * @param hiddenMoved the money in those payments
     * @param unfollowed  players known only through someone else's notice
     */
    public record Snapshot(long builtAt, int payments, long moved, int hidden, long hiddenMoved, int unfollowed,
                           List<Account> accounts, List<Edge> edges) {
        public static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0, List.of(), List.of());
        }

        public Account account(String name) {
            if (name == null) return null;
            for (Account a : accounts) if (a.name().equalsIgnoreCase(name)) return a;
            return null;
        }

        /** Who pays {@code name}, the most first. */
        public List<Edge> into(String name) {
            List<Edge> out = new ArrayList<>();
            for (Edge e : edges) if (e.receiver().equalsIgnoreCase(name)) out.add(e);
            return out;
        }

        /** Who {@code name} pays, the most first. */
        public List<Edge> outOf(String name) {
            List<Edge> out = new ArrayList<>();
            for (Edge e : edges) if (e.payer().equalsIgnoreCase(name)) out.add(e);
            return out;
        }

        /** Everyone {@code name} has paid or been paid by, as the server spells them. */
        public List<String> around(String name) {
            Set<String> seen = new HashSet<>();
            List<String> out = new ArrayList<>();
            for (Edge e : edges) {
                String other = e.receiver().equalsIgnoreCase(name) ? e.payer()
                        : e.payer().equalsIgnoreCase(name) ? e.receiver() : null;
                if (other != null && seen.add(other.toLowerCase(Locale.ROOT))) out.add(other);
            }
            return out;
        }
    }

    private static final class Payment {
        String payer;
        String receiver;
        long amount;
        long at;
        boolean toldByPayer;
        boolean toldByReceiver;
    }

    private static final class Tally {
        String name;
        long in;
        long out;
        int paymentsIn;
        int paymentsOut;
        final Set<String> payers = new HashSet<>();
        final Set<String> payees = new HashSet<>();
        int trades;
        boolean own;
        boolean shownPrivate;
        long exposed;
        long lastAt;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * @param seen          the notices, in any order
     * @param followedSince lower-cased name to when the server confirmed the follow
     */
    public static Snapshot build(List<Follows.Seen> seen, Map<String, Long> followedSince, long now) {
        List<Follows.Seen> events = new ArrayList<>(seen);
        events.sort(Comparator.comparingLong(Follows.Seen::at));
        Map<String, Tally> tallies = new LinkedHashMap<>();
        List<Payment> payments = new ArrayList<>();
        Map<String, ArrayDeque<Payment>> lately = new HashMap<>();
        for (Follows.Seen s : events) {
            if (s.player() == null || s.player().isBlank()) continue;
            String kind = s.kind();
            if (kind.equals("listed") || kind.equals("ordered") || kind.equals("bought") || kind.equals("sold")) {
                Tally t = tally(tallies, s.player());
                t.trades++;
                t.own = true;
                continue;
            }
            boolean byPayer = kind.equals("paid");
            if (!byPayer && !kind.equals("was_paid")) continue;
            String other = s.itemId();
            if (other == null || other.isBlank() || other.equalsIgnoreCase(s.player()) || s.price() <= 0) continue;
            String payer = byPayer ? s.player() : other;
            String receiver = byPayer ? other : s.player();
            tally(tallies, s.player()).own = true;
            // One payment, two tellings: count it once.
            ArrayDeque<Payment> near = lately.computeIfAbsent(key(payer) + ">" + key(receiver) + ">" + s.price(),
                    k -> new ArrayDeque<>());
            while (!near.isEmpty() && s.at() - near.peekFirst().at > PAIR_MILLIS) near.pollFirst();
            Payment match = null;
            for (Payment p : near) {
                if (byPayer ? !p.toldByPayer : !p.toldByReceiver) {
                    match = p;
                    break;
                }
            }
            if (match == null) {
                match = new Payment();
                match.payer = payer;
                match.receiver = receiver;
                match.amount = s.price();
                match.at = s.at();
                near.addLast(match);
                payments.add(match);
            }
            if (byPayer) match.toldByPayer = true;
            else match.toldByReceiver = true;
        }

        Map<String, long[]> pairs = new LinkedHashMap<>();   // count, total, largest, lastAt
        Map<String, String[]> pairNames = new HashMap<>();
        long moved = 0;
        long hiddenMoved = 0;
        for (Payment p : payments) {
            Tally from = tally(tallies, p.payer);
            Tally to = tally(tallies, p.receiver);
            from.out += p.amount;
            from.paymentsOut++;
            from.payees.add(key(p.receiver));
            from.lastAt = Math.max(from.lastAt, p.at);
            to.in += p.amount;
            to.paymentsIn++;
            to.payers.add(key(p.payer));
            to.lastAt = Math.max(to.lastAt, p.at);
            moved += p.amount;
            // Told by the other side alone, this money is known only because
            // that notice named them.
            if (!p.toldByPayer) from.exposed += p.amount;
            if (!p.toldByReceiver) to.exposed += p.amount;
            // The receiver's telling of a payer we were already following: had
            // the payer's own been allowed out, it is the one that would have come.
            Long since = followedSince == null ? null : followedSince.get(key(p.payer));
            if (!p.toldByPayer && since != null && since < p.at) from.shownPrivate = true;
            String pair = key(p.payer) + ">" + key(p.receiver);
            long[] sum = pairs.computeIfAbsent(pair, k -> new long[4]);
            pairNames.putIfAbsent(pair, new String[] {from.name, to.name});
            sum[0]++;
            sum[1] += p.amount;
            sum[2] = Math.max(sum[2], p.amount);
            sum[3] = Math.max(sum[3], p.at);
        }

        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, long[]> e : pairs.entrySet()) {
            String[] names = pairNames.get(e.getKey());
            long[] v = e.getValue();
            edges.add(new Edge(names[0], names[1], (int) v[0], v[1], v[2], v[3]));
        }
        edges.sort(Comparator.comparingLong(Edge::total).reversed());

        List<Account> accounts = new ArrayList<>();
        int hidden = 0;
        int unfollowed = 0;
        for (Tally t : tallies.values()) {
            if (t.paymentsIn + t.paymentsOut == 0) continue;
            boolean followed = followedSince != null && followedSince.containsKey(key(t.name));
            Privacy privacy = t.own ? Privacy.OPEN : t.shownPrivate ? Privacy.HIDDEN
                    : followed ? Privacy.SILENT : Privacy.NOT_FOLLOWED;
            if (privacy == Privacy.HIDDEN) {
                hidden++;
                hiddenMoved += t.exposed;
            } else if (privacy == Privacy.NOT_FOLLOWED) {
                unfollowed++;
            }
            int both = 0;
            for (String payer : t.payers) if (t.payees.contains(payer)) both++;
            accounts.add(new Account(t.name, t.in, t.out, t.paymentsIn, t.paymentsOut, t.payers.size(),
                    t.payees.size(), both, t.trades, privacy, privacy == Privacy.OPEN ? 0 : t.exposed, t.lastAt,
                    shape(t.in, t.out, t.payers, t.payees, both)));
        }
        accounts.sort(Comparator.comparingLong(Account::moved).reversed());
        return new Snapshot(now, payments.size(), moved, hidden, hiddenMoved, unfollowed,
                List.copyOf(accounts), List.copyOf(edges));
    }

    private static Tally tally(Map<String, Tally> tallies, String name) {
        return tallies.computeIfAbsent(key(name), k -> {
            Tally t = new Tally();
            t.name = name;
            return t;
        });
    }

    static Shape shape(long in, long out, Set<String> payers, Set<String> payees, int bothWays) {
        if (payers.size() >= MANY && bothWays >= 3) return Shape.HUB;
        if (payers.size() >= MANY && out * 5 <= in) return Shape.COLLECTOR;
        if (payees.size() >= MANY && in * 5 <= out) return Shape.PAYS_MANY;
        if (in >= LARGE && out * 5 >= in * 4 && out * 4 <= in * 5 && !payers.containsAll(payees)) {
            return Shape.PASSES_ON;
        }
        if (out == 0) return Shape.RECEIVES;
        if (in == 0) return Shape.SENDS;
        return Shape.MIXED;
    }
}
