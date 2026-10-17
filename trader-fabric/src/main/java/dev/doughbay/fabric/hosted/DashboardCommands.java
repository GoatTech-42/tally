package dev.doughbay.fabric.hosted;

import java.util.*;

/** One receipt per command id. Game-thread dispatch checks the socket epoch and expiry again. */
public final class DashboardCommands {
    public record Command(String id,String action,long createdAt,long expiresAt){}
    public record Result(String status,String reason){}
    public interface Handler{Result run(String action);}
    public record Receipt(Command command,long epoch,Result result){}
    private final Map<String,Receipt> receipts=new LinkedHashMap<>();
    private long epoch;
    public synchronized void connected(long next){epoch=next;}
    public synchronized void disconnected(long old){
        if(epoch!=old)return;epoch=0;
        receipts.replaceAll((id,r)->r.result()==null?new Receipt(r.command(),r.epoch(),new Result("rejected","connection changed")):r);
    }
    public synchronized Receipt receive(long connection,Command command,long now){
        Receipt prior=receipts.get(command.id());if(prior!=null)return prior;
        Result rejection=null;
        if(connection==0||connection!=epoch)rejection=new Result("rejected","connection changed");
        else if(command.expiresAt()<=now||command.createdAt()<=0||command.expiresAt()<=command.createdAt()
            ||command.expiresAt()-command.createdAt()>30_000||command.createdAt()>now+5000)rejection=new Result("rejected","expired");
        else if(!DashboardProtocol.CAPABILITIES.contains(command.action()))rejection=new Result("rejected","unsupported");
        receipts.entrySet().removeIf(e->e.getValue().result()!=null&&e.getValue().command().expiresAt()<=now);
        if(receipts.size()>=256)return new Receipt(command,connection,new Result("rejected","busy"));
        var receipt=new Receipt(command,connection,rejection);receipts.put(command.id(),receipt);return receipt;
    }
    public synchronized Result dispatch(long connection,String id,long now,Handler handler){
        Receipt r=receipts.get(id);if(r==null)return new Result("rejected","unsupported");
        if(r.result()!=null)return r.result();
        Result result;
        if(epoch!=connection||r.epoch()!=connection)result=new Result("rejected","connection changed");
        else if(now>=r.command().expiresAt())result=new Result("rejected","expired");
        else{try{result=handler.run(r.command().action());}catch(RuntimeException e){result=new Result("rejected","controller error; check local panel");}}
        receipts.put(id,new Receipt(r.command(),r.epoch(),result));return result;
    }
}
