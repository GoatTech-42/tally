package dev.doughbay.fabric;

import java.util.*;
import java.util.function.Function;

/** Per-item preference among eligible new trades; never an eligibility or spending override. */
public final class ItemPriorities {
    public static final String KEY = "items.priority";
    public enum Level {
        LOW("Low",0), STANDARD("Standard",1), HIGH("High",2);
        private final String label; private final int rank;
        Level(String label,int rank){this.label=label;this.rank=rank;}
        public String label(){return label;}
        public int rank(){return rank;}
    }
    private record Parsed(String text,Map<String,Level> entries){}
    private static volatile Parsed cached=new Parsed("",Map.of());
    private ItemPriorities(){}
    private static String item(String raw){
        String id=Tuning.normalizeItem(raw);
        if(id.startsWith("="))id=id.substring(1);
        return id.matches("[a-z0-9_]{1,128}")?id:"";
    }
    private static Map<String,Level> parse(String raw){
        Map<String,Level> out=new TreeMap<>();
        if(raw.length()>65536)return Map.of();
        for(String entry:raw.split("[,;\r\n]+")){
            int at=entry.lastIndexOf('=');if(at<1)continue;
            String id=item(entry.substring(0,at));if(id.isEmpty())continue;
            Level level;
            try{level=Level.valueOf(entry.substring(at+1).strip().toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException ignored){continue;}
            if(level==Level.STANDARD)out.remove(id);else out.put(id,level);
        }
        return Map.copyOf(out);
    }
    private static Map<String,Level> entries(){
        String raw=Tuning.text(KEY);Parsed old=cached;
        if(old.text().equals(raw))return old.entries();
        synchronized(ItemPriorities.class){
            old=cached;if(!old.text().equals(raw))cached=new Parsed(raw,parse(raw));
            return cached.entries();
        }
    }
    public static Level level(String id){return entries().getOrDefault(item(id),Level.STANDARD);}
    /** Negative means a is preferred. Equal levels retain the caller's existing ranking. */
    public static int compare(String a,String b){return Integer.compare(level(b).rank(),level(a).rank());}
    public static <T> void prioritize(List<T> values,Function<T,String> id){values.sort((a,b)->compare(id.apply(a),id.apply(b)));}
    public static void set(Collection<String> ids,Level level){
        Objects.requireNonNull(ids);Objects.requireNonNull(level);
        Map<String,Level> next=new TreeMap<>(entries());
        for(String raw:ids){String id=item(raw);if(id.isEmpty())continue;if(level==Level.STANDARD)next.remove(id);else next.put(id,level);}
        StringJoiner text=new StringJoiner(", ");next.forEach((id,value)->text.add(id+"="+value.name().toLowerCase(Locale.ROOT)));
        if(!text.toString().equals(Tuning.text(KEY)))Tuning.setText(KEY,text.toString());
    }
}
