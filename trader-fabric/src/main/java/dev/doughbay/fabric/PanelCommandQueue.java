package dev.doughbay.fabric;

import java.util.*;

/** Process-local, bounded, at-most-once manual work. It never resumes a stopped trader. */
final class PanelCommandQueue {
    static final int CAPACITY=16;
    static final long LIFETIME=120_000;
    record View(String id,String command,String status,String note,long at) {}
    interface Port {
        default Object connection(){return null;}
        String blocker();
        void queue(String id,String command,long expires);
        String status(String id);
        boolean cancel(String id);
    }
    private static final class Job {
        final String id,command;final long at;final Object connection;String status="QUEUED",note="Waiting for a safe checkpoint";
        Job(String id,String command,long at,Object connection){this.id=id;this.command=command;this.at=at;this.connection=connection;}
        View view(){return new View(id,"/"+command.split(" ",2)[0],status,note,at);}
    }
    private final LinkedHashMap<String,Job> jobs=new LinkedHashMap<>();
    synchronized View add(String id,String command,long now) {
        return add(id,command,now,null);
    }
    synchronized View add(String id,String command,long now,Object connection) {
        UUID.fromString(id);String normalized=normalize(command);
        Job old=jobs.get(id);
        if(old!=null){if(!old.command.equals(normalized))throw new IllegalArgumentException("Request changed; review it again");return old.view();}
        if(jobs.values().stream().filter(j->pending(j.status)).count()>=CAPACITY)throw new IllegalArgumentException("Command queue is full");
        while(jobs.size()>=64){String remove=jobs.values().stream().filter(j->!pending(j.status)).map(j->j.id).findFirst().orElseThrow(()->new IllegalArgumentException("Command queue is full"));jobs.remove(remove);}
        Job job=new Job(id,normalized,now,connection);jobs.put(id,job);return job.view();
    }
    synchronized List<View> views(){return jobs.values().stream().map(Job::view).toList();}
    synchronized void tick(Port port,long now) {
        for(Job job:jobs.values()) {
            if(!pending(job.status))continue;
            if(job.status.equals("WAITING")) {
                String outcome=port.status(job.id);
                if(!outcome.equals("QUEUED")){job.status=outcome;job.note=outcome.equals("SENT")?"Sent once to the game connection; server outcome is not confirmed":"Not retried. Check the client before sending another command";continue;}
            }
            if(job.connection!=null&&job.connection!=port.connection()){
                if(job.status.equals("WAITING")&&!port.cancel(job.id))continue;
                job.status="CANCELLED";job.note="Game connection changed before sending; not replayed";continue;
            }
            if(now-job.at>=LIFETIME){if(job.status.equals("WAITING")&&!port.cancel(job.id))continue;job.status="EXPIRED";job.note="Expired without sending";continue;}
            if(job.status.equals("WAITING"))return;
            String blocker=port.blocker();if(blocker!=null){job.note=blocker;return;}
            // Mark before handing off: a transport exception is not permission to replay a payment.
            job.status="WAITING";job.note="Waiting for normal command pacing";
            try{port.queue(job.id,job.command,job.at+LIFETIME);}catch(RuntimeException e){job.status="UNCERTAIN";job.note="Handoff uncertain; no automatic retry";}
            return;
        }
    }
    synchronized boolean cancel(String id,Port port){Job j=jobs.get(id);if(j==null||!pending(j.status))return false;if(j.status.equals("WAITING")&&!port.cancel(id))return false;j.status="CANCELLED";j.note="Cancelled before sending";return true;}
    private static boolean pending(String state){return state.equals("QUEUED")||state.equals("WAITING");}
    static String normalize(String text) {
        if(text==null||text.length()>256||text.codePoints().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Use one command, at most 256 characters");
        String command=text.replaceAll("^ +| +$", "");if(command.startsWith("/"))command=command.substring(1);
        OutgoingCommandText.requireValid(command);
        String head=command.split(" ",2)[0].toLowerCase(Locale.ROOT);
        if(!Set.of("rtp","home","spawn","pay","bal","balance","msg","reply","r","help").contains(head))
            throw new IllegalArgumentException("Supported manual commands: /rtp, /home, /spawn, /pay, /bal, /balance, /msg, /reply and /help. Trading commands keep their normal controls.");
        if(head.equals("pay")&&!command.matches("(?i)pay [A-Za-z0-9_]{1,16} [0-9]+(?:\\.[0-9]+)?[kmbKMB]?"))throw new IllegalArgumentException("Review /pay player amount before queuing");
        return head+command.substring(command.indexOf(' ')<0?command.length():command.indexOf(' '));
    }
}
