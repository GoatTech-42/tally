package dev.doughbay.fabric.hosted;

import dev.doughbay.fabric.web.LocalPanel;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class DashboardPanelTest {
    final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    final AtomicInteger presses=new AtomicInteger();final List<Runnable> game=new CopyOnWriteArrayList<>();
    final AtomicBoolean current=new AtomicBoolean(true);
    final LocalPanel.Backend backend=new LocalPanel.Backend(){
        public byte[] png(){return new byte[]{1,2,3};}
        public String state(){return "{\"privateFiltered\":true}";}
        public void press(String id,String value){presses.incrementAndGet();}
        public void text(String id,String value){presses.incrementAndGet();}
        public String data(String name,Map<String,String> query){return name.equals("missing")?null:"{\"key\":\""+query.getOrDefault("key","")+"\"}";}
        public String change(String name,Map<String,String> form){presses.incrementAndGet();return "{\"ok\":true}";}
        public byte[] icon(String id){return null;}
    };
    final DashboardPanel panel=new DashboardPanel(()->backend,game::add,timer);
    @AfterEach void close(){current.set(false);panel.close();timer.shutdownNow();}
    ObjectNode frame(String id,String op){return DashboardProtocol.JSON.createObjectNode().put("type","panel").put("id","pr_"+id).put("op",op).put("expiresAt",System.currentTimeMillis()+10_000);}
    CompletableFuture<ObjectNode> request(ObjectNode frame){var reply=new CompletableFuture<ObjectNode>();panel.receive(1,frame,current::get,reply::complete);return reply;}
    @Test void pageAndStateUseTheActualPanelBackend()throws Exception{
        var page=request(frame("page","page")).get(2,TimeUnit.SECONDS);assertEquals(LocalPanel.pageHtml(),page.path("body").asText());assertEquals("panel_result",page.path("type").asText());
        var state=request(frame("state","state")).get(2,TimeUnit.SECONDS);assertEquals(backend.state(),state.path("body").asText());
    }
    @Test void readsForwardMapsAndNullBecomes404()throws Exception{
        var data=frame("data","data").put("name","settings");data.putObject("query").put("key","slots.max");
        assertTrue(request(data).get(2,TimeUnit.SECONDS).path("body").asText().contains("slots.max"));
        assertEquals(404,request(frame("missing","data").put("name","missing")).get(2,TimeUnit.SECONDS).path("status").asInt());
        assertEquals(404,request(frame("icon","icon").put("item","minecraft:diamond")).get(2,TimeUnit.SECONDS).path("status").asInt());
    }
    @Test void mutationsWaitForGameThreadAndDuplicateIdsCannotExecuteTwice()throws Exception{
        var action=frame("act","act");action.putObject("act").put("id","ctl:start").put("value","");
        var reply=request(action);var duplicate=request(action);assertEquals(0,presses.get());assertEquals(1,game.size());game.removeFirst().run();
        assertEquals(204,reply.get(2,TimeUnit.SECONDS).path("status").asInt());assertEquals(1,presses.get());
        var replay=request(action);assertEquals(204,replay.get(2,TimeUnit.SECONDS).path("status").asInt());assertEquals(1,presses.get());
        // A duplicate while still pending shares the original execution; no second action is queued.
        assertFalse(duplicate.isDone());
    }
    @Test void changesAndTextUseTheSameBackend()throws Exception{
        var change=frame("change","change").put("name","setting");change.putObject("form").put("key","slots.max").put("value","45");
        var reply=request(change);game.removeFirst().run();assertEquals(200,reply.get().path("status").asInt());
        var text=frame("text","act");text.putObject("act").put("id","tune:set").put("text","45");
        reply=request(text);game.removeFirst().run();assertEquals(204,reply.get().path("status").asInt());assertEquals(2,presses.get());
    }
    @Test void timedOutGameQueueDoesNotApplyLateMutation()throws Exception{
        var action=frame("timeout","act").put("expiresAt",System.currentTimeMillis()+100);action.putObject("act").put("id","ctl:start");
        var reply=request(action);assertEquals(504,reply.get(2,TimeUnit.SECONDS).path("status").asInt());game.removeFirst().run();assertEquals(0,presses.get());
    }
    @Test void disconnectCancelsQueuedActionAndDoesNotReplyOnNewSocket(){
        var action=frame("disconnect","act");action.putObject("act").put("id","ctl:start");var reply=request(action);
        current.set(false);panel.disconnected(1);game.removeFirst().run();assertEquals(0,presses.get());assertFalse(reply.isDone());
    }
    @Test void ninthOutstandingRequestIsRejected()throws Exception{
        for(int i=0;i<8;i++){var action=frame("busy"+i,"act");action.putObject("act").put("id","ctl:start");request(action);}
        assertEquals(429,request(frame("ninth","state")).get().path("status").asInt());assertEquals(8,game.size());
    }
    @Test void expiryAndIdsAreCheckedBeforeExecuting(){
        var expired=frame("expired","act").put("expiresAt",System.currentTimeMillis()-1);expired.putObject("act").put("id","ctl:start");assertFalse(request(expired).isDone());assertTrue(game.isEmpty());
        assertFalse(request(frame("bad","state").put("id","../secret")).isDone());
    }
    @Test void rejectsTraversalMalformedFormsAndOversizedBodies()throws Exception{
        assertEquals(400,request(frame("asset","asset").put("asset","../../token")).get().path("status").asInt());
        var data=frame("badform","change").put("name","setting");data.putObject("form").put("key","x").put("value","x".repeat(DashboardPanel.MAX_FORM));
        assertEquals(400,request(data).get().path("status").asInt());assertTrue(game.isEmpty());
        assertEquals(413,DashboardPanel.text("pr_large","application/json","x".repeat(DashboardPanel.MAX_TEXT+1)).path("status").asInt());
        assertEquals(413,DashboardPanel.binary("pr_big","font/ttf",new byte[DashboardPanel.MAX_BINARY+1],false).path("status").asInt());
        var binary=request(frame("png","png")).get(2,TimeUnit.SECONDS);assertEquals("base64",binary.path("encoding").asText());assertArrayEquals(new byte[]{1,2,3},Base64.getDecoder().decode(binary.path("body").asText()));
    }
    @Test void reusedIdWithDifferentContentIsRejected()throws Exception{
        var first=frame("reuse","act");first.putObject("act").put("id","ctl:start");request(first);
        var second=first.deepCopy();second.withObject("act").put("id","ctl:stop");assertEquals(409,request(second).get().path("status").asInt());assertEquals(1,game.size());
    }
    @Test void bundlesOnlyKnownAssetsAndAdvertisesPanel()throws Exception{
        assertTrue(DashboardProtocol.hello("build","Main","hive").path("capabilities").toString().contains("panel"));
        for(String asset:List.of("logo","font")){
            var result=request(frame(asset,"asset").put("asset",asset)).get(2,TimeUnit.SECONDS);
            assertEquals(200,result.path("status").asInt());assertEquals("base64",result.path("encoding").asText());
            assertTrue(Base64.getDecoder().decode(result.path("body").asText()).length<=DashboardPanel.MAX_BINARY);
        }
    }
    @Test void oversizedPngIsResizedWithinBinaryLimit()throws Exception{
        var image=new java.awt.image.BufferedImage(600,600,java.awt.image.BufferedImage.TYPE_INT_ARGB);var random=new Random(3);
        for(int y=0;y<600;y++)for(int x=0;x<600;x++)image.setRGB(x,y,random.nextInt()|0xff000000);
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);assertTrue(out.size()>DashboardPanel.MAX_BINARY);
        var response=DashboardPanel.binary("pr_resize","image/png",out.toByteArray(),true);assertEquals(200,response.path("status").asInt());
        byte[] png=Base64.getDecoder().decode(response.path("body").asText());assertTrue(png.length<=DashboardPanel.MAX_BINARY);
        assertTrue(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png)).getWidth()<600);
    }
}
