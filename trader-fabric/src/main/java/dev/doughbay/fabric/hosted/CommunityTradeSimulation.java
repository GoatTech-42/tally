package dev.doughbay.fabric.hosted;
import java.util.List;
/** A paper decision from current observed pages. Never a reservation or execution permit. */
public final class CommunityTradeSimulation {
    public record Level(long unitPrice,long remainingLowerBound){}
    public record Result(String reason,long purchaseCost,long deliveryGross,long netProfit,double roi,
                         long deliveryUnit,boolean quantityObserved,boolean confirmationRequired){}
    /** Anonymous price evidence for paper economics only; never proof of a non-owned seller. */
    public static boolean usableAuctionLot(int observed,int requested,boolean plain,boolean possibleOwnListing){
        return requested>0&&observed==requested&&plain&&!possibleOwnListing;
    }
    public static long remainingLowerBound(long totalFloor,long deliveredFloor,long deliveredBand){
        if(totalFloor<=0||deliveredFloor<0||deliveredBand<=0||deliveredFloor>totalFloor)return 0;
        long uncertainty=Math.min(totalFloor-deliveredFloor,deliveredBand-1);
        return totalFloor-deliveredFloor-uncertainty;
    }
    public static Result evaluate(List<Level> levels,int parseFailures,int quantity,long auctionUpper,
                                  long quoteAt,long auctionAt,long ownAt,boolean ownMarket,
                                  long purchaseCap,long outstandingRoom,double minProfit,double minRoi,
                                  long now,boolean guardsClear,boolean unresolvedExposure){
        long unit=levels.stream().filter(r->r.unitPrice()>0&&r.remainingLowerBound()>=quantity)
                .mapToLong(Level::unitPrice).max().orElse(0);
        boolean capacity=quantity>0&&unit>0&&parseFailures==0;
        String early=!guardsClear?"guard_hold":unresolvedExposure?"unresolved_exposure_hold"
                :ownAt<=0||ownAt>now||now-ownAt>60_000?"own_orders_need_refresh"
                :ownMarket?"own_market_requires_review":quoteAt<=0||auctionAt<=0||quoteAt>now||auctionAt>now
                ||now-quoteAt>30_000||now-auctionAt>30_000?"live_evidence_expired"
                :parseFailures>0?"order_quantity_parse_uncertain":!capacity?"insufficient_live_order_quantity"
                :auctionUpper<=0?"no_matching_live_auction"
                :!Double.isFinite(minProfit)||!Double.isFinite(minRoi)||minProfit<0||minRoi<0?"invalid_profit_limits":"";
        long gross=0;
        try{if(capacity&&auctionUpper>0)gross=Math.multiplyExact(unit,(long)quantity);}
        catch(ArithmeticException e){return new Result("amount_overflow",auctionUpper,0,0,0,unit,capacity,true);}
        long profit=gross>0?gross-auctionUpper:0;
        double roi=gross>0&&auctionUpper>0?profit*100.0/auctionUpper:0;
        if(!early.isEmpty())return new Result(early,Math.max(0,auctionUpper),gross,profit,roi,unit,capacity,true);
        String reason=auctionUpper>purchaseCap?"purchase_cap_exceeded":auctionUpper>outstandingRoom?"outstanding_cap_exceeded"
                :profit<minProfit?"minimum_profit_not_met":roi<minRoi?"minimum_roi_not_met":"paper_candidate_qualified";
        return new Result(reason,auctionUpper,gross,profit,roi,unit,capacity,true);
    }
    private CommunityTradeSimulation(){}
}
