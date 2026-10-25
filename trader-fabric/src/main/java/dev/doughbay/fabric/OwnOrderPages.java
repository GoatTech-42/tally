package dev.doughbay.fabric;

import java.util.*;
import dev.doughbay.fabric.AutomatedExecutionDriver.OwnOrderRow;

/** Bounded native page traversal. Delivery updates alone never prove a page turn. */
final class OwnOrderPages {
    static final int MAX_PAGES=20;
    enum Turn { READY, WAITING, FAILED }
    private final Set<String> seen=new HashSet<>();
    private final List<OwnOrderRow> rows=new ArrayList<>();
    private boolean pending;
    private int previousWindow;
    private String previous="",candidate="";
    private long requestedAt,candidateAt;
    void reset(){seen.clear();rows.clear();pending=false;candidate="";}
    static String fingerprint(List<OwnOrderRow> rows){
        // Exclude changing delivery counts and expiry countdowns.
        return rows.stream().map(r->r.itemKey()+"|"+r.requested()+"|"+r.unitPrice()).sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
    }
    boolean append(List<OwnOrderRow> page){
        if(seen.size()>=MAX_PAGES || !seen.add(fingerprint(page)))return false;
        rows.addAll(page);return true;
    }
    void requested(int window,String frame,long now){
        pending=true;previousWindow=window;previous=frame;requestedAt=now;candidate="";
    }
    boolean pending(){return pending;}
    static boolean atEnd(int rowCount,int firstEmpty,boolean hasNext){
        return rowCount>=0&&rowCount<=45&&(!hasNext||rowCount==0||rowCount<45&&firstEmpty>=rowCount);
    }
    Turn observe(int window,String frame,boolean controlsReady,long now){
        if(!pending){
            if(!seen.isEmpty())return Turn.READY;
            if(!controlsReady)return Turn.WAITING;
            String initial=window+"|"+frame;
            if(!initial.equals(candidate)){candidate=initial;candidateAt=now;return Turn.WAITING;}
            return now-candidateAt>=750?Turn.READY:Turn.WAITING;
        }
        if(now-requestedAt>=12000)return Turn.FAILED;
        if(!controlsReady || window==previousWindow || previous.equals(frame))return Turn.WAITING;
        String next=window+"|"+frame;
        if(!next.equals(candidate)){candidate=next;candidateAt=now;return Turn.WAITING;}
        if(now-candidateAt<750)return Turn.WAITING;
        pending=false;return Turn.READY;
    }
    List<OwnOrderRow> rows(){return List.copyOf(rows);}
    boolean hasTurnedPage(){return seen.size()>1;}
}
