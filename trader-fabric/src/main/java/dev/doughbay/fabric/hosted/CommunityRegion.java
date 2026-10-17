package dev.doughbay.fabric.hosted;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import java.util.*;

/** Explicit server-provided labels only. No domains, coordinates or map-cell estimates. */
public final class CommunityRegion {
    private record Signal(String name,Object connection,Object level,long at){}
    private static volatile Signal tab=new Signal("",null,null,0),current=tab;
    private static long nextTick;
    public static void tab(String header,String footer){
        var mc=Minecraft.getInstance();if(!donut(mc))return;
        var lines=new ArrayList<String>();if(header!=null)lines.addAll(Arrays.asList(header.split("\\R")));if(footer!=null)lines.addAll(Arrays.asList(footer.split("\\R")));
        tab=new Signal(CommunityWire.regionLabel(lines),mc.getConnection(),mc.level,System.currentTimeMillis());
    }
    public static void tick(Minecraft mc){
        long now=System.currentTimeMillis();if(!donut(mc)){current=new Signal("",null,null,0);return;}
        if(now<nextTick)return;nextTick=now+2000;
        var lines=new ArrayList<String>();
        try{var board=mc.level.getScoreboard();var sidebar=board.getDisplayObjective(DisplaySlot.SIDEBAR);if(sidebar!=null){lines.add(sidebar.getDisplayName().getString());
            for(var entry:board.listPlayerScores(sidebar)){if(entry.isHidden())continue;var team=board.getPlayersTeam(entry.owner());String line=PlayerTeam.formatNameForTeam(team,entry.ownerName()).getString();if(entry.display()!=null)line+=" "+entry.display().getString();lines.add(line);}}
        }catch(RuntimeException ignored){}
        String sidebar=CommunityWire.regionLabel(lines);var seen=tab;
        String tabLabel=seen.connection()==mc.getConnection()&&seen.level()==mc.level?seen.name():"";
        String name=!sidebar.isEmpty()&&!tabLabel.isEmpty()&&!sidebar.equals(tabLabel)?"":sidebar.isEmpty()?tabLabel:sidebar;
        current=new Signal(name,mc.getConnection(),mc.level,now);
    }
    public static boolean donut(Minecraft mc){return mc!=null&&mc.level!=null&&mc.player!=null&&mc.getConnection()!=null&&!mc.hasSingleplayerServer()&&mc.getCurrentServer()!=null&&dev.doughbay.fabric.staffwatch.DonutServer.matches(mc.getCurrentServer().ip);}
    public static String current(){var signal=current;return System.currentTimeMillis()-signal.at()<15_000?signal.name():"";}
    public static String current(Minecraft mc){
        var signal=current;long age=System.currentTimeMillis()-signal.at();
        return donut(mc)&&signal.connection()==mc.getConnection()&&signal.level()==mc.level&&age>=0&&age<15_000?signal.name():"";
    }
    private CommunityRegion(){}
}
