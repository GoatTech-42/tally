package dev.doughbay.fabric.hosted;

import dev.doughbay.fabric.OrderBook.Order;
import java.util.*;

/** Historical guidance chooses checks, never supplies executable prices or ownership. */
public final class CommunityMarketGuidance {
    public static final long HISTORY_TTL=900_000, LIVE_TTL=30_000, OWN_TTL=60_000;
    public record Hint(String itemId,long historicalBid,String evidence,boolean ownMarket){}
    public record Check(String status,long historicalBid,long liveBid,long proposedUnit,
                        boolean ownOrdersFresh,boolean priceChanged,boolean placementStillRequiresValidation){}
    public static List<Hint> plan(List<Order> history,Set<String> followed,Set<String> allowed,
                                  Set<String> ownMarkets,Map<String,Long> ceilings,long now,int limit){
        var bids=new HashMap<String,Long>();
        for(var row:history)if(row.parts().isEmpty()&&!row.itemKey().contains("#")&&row.remaining()>0
                &&row.unitPrice()>0&&row.observedAt()>0&&row.observedAt()<=now&&now-row.observedAt()<=HISTORY_TTL
                &&allowed.contains(row.itemId()))bids.merge(row.itemId(),row.unitPrice(),Math::max);
        var items=new HashSet<>(bids.keySet());
        for(String item:followed)if(allowed.contains(item)&&!item.contains("#"))items.add(item);
        var hints=new ArrayList<Hint>();
        for(String item:items)hints.add(new Hint(item,bids.getOrDefault(item,0L),
                followed.contains(item)?bids.containsKey(item)?"snapshot_and_follow":"follow_history":"snapshot_history",
                ownMarkets.contains(item)));
        hints.sort(Comparator.comparing(Hint::ownMarket)
                .thenComparing((Hint h)->!(ceilings.getOrDefault(h.itemId(),0L)>h.historicalBid()))
                .thenComparing((Hint h)->!followed.contains(h.itemId()))
                .thenComparing(Comparator.comparingLong((Hint h)->Math.max(0,ceilings.getOrDefault(h.itemId(),0L)-h.historicalBid())).reversed())
                .thenComparing(Hint::itemId));
        return List.copyOf(hints.subList(0,Math.min(Math.max(0,Math.min(limit,3)),hints.size())));
    }
    /** Read-only new-bid preview. Only the live quote can determine proposedUnit.
     * No prediction of fills/profit, capacity, competitor ownership or executable permission. */
    public static Check check(Hint hint,boolean succeeded,long liveBid,long quoteAt,long ownAt,
                              Set<String> ownMarkets,long ceiling,long spendCap,long now,boolean guardsClear){
        return check(hint,succeeded,liveBid,quoteAt,ownAt,ownMarkets,1,ceiling,spendCap,now,guardsClear);
    }
    public static Check check(Hint hint,boolean succeeded,long liveBid,long quoteAt,long ownAt,
                              Set<String> ownMarkets,int quantity,long ceiling,long spendCap,long now,boolean guardsClear){
        boolean ownFresh=ownAt>0&&ownAt<=now&&now-ownAt<=OWN_TTL;
        String reason=!guardsClear?"guard_hold":!ownFresh?"own_orders_need_refresh"
                :!succeeded?"live_search_failed":quoteAt>now||quoteAt<=0||now-quoteAt>LIVE_TTL?"live_quote_expired"
                :liveBid<=0?"no_live_order":ownMarkets.contains(hint.itemId())?"own_market_requires_existing_order_review"
                :quantity<=0||liveBid==Long.MAX_VALUE||liveBid+1>ceiling||liveBid+1>spendCap/Math.max(1,quantity)?"outside_current_caps":"live_checked_candidate";
        return new Check(reason,hint.historicalBid(),Math.max(0,liveBid),
                reason.equals("live_checked_candidate")?liveBid+1:0,ownFresh,
                succeeded&&hint.historicalBid()>0&&hint.historicalBid()!=liveBid,true);
    }
    private CommunityMarketGuidance(){}
}
