package dev.doughbay.fabric.hosted;

import dev.doughbay.core.analysis.CommodityRegistry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Optional bootstrap discovery must never stop the local feed while item components load. */
public final class CommunityBootstrapMarkets {
    public static Set<String> ready(Set<String> items,Predicate<String> allowed,ToIntFunction<String> stackSize){
        var keys=new TreeSet<String>();
        for(String item:items){
            if(!CommodityRegistry.DEFAULT_COMMODITIES.contains(item)||!allowed.test(item))continue;
            try{
                int size=stackSize.applyAsInt(item);
                if(size>=1&&size<=64)keys.add(item+"|"+size);
            }catch(RuntimeException unavailable){
                // Not bound yet: skip this market now and retry on the next normal feed pass.
                // Never substitute a guessed stack size or restart local analysis.
            }
        }
        return Set.copyOf(keys);
    }
    private CommunityBootstrapMarkets(){}
}
