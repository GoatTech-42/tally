package dev.doughbay.fabric.staffwatch;

/** Passive counts only. No names, UUIDs, positions, packet bodies or extra server requests. */
public final class StaffPacketHealth {
    private long playerInfo, modeUpdates, combinedAdds, modeEntries, recognized, lastModeAt;
    private long combinedStaffEntries, combinedStaffSpectators, combinedStaffChanges;
    private final java.util.LinkedHashMap<java.util.UUID, String> combinedStaffModes = new java.util.LinkedHashMap<>();
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
    /** Observational only: a global player-list mode is never local staff proof. */
    public synchronized void combinedStaff(java.util.UUID id, String mode) {
        if (id == null || mode == null || !java.util.Set.of("survival", "creative", "adventure", "spectator").contains(mode)) return;
        combinedStaffEntries++;
        if (mode.equals("spectator")) combinedStaffSpectators++;
        String before = combinedStaffModes.put(id, mode);
        if (before != null && !before.equals(mode)) combinedStaffChanges++;
        while (combinedStaffModes.size() > 64) combinedStaffModes.remove(combinedStaffModes.keySet().iterator().next());
    }
    public synchronized void resetStaffBaselines() { combinedStaffModes.clear(); }
    /** Totals since module initialization; absence of mode updates does not prove absence of staff. */
    public synchronized String report(long now, int listedStaff, int spectators) {
        if (nextReport == 0) { nextReport = now + 60_000; return null; }
        if (now < nextReport) return null;
        nextReport = now + 60_000;
        return "Staff Watch detector health (since initialization): playerInfo=" + playerInfo
                + " modeUpdates=" + modeUpdates + " combinedAddMode=" + combinedAdds
                + " modeEntries=" + modeEntries + " recognizedModeChanges=" + recognized
                + " lastModeAgeMs=" + (lastModeAt > 0 ? Math.max(0, now-lastModeAt) : -1)
                + " listedStaff=" + Math.max(0,listedStaff) + " spectators=" + Math.max(0,spectators)
                + " combinedStaffEntries=" + combinedStaffEntries + " combinedStaffSpectators=" + combinedStaffSpectators
                + " combinedStaffModeChanges=" + combinedStaffChanges;
    }
}
