package dev.doughbay.fabric;

import java.util.*;
import java.util.regex.Pattern;

/** Closed structural evidence only. This never decides whether a tile is an order or can be clicked. */
final class OwnOrderRejectionEvidence {
    static final List<String> WORDS=List.of("max","orders","buy","donut","slots","more","upgrade","unlock","locked","empty","new","create","place","no","found","available","here","page","requested","delivered","each","price","amount","quantity","order","limit","reached","click","not","per");
    private static final Pattern REQUESTED=Pattern.compile("(?i)([0-9][0-9.,]*\\s*[kmb]?)\\s+requested");
    private static final Pattern PRICE=Pattern.compile("(?i)\\$\\s*([0-9][0-9.,]*)\\s*([kmb]?)\\s+each");
    private static final Pattern DELIVERED=Pattern.compile("(?i)([0-9][0-9.,]*[kmb]?)\\s*/\\s*([0-9][0-9.,]*[kmb]?)\\s+delivered");
    record Shape(int nameMask,int loreMask,int itemMask,int parseMask) {}
    private int nameMask,loreMask,itemMask,parseMask;
    private final Set<Shape> shapes=new HashSet<>();
    private long at;
    static int words(String text) {
        Set<String> tokens=new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^a-z]+")));
        int mask=0;for(int i=0;i<WORDS.size();i++)if(tokens.contains(WORDS.get(i)))mask|=1<<i;
        return mask;
    }
    static Shape shape(String item,String name,List<String> lore,boolean unsupportedQuantity) {
        String text=String.join(" ",lore),lower=text.toLowerCase(Locale.ROOT);
        String combined=(name+" "+text).toLowerCase(Locale.ROOT);
        int parse=(name.isBlank()?1:0)|(lore.isEmpty()?2:0)|(lower.contains("requested")?4:0)
                |(REQUESTED.matcher(text).find()?8:0)|(lower.contains("each")?16:0)|(PRICE.matcher(text).find()?32:0)
                |(lower.contains("delivered")?64:0)|(DELIVERED.matcher(text).find()?128:0)
                |(name.indexOf('§')>=0||text.indexOf('§')>=0?256:0)
                |(name.indexOf('\u00a0')>=0||text.indexOf('\u00a0')>=0?512:0)|(unsupportedQuantity?1024:0)
                |(Pattern.compile("donut\\+(?!\\+)").matcher(combined).find()?2048:0)
                |(combined.contains("donut++")?4096:0);
        int kind=item.equals("minecraft:barrier")?1:item.endsWith("glass_pane")?2:item.endsWith("glass")?4:
                item.equals("minecraft:paper")?8:item.equals("minecraft:air")?32:item.equals("minecraft:player_head")?64:16;
        return new Shape(words(name),words(text),kind,parse);
    }
    void add(String item,String name,List<String> lore,boolean unsupportedQuantity) {
        Shape s=shape(item,name,lore,unsupportedQuantity);
        nameMask|=s.nameMask();loreMask|=s.loreMask();itemMask|=s.itemMask();parseMask|=s.parseMask();
        if(shapes.size()<45)shapes.add(s);
    }
    void capturedAt(long now){at=now;}
    String contextLine() {
        return "ctx own_read_reject_name_mask="+nameMask+" own_read_reject_lore_mask="+loreMask
                +" own_read_reject_item_mask="+itemMask+" own_read_reject_parse_mask="+parseMask
                +" own_read_reject_shapes="+shapes.size()+" own_read_reject_at_ms="+at;
    }
}
