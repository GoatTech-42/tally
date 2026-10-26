package dev.doughbay.fabric;
import java.util.Map;
/** Legacy routes remain inert so old browser tabs cannot collect or send reports. */
public final class WebReports {
    private WebReports() {}
    public static void sent(String fingerprint) {}
    public static void observe(AutomaticReportDetector.Observation observation,long now,String build) {}
    public static String data(){return "{\"alert\":false,\"retired\":true,\"notice\":\"Public reporting is retired\"}";}
    public static String change(Map<String,String> form){return "{\"ok\":false,\"retired\":true,\"note\":\"Public services retired; local trading remains available\"}";}
}
