package dev.doughbay.fabric.staffwatch;

/** Passive counts only. No names, UUIDs, positions, packet bodies or extra server requests. */
public final class StaffPacketHealth {
    private long playerInfo, modeUpdates, combinedAdds, modeEntries, recognized, lastModeAt;
    private long nextReport;
    public synchronized void received(boolean updatesMode, boolean addsPlayer, int entries, long now) {
        playerInfo++;
        if (updatesMode) {
            modeUpdates++;
            modeEntries += Math.max(0, entries);
            if (addsPlayer) combinedAdds++;
            lastModeAt = now;
        }
    }
    public synchronized void recognizedMode() { recognized++; }
    /** Totals since module initialization; absence of mode updates does not prove absence of staff. */
    public synchronized String report(long now, int listedStaff, int spectators) {
        if (nextReport == 0) { nextReport = now + 60_000; return null; }
        if (now < nextReport) return null;
        nextReport = now + 60_000;
        return "Staff Watch detector health (since initialization): playerInfo=" + playerInfo
                + " modeUpdates=" + modeUpdates + " combinedAddMode=" + combinedAdds
                + " modeEntries=" + modeEntries + " recognizedModeChanges=" + recognized
                + " lastModeAgeMs=" + (lastModeAt > 0 ? Math.max(0, now-lastModeAt) : -1)
                + " listedStaff=" + Math.max(0,listedStaff) + " spectators=" + Math.max(0,spectators);
    }
}
