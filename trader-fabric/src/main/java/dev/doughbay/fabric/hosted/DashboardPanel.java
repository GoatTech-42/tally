package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.web.LocalPanel;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Executes the existing panel backend without opening a listener or bypassing its controller. */
public final class DashboardPanel implements AutoCloseable {
    public static final int MAX_TEXT=256*1024, MAX_BINARY=512*1024, MAX_FORM=64*1024;
    private final Supplier<LocalPanel.Backend> backend;
    private final Executor game;
    private final ScheduledExecutorService timer;
    private final ExecutorService reads=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"gonuts-dashboard-panel");t.setDaemon(true);return t;});
    private final LinkedHashMap<String,Request> requests=new LinkedHashMap<>();
    private int inFlight;
    private static final class Request {
        final long epoch,expires;final String identity;final JsonNode frame;final BooleanSupplier current;final Consumer<ObjectNode> reply;
        ObjectNode result;boolean answered,running;ScheduledFuture<?> timeout;
        Request(long epoch,JsonNode frame,BooleanSupplier current,Consumer<ObjectNode> reply){this.epoch=epoch;this.frame=frame.deepCopy();this.expires=frame.path("expiresAt").asLong();this.identity=frame.toString();this.current=current;this.reply=reply;}
    }
    public DashboardPanel(Supplier<LocalPanel.Backend> backend,Executor game,ScheduledExecutorService timer){this.backend=backend;this.game=game;this.timer=timer;}
    public synchronized void receive(long epoch,JsonNode frame,BooleanSupplier current,Consumer<ObjectNode> reply){
        long now=System.currentTimeMillis();String id=frame.path("id").asText();long expires=frame.path("expiresAt").asLong();
        if(!id.matches("pr_[A-Za-z0-9_-]{1,64}")||expires<=now||!current.getAsBoolean())return;
        if(expires-now>15_000){reply.accept(error(id,400,"Invalid expiry"));return;}
        String key=epoch+":"+id;var old=requests.get(key);
        if(old!=null){if(!old.identity.equals(frame.toString()))reply.accept(error(id,409,"Request id already used"));else if(old.result!=null)reply.accept(old.result.deepCopy());return;}
        requests.entrySet().removeIf(e->e.getValue().answered&&!e.getValue().running&&now-e.getValue().expires>30_000);
        if(inFlight>=8||requests.size()>=256){reply.accept(error(id,429,"Panel busy"));return;}
        try{validate(frame);}catch(IllegalArgumentException e){reply.accept(error(id,400,"Invalid panel request"));return;}
        var request=new Request(epoch,frame,current,reply);requests.put(key,request);inFlight++;
        request.timeout=timer.schedule(()->finish(request,error(id,504,"The client did not answer in time")),expires-now,TimeUnit.MILLISECONDS);
        String op=frame.path("op").asText();Executor executor=op.equals("change")||op.equals("act")?game:reads;
        try{executor.execute(()->execute(request));}catch(RejectedExecutionException e){inFlight--;finish(request,error(id,503,"Panel unavailable"));}
    }
    private void execute(Request request){
        synchronized(this){
            if(request.answered||!request.current.getAsBoolean()||System.currentTimeMillis()>=request.expires){inFlight--;finish(request,null);return;}
            request.running=true;
        }
        ObjectNode result;
        try{result=call(request.frame);}catch(Exception e){result=error(request.frame.path("id").asText(),500,"Panel request failed");}
        synchronized(this){request.running=false;inFlight--;finish(request,result);}
    }
    private synchronized void finish(Request request,ObjectNode result){
        if(request.answered)return;request.answered=true;if(request.timeout!=null)request.timeout.cancel(false);
        if(!request.current.getAsBoolean()||result==null)return;
        if(System.currentTimeMillis()>=request.expires)result=error(request.frame.path("id").asText(),504,"The client did not answer in time");
        request.result=result;request.reply.accept(result.deepCopy());
    }
    public synchronized void disconnected(long epoch){for(var request:requests.values())if(request.epoch==epoch)finish(request,null);}
    private ObjectNode call(JsonNode request)throws Exception {
        String id=request.path("id").asText(),op=request.path("op").asText();var b=backend.get();if(b==null)return error(id,503,"Panel unavailable");
        return switch(op){
            case "page" -> text(id,"text/html; charset=utf-8",LocalPanel.pageHtml());
            case "state" -> text(id,"application/json; charset=utf-8",b.state());
            case "data" -> text(id,"application/json; charset=utf-8",b.data(request.path("name").asText(),map(request.path("query"))));
            case "change" -> text(id,"application/json; charset=utf-8",b.change(request.path("name").asText(),map(request.path("form"))));
            case "act" -> {
                var act=request.path("act");if(act.has("text"))b.text(act.path("id").asText(),act.path("text").asText());else b.press(act.path("id").asText(),act.path("value").asText(""));
                yield result(id,204,"text/plain; charset=utf-8","");
            }
            case "png" -> binary(id,"image/png",b.png(),true);
            case "icon" -> binary(id,"image/png",b.icon(request.path("item").asText()),false);
            case "asset" -> binary(id,request.path("asset").asText().equals("font")?"font/ttf":"image/png",LocalPanel.asset(request.path("asset").asText()),false);
            default -> error(id,400,"Invalid panel operation");
        };
    }
    static void validate(JsonNode frame){
        String op=frame.path("op").asText();
        if(!Set.of("page","state","data","change","act","png","icon","asset").contains(op))throw new IllegalArgumentException();
        if(op.equals("data")||op.equals("change")){
            if(!frame.path("name").asText().matches("[a-z]{1,24}"))throw new IllegalArgumentException();
            map(frame.path(op.equals("data")?"query":"form"));
        }
        if(op.equals("act")){
            var act=frame.path("act");if(!act.isObject()||!act.path("id").asText().matches("[a-z]{1,16}:[A-Za-z0-9_.-]{1,48}"))throw new IllegalArgumentException();
            map(act);
        }
        if(op.equals("icon")&&!frame.path("item").asText().matches("([a-z0-9_.\\-]{1,40}:)?[a-z0-9_.\\-]{1,64}"))throw new IllegalArgumentException();
        if(op.equals("asset")&&!Set.of("logo","font").contains(frame.path("asset").asText()))throw new IllegalArgumentException();
    }
    static Map<String,String> map(JsonNode node){
        if(node.isMissingNode()||node.isNull())return Map.of();if(!node.isObject()||node.size()>256)throw new IllegalArgumentException();
        Map<String,String> values=new LinkedHashMap<>();int size=0;
        var fields=node.fields();while(fields.hasNext()){
            var entry=fields.next();if(!entry.getValue().isTextual())throw new IllegalArgumentException();
            String key=entry.getKey(),value=entry.getValue().asText();
            size+=URLEncoder.encode(key,StandardCharsets.UTF_8).length()+URLEncoder.encode(value,StandardCharsets.UTF_8).length()+2;
            if(size>MAX_FORM)throw new IllegalArgumentException();values.put(key,value);
        }return Map.copyOf(values);
    }
    static ObjectNode text(String id,String type,String body){
        if(body==null)return error(id,404,"Not found");if(body.getBytes(StandardCharsets.UTF_8).length>MAX_TEXT)return error(id,413,"Panel response too large");
        return bounded(result(id,200,type,body));
    }
    static ObjectNode binary(String id,String type,byte[] body,boolean downscale)throws IOException {
        if(body==null||body.length==0)return error(id,404,"Not found");
        if(body.length>MAX_BINARY&&downscale)body=downscale(body);
        if(body.length>MAX_BINARY)return error(id,413,"Panel response too large");
        return bounded(result(id,200,type,Base64.getEncoder().encodeToString(body)).put("encoding","base64"));
    }
    private static byte[] downscale(byte[] body)throws IOException {
        if(body.length>16*1024*1024)return body;
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(body));if(image==null)return body;
        for(int attempt=0;attempt<8&&body.length>MAX_BINARY;attempt++){
            int width=Math.max(1,image.getWidth()*3/4),height=Math.max(1,image.getHeight()*3/4);
            BufferedImage smaller=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var g=smaller.createGraphics();try{g.drawImage(image,0,0,width,height,null);}finally{g.dispose();}
            var out=new ByteArrayOutputStream();ImageIO.write(smaller,"png",out);body=out.toByteArray();image=smaller;
        }return body;
    }
    private static ObjectNode bounded(ObjectNode result){return result.toString().getBytes(StandardCharsets.UTF_8).length>DashboardProtocol.MAX_PANEL_FRAME_BYTES?error(result.path("id").asText(),413,"Panel response too large"):result;}
    private static ObjectNode result(String id,int status,String type,String body){return DashboardProtocol.JSON.createObjectNode().put("type","panel_result").put("id",id).put("status",status).put("contentType",type).put("body",body);}
    static ObjectNode error(String id,int status,String body){return result(id,status,"text/plain; charset=utf-8",body);}
    @Override public void close(){synchronized(this){for(var r:requests.values())finish(r,null);}reads.shutdownNow();}
}
