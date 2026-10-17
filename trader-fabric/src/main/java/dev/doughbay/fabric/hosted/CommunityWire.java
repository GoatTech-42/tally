package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.*;

/** Closed upload schema. Never accepts raw lore, chat, descriptors, UUIDs or local identifiers. */
public final class CommunityWire {
    public static final URI BASE=URI.create("https://gonuts.at/community/v1/");
    public static final String CONSENT="share-observations-v1";
    public static boolean account(String name){return name!=null&&name.matches("\\.?[A-Za-z0-9_]{2,20}");}
    public static String owner(List<String> tooltip){
        if(tooltip==null)return "";
        var label="(?:seller(?: name)?|owner|order owner|ordered by|order by|placed by|created by|creator|listed by|sold by|buyer|player)";
        var pattern=java.util.regex.Pattern.compile("(?i)^\\s*[•*▸▶»›>-]*\\s*"+label+"(?:\\s*[:=»›–—-]\\s*|\\s+)(\\.?[A-Za-z0-9_]{2,20})\\s*$");
        var header=java.util.regex.Pattern.compile("(?i)^\\s*[•*▸▶»›>-]*\\s*"+label+"\\s*[:=»›–—-]\\s*$");
        for(int i=0;i<tooltip.size();i++){String line=tooltip.get(i);if(line==null)continue;line=line.replaceAll("§.","").strip();var match=pattern.matcher(line);if(match.matches())return match.group(1);
            if(header.matcher(line).matches()&&i+1<tooltip.size()){String next=tooltip.get(i+1);if(next!=null){next=next.replaceAll("§.","").strip();if(account(next))return next;}}
        }
        return "";
    }
    public static String regionLabel(List<String> lines){
        String result="";
        var pattern=java.util.regex.Pattern.compile("(?i)^\\s*(?:server|shard|region)\\s*[:»›=]\\s*(NA[ -]?(?:West|East)|EU[ -]?(?:West|Central)|Asia|Oceania|Europe|West|East)[ -]*([0-9]{1,3})\\s*$");
        for(String line:lines){if(line==null)continue;var match=pattern.matcher(line.replaceAll("§.",""));if(!match.matches())continue;
            String name=match.group(1).toLowerCase(Locale.ROOT).replaceAll("[ -]+","-");if(name.equals("west")||name.equals("east"))name="na-"+name;
            if(name.equals("nawest"))name="na-west";if(name.equals("naeast"))name="na-east";if(name.equals("euwest"))name="eu-west";if(name.equals("eucentral"))name="eu-central";
            String value=name+"-"+Integer.parseInt(match.group(2));if(!result.isEmpty()&&!result.equals(value))return "";result=value;
        }return result;
    }
    public static String key(String item,int stack){
        if(item==null||stack<1||stack>64)throw new IllegalArgumentException();
        String[] split=item.split("#",2);if(!split[0].matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException();
        if(split.length>1&&!split[1].matches("[a-f0-9]{16,64}"))throw new IllegalArgumentException();
        return split[0]+"|"+stack+(split.length>1?"#"+split[1].substring(0,16):"");
    }
    public static ObjectNode follow(String actor,String kind,String item,String itemName,int quantity,long total,long at){
        if(!account(actor)||!Set.of("listed","ordered","bought").contains(kind)||quantity<1||quantity>1_000_000||!money(total))return null;
        var row=event("follow_trade",at).put("actor",actor).put("kind",kind).put("quantity",quantity).put("total",total);
        if(item!=null&&!item.isBlank()&&quantity<=64){try{row.put("itemKey",key(item,quantity));}catch(IllegalArgumentException e){return null;}}
        else if(itemName!=null&&itemName.matches("[A-Za-z0-9' -]{1,48}"))row.put("itemName",itemName);
        else return null;
        return row;
    }
    public static ObjectNode payment(String payer,String recipient,long amount,long at){
        return account(payer)&&account(recipient)&&money(amount)?event("payment",at).put("payer",payer).put("recipient",recipient).put("amount",amount):null;
    }
    public static ObjectNode sale(String account,String item,int count,long price,long at){
        if(!account(account)||count<=0||count>1_000_000||!money(price))return null;
        try{return event("sale",at).put("account",account).put("itemKey",key(item,Math.min(count,64))).put("quantity",count).put("price",price);}
        catch(IllegalArgumentException e){return null;}
    }
    public static ObjectNode ask(String seller,String item,int stack,long total,long at,String scan){
        if(!account(seller)||!money(total)||stack<1||total<stack)return null;
        try{return event("ask",at).put("seller",seller).put("itemKey",key(item,stack)).put("stack",stack).put("unitPrice",total/stack).put("coverage","partial").put("scanId",scan);}
        catch(IllegalArgumentException e){return null;}
    }
    public static ObjectNode priceLevel(String item,int stack,long total,long at,String source){
        if(!money(total)||stack<1||total<stack||!Set.of("probe","page").contains(source))return null;
        try{return event("price_level",at).put("itemKey",key(item,stack)).put("stack",stack).put("unitPrice",total/stack).put("source",source);}
        catch(IllegalArgumentException e){return null;}
    }
    public record Estimate(double unitPrice,String source,boolean replaceable){}
    /** Price-only context is never promoted to actionable ask evidence, regardless of server fields. */
    public static Estimate estimate(com.fasterxml.jackson.databind.JsonNode answer){
        var asks=answer.path("asks");double price=Double.POSITIVE_INFINITY;
        for(var row:asks.path("rows")){if(row.has("sellerOwnIndex"))continue;double n=row.path("unitPrice").asDouble();if(Double.isFinite(n)&&n>0)price=Math.min(price,n);}
        if(Double.isFinite(price))return new Estimate(price,"asks",!Set.of("mixed","unknown","").contains(answer.path("regionScope").asText())&&asks.path("usability").asText().equals("replace_scan"));
        var levels=answer.path("priceLevels");if(levels.path("usability").asText().equals("expired"))return null;
        for(var row:levels.path("rows")){double n=row.path("unitPrice").asDouble();if(Double.isFinite(n)&&n>0)price=Math.min(price,n);}
        return Double.isFinite(price)?new Estimate(price,"priceLevels",false):null;
    }
    public static ObjectNode orderLevel(String item,int stack,long unit,long total,long delivered,long at,int page){
        if(!money(unit)||total<1||total>1_000_000_000_000L||delivered<0||delivered>total||page<1)return null;
        try{return event("order_level",at).put("itemKey",key(item,stack)).put("unitPrice",unit).put("total",total)
                .put("delivered",delivered).put("depth",1).put("page",page).put("source","page");}
        catch(IllegalArgumentException e){return null;}
    }
    public static ObjectNode order(String owner,String item,int stack,long unit,long total,long delivered,long at,String sweep,int seq){
        if(!account(owner)||!money(unit)||total<1||total>1_000_000_000_000L||delivered<0||delivered>total)return null;
        try{return event("order",at).put("owner",owner).put("itemKey",key(item,stack)).put("unitPrice",unit).put("total",total).put("delivered",delivered).put("sweepId",sweep).put("sweepSeq",seq).put("scope","full");}
        catch(IllegalArgumentException e){return null;}
    }
    public static ObjectNode event(String type,long at){return DashboardProtocol.JSON.createObjectNode().put("type",type).put("observedAt",at);}
    private static boolean money(long n){return n>0&&n<=1_000_000_000_000L;}
    private CommunityWire(){}
}
