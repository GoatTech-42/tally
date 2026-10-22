package dev.doughbay.fabric.staffwatch;

import java.util.concurrent.atomic.LongAdder;

/** Count-only diagnostics appended to the existing minute report; no extra probes or identities. */
public final class AntiVanishHealth {
    private final LongAdder bundles = new LongAdder(), modePackets = new LongAdder(), initialModes = new LongAdder();
    private final LongAdder queuedModes = new LongAdder(), requests = new LongAdder(), replies = new LongAdder();
    private final LongAdder names = new LongAdder(), knownStaff = new LongAdder(), hiddenCandidates = new LongAdder();
    private final LongAdder alerts = new LongAdder();

    public void bundle() { bundles.increment(); }
    public void modePacket(boolean initial) { modePackets.increment(); if (initial) initialModes.increment(); }
    public void queuedMode() { queuedModes.increment(); }
    public void probeRequest() { requests.increment(); }
    public void probeReply(int count) { replies.increment(); names.add(Math.max(0, count)); }
    public void reviewedProbe(int known, int hidden) { knownStaff.add(Math.max(0, known)); hiddenCandidates.add(Math.max(0, hidden)); }
    public void alertRequest() { alerts.increment(); }

    public String summary() {
        return "avBundles=" + bundles.sum() + " avModePackets=" + modePackets.sum()
                + " avInitialModePacketsIgnored=" + initialModes.sum() + " avModesQueued=" + queuedModes.sum()
                + " avProbeAttempts=" + requests.sum() + " avProbeReplies=" + replies.sum()
                + " avProbeNames=" + names.sum() + " avProbeKnownStaff=" + knownStaff.sum()
                + " avProbeHiddenCandidates=" + hiddenCandidates.sum() + " avAlertRequests=" + alerts.sum();
    }
}
