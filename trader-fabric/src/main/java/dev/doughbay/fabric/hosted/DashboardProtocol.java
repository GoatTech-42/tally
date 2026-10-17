package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Fixed destination and allowlisted wire projection; no raw log, detail or action text. */
public final class DashboardProtocol {
    public static final ObjectMapper JSON=new ObjectMapper();
    public static final URI PAGE=URI.create("https://gonuts.at/dashboard/");
    public static final URI REDEEM=URI.create("https://gonuts.at/dashboard/api/pair/redeem");
    public static final URI CHECK=URI.create("https://gonuts.at/dashboard/api/client/check");
    public static final URI SOCKET=URI.create("wss://gonuts.at/dashboard/ws/client");
    public static final int MAX_BYTES=32768;
    public static final int MAX_PANEL_FRAME_BYTES=768*1024;
    public static final List<String> CAPABILITIES=List.of("pause","resume","snapshot","panel","swarm_enable","swarm_disable","swarm_rank");
    private DashboardProtocol(){}
    public static String code(String input){
        String value=Objects.requireNonNullElse(input,"").toUpperCase(Locale.ROOT).replace("-","").replace(" ","").replace('0','O').replace('1','L').replace('I','L');
        if(!value.matches("[A-Z2-9]{8}"))throw new IllegalArgumentException("Enter the eight-character pairing code");
        return value.substring(0,4)+"-"+value.substring(4);
    }
    public static String label(String input){
        String value=Objects.requireNonNullElse(input,"").strip();
        // No local paths, mention/chat syntax or credential-looking labels reach the relay.
        if(value.length()>48||!value.matches("[A-Za-z0-9 _().-]{1,48}")||value.matches(".*(?:gni1_|dbay_|Bearer|[A-Za-z0-9_-]{40,}).*"))return "GoNuts client";
        return value;
    }
    public static String build(String value){return value!=null&&value.matches("[0-9A-Za-z.+_-]{0,60}")?value:"";}
    public static ObjectNode hello(String build,String name,String hive){
        var j=JSON.createObjectNode().put("type","hello").put("build",build(build)).put("name",label(name)).put("hive",hive);
        j.putArray("protocols").add(1);var a=j.putArray("capabilities");CAPABILITIES.forEach(a::add);return j;
    }
    public static ObjectNode pairing(String code,String name,String build,String hive){var j=hello(build,name,hive);j.remove("type");j.put("code",code(code));return j;}
    public static ObjectNode telemetry(long at,String state,String account,long listed,long slots,long bank,long trades,String build,boolean update,long actionAt){
        Set<String> states=Set.of("SCANNING","BID_DESK","PREPARING_LIST","LISTING","MONITORING","COOLDOWN","PAUSED","STOPPED","IDLE","BUYING","REPRICING","RECOVERING","EVADING");
        // Keep the existing relay state vocabulary while describing native read operations accurately.
        String projected="READING_ORDERS".equals(state)?"SCANNING":"AUDITING_SLOTS".equals(state)?"MONITORING":state;
        String known=states.contains(projected)?projected:"UNKNOWN";
        String description="READING_ORDERS".equals(state)?"Reading orders":"AUDITING_SLOTS".equals(state)?"Checking own listings":activity(known);
        var j=JSON.createObjectNode().put("type","telemetry").put("at",at).put("state",known).put("detail",description)
            .put("account",label(account)).put("listed",count(listed)).put("slots",count(slots)).put("bank",money(bank)).put("trades",count(trades)).put("build",build(build))
            .put("lastAction",description).put("lastActionAt",actionAt);
        j.putObject("update").put("available",update).put("latest","");j.putObject("report").put("prepared",false).put("kind","");
        j.putObject("controls").put("pause",!known.equals("PAUSED")&&!known.equals("STOPPED"))
            .put("resume",known.equals("PAUSED")||known.equals("STOPPED")).put("snapshot",true).put("send_crash_report",false);
        return j;
    }
    private static String activity(String state){return switch(state){case "SCANNING"->"Evaluating configured trades";case "BID_DESK"->"Managing orders";case "BUYING"->"Purchasing";case "PREPARING_LIST","LISTING"->"Listing stock";case "MONITORING"->"Monitoring listings";case "REPRICING"->"Repricing stock";case "PAUSED"->"Trading paused";case "STOPPED"->"Trading stopped";case "EVADING"->"Connection recovery";case "RECOVERING"->"Recovering retained stock";case "COOLDOWN"->"Waiting for cooldown";default->"Idle";};}
    public static long count(long value){return Math.max(0,Math.min(1_000_000,value));}
    public static long money(long value){return Math.max(-1_000_000_000_000L,Math.min(1_000_000_000_000L,value));}
    public static JsonNode parse(String text)throws Exception{
        int bytes=text.getBytes(StandardCharsets.UTF_8).length;
        if(bytes>MAX_PANEL_FRAME_BYTES)throw new IllegalArgumentException("Oversized frame");
        JsonNode j=JSON.readTree(text);if(!j.isObject())throw new IllegalArgumentException("Invalid frame");
        if(bytes>MAX_BYTES&&!j.path("type").asText().equals("panel"))throw new IllegalArgumentException("Oversized frame");
        return j;
    }
    public static long backoff(int attempt,double jitter){long base=Math.min(60_000,1000L<<Math.min(6,Math.max(0,attempt)));return Math.round(base*Math.max(.7,Math.min(1.3,jitter)));}
    public static String pairError(int code){return switch(code){case 400->"Check the pairing code and client label";case 404->"Code not found; check it and try again";case 409->"Code already used; request a new code";case 410->"Code expired; request a new code";case 429->"Too many attempts; wait before trying again";case 503->"The dashboard is currently disabled";default->"Could not link; try again later";};}
}
