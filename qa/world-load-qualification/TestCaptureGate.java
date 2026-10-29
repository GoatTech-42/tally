/** Focused local exercise of the v6.3.3 capture sequencing (no Minecraft runtime).
 *  Covers: once-per-capture semantics, ordered completion, fail-closed mismatch,
 *  fail-closed overwrite (the v6.3.2 spin pattern), hook-error surfacing, and a
 *  simulated case-16 pass loop asserting each named capture completes exactly once. */
public final class TestCaptureGate{
 static int passed,failed;
 static void check(boolean ok,String name){if(ok){passed++;System.out.println("PASS "+name);}else{failed++;System.out.println("FAIL "+name);}}
 public static void main(String[] a){
  // t1: request once, hook view, complete, consume once
  var g=new dev.doughbay.fabric.CaptureGate();
  check(!g.request("s2.png"),"t1 first request pending");
  check("s2.png".equals(g.pendingForHook()),"t1 hook sees pending name");
  g.complete();
  check(g.pendingForHook()==null,"t1 hook idle after complete");
  check(g.request("s2.png"),"t1 own requester consumes");
  check(g.pendingForHook()==null,"t1 gate empty after consume");
  // t2: a second distinct capture proceeds normally after the first completed
  check(!g.request("s1b.png"),"t2 next capture requests");
  g.complete();
  check(g.request("s1b.png"),"t2 own requester consumes");
  // t3: mismatched consumer fails closed
  var g3=new dev.doughbay.fabric.CaptureGate();
  g3.request("s1b.png");g3.complete();
  boolean threw=false;try{g3.request("s2.png");}catch(IllegalStateException e){threw=true;}
  check(threw,"t3 wrong requester consuming completed frame fails closed");
  // t4: overwrite attempt fails closed and the pending capture survives (v6.3.2 pattern)
  var g4=new dev.doughbay.fabric.CaptureGate();
  g4.request("s1b.png");
  threw=false;try{g4.request("s2.png");}catch(IllegalStateException e){threw=true;}
  check(threw,"t4 different-name request while pending fails closed");
  check("s1b.png".equals(g4.pendingForHook()),"t4 pending capture untouched");
  g4.complete();
  check(g4.request("s1b.png"),"t4 original capture still consumable");
  // t5: hook failure surfaces to the requester
  var g5=new dev.doughbay.fabric.CaptureGate();
  g5.request("x.png");g5.fail("boom");g5.complete();
  threw=false;try{g5.request("x.png");}catch(IllegalStateException e){threw=true;}
  check(threw,"t5 hook error fails closed");
  // t6: simulated case-16 pass loop with done-guards: each capture completes exactly once, in order
  var g6=new dev.doughbay.fabric.CaptureGate();
  boolean s2d=false,s1bd=false;StringBuilder tr=new StringBuilder();
  for(int tick=0;tick<40&&!(s2d&&s1bd);tick++){
   if(!s2d){ if(!g6.request("s2.png")){g6.complete();continue;} s2d=true;tr.append("s2;"); }
   if(!g6.request("s1b.png")){g6.complete();continue;}
   s1bd=true;tr.append("s1b;");
  }
  check(s2d&&s1bd,"t6 both captures complete");
  check("s2;s1b;".contentEquals(tr),"t6 each captured exactly once in order");
  // t7: the unguarded v6.3.2 re-request pattern now fails closed instead of spinning
  var g7=new dev.doughbay.fabric.CaptureGate();
  boolean spun=false,closed=false;
  try{
   for(int tick=0;tick<40;tick++){
    if(!g7.request("s2.png")){g7.complete();continue;}
    if(!g7.request("s1b.png")){g7.complete();continue;}
    spun=true;break;
   }
  }catch(IllegalStateException e){closed=true;}
  check(closed&&!spun,"t7 unguarded re-request fails closed (no spin)");
  System.out.println(failed==0?("ALL "+passed+" CHECKS PASSED"):(failed+" CHECKS FAILED"));
  if(failed>0)System.exit(1);
 }
}
