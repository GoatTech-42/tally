package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PanelBotRegistryTest {
    @TempDir Path temp;
    private PanelBotRegistry.Bot bot(String id,long at){return new PanelBotRegistry.Bot(id,at,ProcessHandle.current().pid(),"ExamplePlayer",temp.toString(),temp.toString(),false,"",id,List.of());}
    @Test void detectsFreshClientsAndConsumesOnlyTheirExpiringRequests() throws Exception {
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();var one=new PanelBotRegistry(temp,a);var two=new PanelBotRegistry(temp,b);
        one.advertise(bot(a,1000));two.advertise(bot(b,1000));assertEquals(2,one.bots(2000).size());
        one.post(b,UUID.randomUUID().toString(),"command",Map.of("command","rtp"),2000);
        assertTrue(one.take(2001).isEmpty());assertEquals(1,two.take(2001).size());assertTrue(two.take(2002).isEmpty());
        assertTrue(one.bots(17000).isEmpty());
    }
    @Test void staleSenderAndPriorRunCannotReplayACommand() throws Exception {
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();var one=new PanelBotRegistry(temp,a);var two=new PanelBotRegistry(temp,b);
        one.advertise(bot(a,1000));two.advertise(bot(b,1000));one.post(b,UUID.randomUUID().toString(),"command",Map.of("command","rtp"),2000);
        assertTrue(two.take(40_000).isEmpty());
        assertTrue(new PanelBotRegistry(temp,UUID.randomUUID().toString()).take(2001).isEmpty());
    }
}
