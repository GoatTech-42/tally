package dev.doughbay.fabric;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What each build changed, read from the notes written when it was released.
 *
 * <p>An entry is a heading with the time it was written and the lines under
 * it:
 *
 * <pre>
 * ## 2026-10-11 12:20 UTC
 * - New: a thing
 * - Fixed: another
 * </pre>
 *
 * <p>The builds all carry one version number, so time is what orders them. An
 * entry is written before its build is made, which puts it after the build
 * before: "newer than the build I am" is exactly the entries a player has not
 * got yet. Anything in the text that is not such an entry is passed over, so
 * the same file can open with a title and the release notes can say more.
 */
public final class Changelog {
    private static final Pattern HEADING = Pattern.compile(
            "^##\\s+(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2})(?:\\s*UTC)?\\s*$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+(.+?)\\s*$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    private Changelog() { }

    /** One release's notes. {@code at} is when they were written, in epoch milliseconds. */
    public record Entry(long at, List<String> lines) {
        /** The time as the player's own clock has it: "Oct 11, 5:20 AM". */
        public String when(ZoneId zone) {
            return SHOWN.format(Instant.ofEpochMilli(at).atZone(zone));
        }
    }

    /** Every entry in {@code text}, the newest first. Text that is not an entry is ignored. */
    public static List<Entry> parse(String text) {
        List<Entry> entries = new ArrayList<>();
        if (text == null || text.isBlank()) return entries;
        long at = -1;
        List<String> lines = null;
        for (String raw : text.split("\\r?\\n")) {
            Matcher heading = HEADING.matcher(raw.strip());
            if (heading.matches()) {
                if (lines != null && !lines.isEmpty()) entries.add(new Entry(at, List.copyOf(lines)));
                try {
                    at = LocalDateTime.parse(heading.group(1) + " " + heading.group(2), STAMP)
                            .toInstant(ZoneOffset.UTC).toEpochMilli();
                    lines = new ArrayList<>();
                } catch (RuntimeException e) {
                    lines = null;
                }
                continue;
            }
            if (raw.startsWith("#")) {
                // Some other heading: whatever follows is not this entry's.
                if (lines != null && !lines.isEmpty()) entries.add(new Entry(at, List.copyOf(lines)));
                lines = null;
                continue;
            }
            if (lines == null) continue;
            Matcher bullet = BULLET.matcher(raw);
            if (bullet.matches()) {
                lines.add(plain(bullet.group(1)));
            } else if (!raw.isBlank() && !lines.isEmpty() && Character.isWhitespace(raw.charAt(0))) {
                // A bullet carried onto the next line.
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + " " + plain(raw.strip()));
            }
        }
        if (lines != null && !lines.isEmpty()) entries.add(new Entry(at, List.copyOf(lines)));
        entries.sort(Comparator.comparingLong(Entry::at).reversed());
        return entries;
    }

    /** The entries written after {@code since}, the newest first. */
    public static List<Entry> after(List<Entry> entries, long since) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.at() > since) out.add(e);
        return out;
    }

    /** Markdown's emphasis and code marks mean nothing on the game's screen. */
    private static String plain(String text) {
        return text.replace("**", "").replace("`", "");
    }
}
