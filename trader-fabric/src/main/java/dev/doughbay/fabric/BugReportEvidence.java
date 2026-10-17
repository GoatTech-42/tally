package dev.doughbay.fabric;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Keeps current evidence even when historical log lines are absent or crowded. */
public final class BugReportEvidence {
    private BugReportEvidence() { }
    public static String readForReport(Path path) {
        String context;
        try {context=DoughBayClient.automationSessionController().bugReportContext(System.currentTimeMillis());}
        catch(RuntimeException e){context="Current controller evidence: unavailable\n";}
        String log=BugReportLog.readForReport(path);
        context+="ctx historical_counters_available="+!log.equals(BugReportLog.UNAVAILABLE)+"\n";
        return combine(context,log);
    }
    static String combine(String context,String log) {
        if(context.getBytes(StandardCharsets.UTF_8).length>3072)throw new IllegalArgumentException("Controller context exceeds report budget");
        var lines=new java.util.ArrayDeque<>(java.util.List.of(log.split("\n")));
        while(!lines.isEmpty() && (context+"\n"+String.join("\n",lines)).getBytes(StandardCharsets.UTF_8).length>BugReportLog.MAX_BYTES)lines.removeFirst();
        return context+"\n"+String.join("\n",lines);
    }
}
