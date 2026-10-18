package dev.doughbay.fabric.hosted;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommunityBootstrapMarketsTest {
    @Test void unboundComponentsDoNotAbortTheFeedAndAreRetriedWithoutGuessing(){
        var bound=new AtomicBoolean();
        java.util.function.ToIntFunction<String> sizes=item->{
            if(!bound.get())throw new NullPointerException("Components not bound yet");
            return item.equals("minecraft:ender_pearl")?16:64;
        };
        var items=Set.of("minecraft:diamond","minecraft:ender_pearl");
        assertTrue(CommunityBootstrapMarkets.ready(items,k->true,sizes).isEmpty());
        bound.set(true);
        assertEquals(Set.of("minecraft:diamond|64","minecraft:ender_pearl|16"),CommunityBootstrapMarkets.ready(items,k->true,sizes));
    }
    @Test void oneUnavailableMarketCannotHideOtherReadyAllowedPlainMarkets(){
        var keys=CommunityBootstrapMarkets.ready(Set.of("minecraft:diamond","minecraft:emerald","minecraft:coal","minecraft:shulker_box"),
                item->!item.equals("minecraft:coal"),item->{
                    if(item.equals("minecraft:emerald"))throw new IllegalStateException("Not bound");
                    return 64;
                });
        assertEquals(Set.of("minecraft:diamond|64"),keys);
        assertTrue(CommunityBootstrapMarkets.ready(Set.of("minecraft:diamond"),k->true,k->0).isEmpty());
        assertTrue(CommunityBootstrapMarkets.ready(Set.of("minecraft:diamond"),k->true,k->65).isEmpty());
    }
}
