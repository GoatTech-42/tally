package dev.doughbay.fabric;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Keeps current evidence even when historical log lines are absent or crowded. */
public final class BugReportEvidence {
    private BugReportEvidence() { }
    public static String readForReport(Path path) {
        return readForReport(path,"");
    }
    public static String readForReport(Path path,String description) {
        ReportTopic topic=ReportTopic.infer(description);
        String context;
        try {context=DoughBayClient.automationSessionController().bugReportContext(System.currentTimeMillis());}
        catch(RuntimeException e){context="Current controller evidence: unavailable\n";}
        String log=BugReportLog.readForReport(path,topic);
        context+=FaultTrace.INSTANCE.report(System.currentTimeMillis());
        context+=ActionTrace.INSTANCE.report(System.currentTimeMillis(),topic);
        context+="ctx historical_counters_available="+!log.equals(BugReportLog.UNAVAILABLE)+"\n";
        return combine(context,log,topic);
    }
    static String combine(String context,String log) {
        return combine(context,log,ReportTopic.GENERAL);
    }
    private static String combine(String context,String log,ReportTopic topic) {
        if(context.getBytes(StandardCharsets.UTF_8).length>7168)throw new IllegalArgumentException("Controller context exceeds report budget");
        var lines=new java.util.ArrayDeque<>(java.util.List.of(log.split("\n")));
        String headers="";
        if(log.startsWith("Filtered GoNuts diagnostic excerpt;")){
            headers=lines.removeFirst()+"\n";
            if(!lines.isEmpty())headers+=lines.removeFirst()+"\n";
        }
        while(!lines.isEmpty() && (context+"\n"+headers+String.join("\n",lines)).getBytes(StandardCharsets.UTF_8).length>BugReportLog.MAX_BYTES){if(topic==ReportTopic.GENERAL)lines.removeFirst();else lines.removeLast();}
        return context+"\n"+headers+String.join("\n",lines);
    }
}
