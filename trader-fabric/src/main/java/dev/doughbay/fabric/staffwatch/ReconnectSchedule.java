package dev.doughbay.fabric.staffwatch;
import java.util.function.LongSupplier;import java.util.random.RandomGenerator;
/** Pure timer: duplicate alerts never extend a wait; elapsed time is monotonic. */
public final class ReconnectSchedule {
 private final LongSupplier clock;private final RandomGenerator random;
 private String address,watcher;private long deadline;
 public ReconnectSchedule(LongSupplier clock,RandomGenerator random){this.clock=clock;this.random=random;}
 public boolean begin(String address,String watcher){if(active())return false;if(address==null||address.isBlank())throw new IllegalArgumentException("Missing server address");this.address=address;this.watcher=watcher;deadline=clock.getAsLong()+random.nextLong(300_000L,600_001L)*1_000_000L;return true;}
 public boolean beginRetry(String address){if(active())return false;if(address==null||address.isBlank())throw new IllegalArgumentException("Missing server address");this.address=address;this.watcher=null;deadline=clock.getAsLong()+120_000L*1_000_000L;return true;}
 public boolean active(){return address!=null;}public String address(){return address;}public String watcher(){return watcher;}
 public long remainingMillis(){return active()?Math.max(0,(deadline-clock.getAsLong()+999_999)/1_000_000):0;}
 public boolean due(){return active()&&remainingMillis()==0;}
 public void cancel(){address=null;watcher=null;deadline=0;}
}
