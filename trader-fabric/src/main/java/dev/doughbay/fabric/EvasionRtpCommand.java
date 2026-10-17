package dev.doughbay.fabric;

import java.util.Collection;

/** A direct RTP route only when both current region and server completions agree. */
public final class EvasionRtpCommand {
    private EvasionRtpCommand() {}
    public static String resolve(String serverLabel, Collection<String> advertised) {
        if (serverLabel == null || advertised == null) return "rtp";
        var label = java.util.regex.Pattern.compile("^(na-west|na-east|eu-west|eu-central|asia|oceania)-[0-9]{1,3}$").matcher(serverLabel);
        if (!label.matches()) return "rtp";
        String region = switch (label.group(1)) {
            case "na-west" -> "west";
            case "na-east" -> "east";
            case "eu-west" -> "eu west";
            case "eu-central" -> "eu central";
            default -> label.group(1);
        };
        return advertised.contains(region) ? "rtp " + region : "rtp";
    }
}
