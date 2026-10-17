package dev.doughbay.fabric.hosted;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
/** One persisted source request per attempt; independent of the local menu. */
public final class IsolatedOrderSource {
    private boolean attempted;
    public void reset(){attempted=false;}
    public String request(String reason,String own,List<String> peers,boolean guardsClear,Predicate<String> post){
        if(!Set.of("stale_read","self_read","no_full_read").contains(reason))return "source_not_needed";
        if(!guardsClear)return "source_guard_wait";
        if(attempted)return "source_already_requested";
        attempted=true;
        if(own==null||own.isBlank())return "source_no_peer";
        for(String peer:peers)if(peer!=null&&!peer.isBlank()&&!peer.equalsIgnoreCase(own))
            return post.test(peer)?"source_queued":"source_write_failed";
        return "source_no_peer";
    }
}
