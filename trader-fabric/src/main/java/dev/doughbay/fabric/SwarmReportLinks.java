package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private optional report capability frozen to the reviewed evidence, never part of the public body. */
final class SwarmReportLinks {
    static final String NOTICE="When available, this report privately links to your Swarm client\'s last 24 hours of sanitized diagnostics and observed numeric settings. This history excludes usernames and chat.";
    private record Link(String ticket,long at) {}
    private static final Map<String,Link> links=new LinkedHashMap<>();
    private SwarmReportLinks(){}
    static synchronized void remember(String evidence,String ticket,long now){
        links.entrySet().removeIf(e->now<e.getValue().at||now-e.getValue().at>=DiagnosticHistory.RETENTION_MS);
        if(evidence==null||evidence.isEmpty()||ticket==null||!ticket.matches("drt_[A-Za-z0-9_-]{32,128}"))return;
        while(links.size()>=64)links.remove(links.keySet().iterator().next());
        links.putIfAbsent(hash(evidence),new Link(ticket,now));
    }
    static boolean supported(JsonNode capabilities){
        var h=capabilities.path("diagnosticHistory");return h.path("supported").asBoolean(false)
                &&h.path("version").isIntegralNumber()&&h.path("version").asInt()==1
                &&h.path("header").asText().equals("X-GoNuts-Swarm-Report");
    }
    static synchronized HttpRequest attach(HttpRequest original,String evidence,JsonNode capabilities,long now){
        if(!supported(capabilities)||evidence==null)return original;
        Link link=links.get(hash(evidence));
        if(link==null||now<link.at||now-link.at>=DiagnosticHistory.RETENTION_MS)return original;
        if(!original.uri().getScheme().equals("https")||!original.uri().getHost().equals("gonuts.at")
                ||!(original.uri().getPath().equals("/v1/bugs")||original.uri().getPath().matches("/v1/bugs/[1-9][0-9]*/evidence")))return original;
        return HttpRequest.newBuilder(original,(key,value)->!key.equalsIgnoreCase("X-GoNuts-Swarm-Report"))
                .header("X-GoNuts-Swarm-Report",link.ticket).build();
    }
    private static String hash(String evidence){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(evidence.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
