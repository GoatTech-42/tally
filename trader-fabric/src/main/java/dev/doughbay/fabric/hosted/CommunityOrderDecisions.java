package dev.doughbay.fabric.hosted;

import dev.doughbay.fabric.OrderBook.Order;
import java.util.*;

/** Pure public-book dependencies shared by production bidding and shadow validation. */
public final class CommunityOrderDecisions {
    public static Map<String,Long> bestBids(List<Order> rows,Map<String,long[]> quotes,long now,long ttl) {
        Map<String,Long> best=new HashMap<>();
        for(var row:rows)if(row.parts().isEmpty()&&row.remaining()>0)
            best.merge(row.itemId(),row.unitPrice(),Math::max);
        quotes.forEach((item,quote)->{if(quote[0]>0&&now-quote[1]<=ttl)best.merge(item,quote[0],Math::max);});
        return best;
    }

    /** Equal envelopes guarantee the same delivery price for every quantity/target pair.
     * Keep all price/remaining pairs: a top-bid-only comparison misses partially filled orders. */
    public static Map<String,SortedMap<Long,Long>> deliveryEnvelope(List<Order> rows) {
        Map<String,SortedMap<Long,Long>> result=new TreeMap<>();
        for(var row:rows)if(row.parts().isEmpty()&&row.remaining()>0)
            result.computeIfAbsent(row.itemId(),ignored->new TreeMap<>())
                    .merge(row.unitPrice(),row.remaining(),Math::max);
        return result;
    }

    public static List<Order> snapshotRows(CommunityOrderShadow.Snapshot snapshot) {
        List<Order> result=new ArrayList<>();
        for(var row:snapshot.rows()) {
            String key=row.path("itemKey").asText();String item=key.substring(0,key.indexOf('|'));
            String parts=row.path("parts").asText();
            result.add(new Order(row.path("observedAt").asLong(),item,item,"",
                    parts.isEmpty()?List.of():List.of(parts.split(";")),row.path("unitPrice").asLong(),
                    row.path("delivered").asLong(),row.path("total").asLong(),0));
        }
        return List.copyOf(result);
    }
    /** Preserve every executable/ranking input and list order, omit only explanation text. */
    public record PurchaseInput(dev.doughbay.core.model.Listing listing,
            dev.doughbay.core.model.MarketStats stats, long buyPrice, long sellPrice,
            double profit, double roi, double holdHours, double saleProbability, double confidence, double score) { }
    public static List<PurchaseInput> purchaseInputs(List<dev.doughbay.core.model.Opportunity> candidates) {
        return candidates.stream().map(o -> new PurchaseInput(o.listing(), o.stats(), o.buyPrice(),
                o.recommendedSellPrice(), o.expectedNetProfit(), o.expectedRoiPercent(),
                o.estimatedHoldHours(), o.saleProbability(), o.confidence(), o.score())).toList();
    }
    /** Diagnostic only: permutation equality never by itself certifies ordered selection. */
    public static Map<PurchaseInput,Integer> purchaseSet(List<PurchaseInput> inputs) {
        var counts = new HashMap<PurchaseInput,Integer>();
        for (var input : inputs) counts.merge(input, 1, Integer::sum);
        return Map.copyOf(counts);
    }
    public static List<Order> distinctRead(List<Order> rows) {
        var distinct = new LinkedHashMap<String,Order>();
        for (var row : rows) distinct.put(row.itemKey() + "|" + String.join(";",row.parts())
                + "|" + row.unitPrice() + "|" + row.total(), row);
        return List.copyOf(distinct.values());
    }
    private CommunityOrderDecisions(){}
}
