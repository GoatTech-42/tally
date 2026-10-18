package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import dev.doughbay.core.analysis.CommodityRegistry;
import dev.doughbay.core.model.Sale;
import dev.doughbay.core.model.ItemFingerprint;
import dev.doughbay.storage.Database;
import dev.doughbay.storage.TransactionRepository;
import java.sql.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Completed-sale history only. Never writes positions, profit, ownership or the live order book. */
public final class CommunityHistoryBootstrap {
    public static final String SOURCE="swarm:completed-sale";
    public static final long WINDOW=86_400_000, FRESH_MARKET=900_000;
    public record Page(List<Sale> sales,String next,long asOf,long windowStart){}
    private static long integer(JsonNode row,String key){
        var value=row.path(key);if(!value.isIntegralNumber()||!value.canConvertToLong())throw new IllegalArgumentException("Invalid history number");
        return value.asLong();
    }
    public static Page decode(JsonNode body,Set<String> requested,long now,long expectedAsOf){
        long generated=integer(body,"generatedAt"),asOf=integer(body,"asOf"),since=integer(body,"windowStart");
        if(body.path("schema").asInt()!=1||!body.path("kind").asText().equals("completed_sales")
                ||!body.path("regionScope").asText().equals("net-donut")||generated<=0||generated>now+5000||now-generated>120_000
                ||asOf<=0||asOf>generated||now-asOf>600_000||since<asOf-WINDOW||since>asOf||expectedAsOf>0&&asOf!=expectedAsOf
                ||body.path("capped").asBoolean()||!body.path("rows").isArray()||body.path("rows").size()>1000)throw new IllegalArgumentException("Invalid history envelope");
        var sales=new ArrayList<Sale>();var ids=new HashSet<String>();
        for(var row:body.path("rows")){
            String id=row.path("id").asText(),key=row.path("itemKey").asText();
            if(!id.matches("[A-Za-z0-9_-]{6,160}")||!ids.add(id))throw new IllegalArgumentException("Invalid history id");
            if(!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|[1-9][0-9]?"))continue;
            String item=key.substring(0,key.indexOf('|'));
            if(!requested.contains(key)||!CommodityRegistry.DEFAULT_COMMODITIES.contains(item))continue;
            if(row.hasNonNull("partsHash")||!row.path("parts").asText().isEmpty())continue;
            long count=integer(row,"quantity"),stack=integer(row,"stack"),total=integer(row,"total"),at=integer(row,"observedAt");
            var provenance=row.path("provenance");
            if(!provenance.path("kind").asText().equals("seller_receipt")||!provenance.path("ownAccount").isBoolean()
                    ||integer(provenance,"independentSources")<1)throw new IllegalArgumentException("Unproven sale history");
            if(provenance.path("ownAccount").asBoolean())continue;
            if(count<1||stack!=Math.min(count,64)||stack!=Integer.parseInt(key.substring(key.indexOf('|')+1))
                    ||total<count||total>1_000_000_000_000L||at<=0||at<since||at>asOf||at>now
                    ||now-at>WINDOW||integer(row,"unitPrice")!=total/count)throw new IllegalArgumentException("Invalid completed sale");
            // A multi-stack receipt is one sale, not several independent stack samples.
            if(count>64)continue;
            sales.add(new Sale(ItemFingerprint.sha256("swarm-history-v1|"+id),at,SOURCE,"Shared completed-sale history",item,item,(int)count,total));
        }
        var next=body.get("next");
        if(next==null||!next.isNull()&&(!next.isTextual()||next.asText().isEmpty()||next.asText().length()>1024))throw new IllegalArgumentException("Invalid history cursor");
        return new Page(List.copyOf(sales),next.isNull()?"":next.asText(),asOf,since);
    }
    /** A thin ledger gets one seed; established or previously seeded ledgers keep collecting normally. */
    public static boolean needsSeed(Database db)throws SQLException{
        try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM (SELECT 1 FROM transactions LIMIT 5000)")){
            if(r.next()&&r.getInt(1)>=5000)return false;
        }
        try(var q=db.connection().prepareStatement("SELECT 1 FROM transactions WHERE seller_uuid=? LIMIT 1")){
            q.setString(1,SOURCE);try(var r=q.executeQuery()){return !r.next();}
        }
    }
    public static int seed(Database db,List<Sale> input,long now,int minimumSamples,BooleanSupplier stillAllowed)throws SQLException{
        if(!stillAllowed.getAsBoolean()||!needsSeed(db))return 0;
        var fresh=new HashSet<String>();
        var counts=new HashMap<String,Integer>();
        var unique=new HashSet<String>();
        for(var sale:input)if(validHistoricalSale(sale,now)&&unique.add(sale.transactionHash()))counts.merge(sale.itemKey()+"|"+sale.itemCount(),1,Integer::sum);
        for(var sale:input)if(validHistoricalSale(sale,now)&&now-sale.soldAt()<=FRESH_MARKET)fresh.add(sale.itemKey()+"|"+sale.itemCount());
        Connection c=db.connection();if(!c.getAutoCommit())throw new SQLException("History seed requires its own transaction");
        c.setAutoCommit(false);int written=0;
        try{
            var repo=new TransactionRepository(db);var seen=new HashSet<String>();
            for(var sale:input){
                if(!validHistoricalSale(sale,now)||!seen.add(sale.transactionHash()))continue;
                String market=sale.itemKey()+"|"+sale.itemCount();
                if(sale.soldAt()>now||now-sale.soldAt()>WINDOW||!fresh.contains(market)||counts.getOrDefault(market,0)<Math.max(3,minimumSamples))continue;
                if(repo.insertIfAbsent(sale,"{\"source\":\"swarm-completed-sale\",\"historicalOnly\":true}"))written++;
            }
            if(!stillAllowed.getAsBoolean()){c.rollback();return 0;}
            c.commit();return written;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        finally{c.setAutoCommit(true);}
    }
    private static boolean validHistoricalSale(Sale sale,long now){
        return sale!=null&&sale.isValid()&&sale.sellerUuid().equals(SOURCE)
                &&CommodityRegistry.DEFAULT_COMMODITIES.contains(sale.itemKey())&&sale.itemKey().equals(sale.itemId())
                &&sale.itemCount()<=64&&sale.totalPrice()>=sale.itemCount()&&sale.totalPrice()<=1_000_000_000_000L
                &&sale.soldAt()<=now&&now-sale.soldAt()<=WINDOW;
    }
    private CommunityHistoryBootstrap(){}
}
