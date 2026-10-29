package dev.doughbay.fabric;
/** Sequences pre-swap frame captures between the tick thread (requester) and the
 *  Window.updateDisplay hook (renderer). One pending named capture at a time; each
 *  completed frame is consumed by its own requester exactly once. Any mismatched
 *  request or consumer fails closed - no silent overwrite of a pending capture
 *  (the v6.3.2 case-16 spin defect). Harness-only; no Minecraft dependencies. */
public final class CaptureGate{
 private String pending;private boolean done;private String error;
 /** Tick side. Returns true exactly once, when the frame captured for THIS name is consumed. */
 public synchronized boolean request(String name){
  if(error!=null)throw new IllegalStateException("pre-swap capture hook failed: "+error);
  if(done){
   if(!name.equals(pending))throw new IllegalStateException("capture consumed by wrong requester: completed '"+pending+"' vs '"+name+"'");
   done=false;pending=null;return true;
  }
  if(pending==null){pending=name;return false;}
  if(!pending.equals(name))throw new IllegalStateException("capture request '"+name+"' would overwrite pending '"+pending+"'");
  return false;
 }
 /** Render side: the name to capture this frame, or null when nothing is pending. */
 public synchronized String pendingForHook(){return done?null:pending;}
 /** Render side: the pending frame was captured and written. */
 public synchronized void complete(){if(pending!=null)done=true;}
 /** Render side: the capture attempt failed; the requester throws fail-closed. */
 public synchronized void fail(String message){if(error==null)error=message;}
}
