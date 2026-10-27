package dev.doughbay.fabric.staffwatch;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;import net.minecraft.client.gui.screens.*;import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
public final class ReconnectController {
 public static final ReconnectController INSTANCE=new ReconnectController();
 public final ReconnectSchedule schedule=new ReconnectSchedule(System::nanoTime,ThreadLocalRandom.current());
 private ServerData target;
 private boolean automaticAttempt;
 private boolean donutSession;
 private boolean cancelledByUser;
 private final ReconnectResume tradingResume=new ReconnectResume();
 private String lastResumeBlocker="";
 private String disconnectReason="";
 public String disconnectReason(){return disconnectReason;}
 /** Reports the existing owner and intent; this callback may precede countdown creation. */
 public String unexpectedDisconnectNextAction(){
  if(regionEscape.flow.ownsConnection())return "Existing region recovery owns the connection; its safety checks still apply.";
  if(dev.doughbay.fabric.DoughBayClient.restartOwnsConnection())return "Existing restart recovery owns the connection; its safety checks still apply.";
  var config=WatchConfig.get();
  if(!config.enabled||!config.autoDisconnect)return "Automatic reconnect is disabled.";
  if(cancelledByUser)return "Automatic reconnect was cancelled by the user.";
  if(!donutSession)return "No eligible server session is retained for automatic reconnect.";
  if(schedule.active())return "Automatic reconnect countdown active: about "+((schedule.remainingMillis()+999)/1000)+" seconds remaining. Trading resumes only after native recovery checks pass.";
  return "Automatic reconnect enabled: normally retries after 2 minutes once the disconnect screen is ready. Trading resumes only after native recovery checks pass.";
 }

 public boolean controlsConnection(){if(regionEscape.flow.ownsConnection())return true;if(dev.doughbay.fabric.DoughBayClient.restartOwnsConnection()&&schedule.watcher()==null)return false;var c=WatchConfig.get();return c.enabled&&c.autoDisconnect&&donutSession&&(cancelledByUser||schedule.active()||flow.phase()!=ReconnectFlow.Phase.IDLE||Minecraft.getInstance().gui.screen() instanceof DisconnectedScreen);}
 private String lastWatcher="";
 public String lastWatcher(){return lastWatcher;}
 private final ReconnectFlow flow=new ReconnectFlow(new ReconnectFlow.Client(){
  private Minecraft mc(){return Minecraft.getInstance();}
  public boolean multiplayer(){return !mc().hasSingleplayerServer()&&mc().getCurrentServer()!=null&&DonutServer.matches(mc().getCurrentServer().ip);}
  public boolean connected(){return mc().level!=null&&mc().getConnection()!=null;}
  public boolean connecting(){return mc().gui.screen() instanceof ConnectScreen || mc().gui.screen() instanceof LevelLoadingScreen || mc().gui.screen() instanceof ProgressScreen;}
  public boolean disconnectedScreen(){return mc().gui.screen() instanceof DisconnectedScreen;}
  public boolean countdownScreen(){return mc().gui.screen() instanceof ReconnectScreen;}
  public String currentAddress(){var server=mc().getCurrentServer();return server==null?null:server.ip;}
  public String failedJoinDestination(String attempted){
   var screen=mc().gui.screen();
   String reason=screen instanceof DisconnectedScreen ? screen.getNarrationMessage().getString() : "";
   return dev.doughbay.fabric.JoinRecovery.next(attempted,dev.doughbay.fabric.Tuning.text("restart.hop_addresses"),reason);
  }
  public boolean automaticRetryAllowed(){
   var screen=mc().gui.screen();
   return !(screen instanceof DisconnectedScreen)||!dev.doughbay.fabric.JoinRecovery.permanentEnforcement(screen.getNarrationMessage().getString());
  }
  public void automaticRetryScheduled(){
   tradingResume.automaticRetryScheduled(System.currentTimeMillis());
  }
  public void disconnect(String watcher){mc().getConnection().getConnection().disconnect(Component.literal("Staff Watch: "+watcher+" triggered a watcher alert. Reconnecting in 5-10 minutes."));}
  public void showCountdown(){mc().gui.setScreen(new ReconnectScreen());}
  public void connect(String address){var server=target;if(server==null||!address.equals(server.ip))server=new ServerData("Staff Watch",address,ServerData.Type.OTHER);automaticAttempt=true;try{ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()),mc(),ServerAddress.parseString(address),server,false,null);}finally{automaticAttempt=false;}}
 },schedule);
 private final StaffRegionEscapeController regionEscape = new StaffRegionEscapeController(this::disconnectNow);
 public String evasionRtpCommand(){return regionEscape.evasionRtpCommand();}
 private ReconnectController(){}
 public void unexpectedConnectionLost(){unexpectedConnectionLost("");}
 public void unexpectedConnectionLost(String reason){
  disconnectReason=reason==null?"":reason.replaceAll("[\\p{Cntrl}]"," ").strip();
  if(disconnectReason.length()>500)disconnectReason=disconnectReason.substring(0,500);
  var config=WatchConfig.get();
  if(!config.enabled||!config.autoDisconnect||cancelledByUser)return;
  var session=dev.doughbay.fabric.DoughBayClient.automationSessionController();
  tradingResume.noteUnexpectedDisconnect(session.snapshot().active()||session.restartRecoveryMayResume(),System.currentTimeMillis());
 }
 public void alert(String watcher){
  var config=WatchConfig.get();
  if(schedule.active()||flow.phase()==ReconnectFlow.Phase.DISCONNECTING||flow.phase()==ReconnectFlow.Phase.WAITING)return;
  var mc=Minecraft.getInstance();
  if(mc.getCurrentServer()==null||!DonutServer.matches(mc.getCurrentServer().ip)||mc.hasSingleplayerServer())return;
  if(!config.enabled||!config.autoDisconnect||watcher==null||watcher.isBlank())return;
  ManualRegionTransfer.cancel();
  if(!config.regionSwitch){
   dev.doughbay.fabric.DoughBayClient.yieldRestartToStaffWatch();
   disconnectNow(watcher);return;
  }
  if(regionEscape.flow.alert(watcher,true)){
   lastWatcher=watcher;
   dev.doughbay.fabric.DoughBayClient.yieldRestartToStaffWatch();
  }
 }
 private void disconnectNow(String watcher){
  var c=WatchConfig.get();var current=Minecraft.getInstance().getCurrentServer();
  if(c.enabled&&watcher!=null&&!watcher.isBlank())lastWatcher=watcher;
  if(flow.staffCooldown(watcher,c.reconnectAddress(),c.enabled&&c.autoDisconnect)){
   lastWatcher=watcher==null?"Staff":watcher;
   var session=dev.doughbay.fabric.DoughBayClient.automationSessionController();
   var before=session.snapshot();
   boolean owned=before.active()||(session.restartRecoveryMayResume()
     && before.detail().startsWith("PAUSED: Staff Watch:"));
   try{StaffWatchClient.detected(lastWatcher);}catch(RuntimeException e){System.err.println("[GoNuts Staff Watch] Alert observer failed: "+e.getMessage());}
   tradingResume.arm(owned&&session.restartRecoveryMayResume(),System.currentTimeMillis());
   lastResumeBlocker="";
   String address=schedule.address();
   target=current!=null&&address.equals(current.ip)?current:new ServerData("Staff Watch",address,ServerData.Type.OTHER);
  }
 }
 public void connectionStarted(ServerData server){
  regionEscape.cancel();
  target=server;cancelledByUser=false;donutSession=DonutServer.matches(server.ip);
  if(!automaticAttempt){tradingResume.cancel();flow.connectionStarted(server.ip,donutSession&&WatchConfig.get().enabled&&WatchConfig.get().autoDisconnect);}
 }
 public void cancel(){tradingResume.cancel();regionEscape.cancel();flow.cancel();target=null;cancelledByUser=true;}
 public boolean canReconnectNow(){return !regionEscape.flow.ownsConnection()&&!dev.doughbay.fabric.DoughBayClient.restartOwnsConnection()&&flow.canReconnectNow();}
 public boolean reconnectNow(){
  if(!canReconnectNow())return false;
  // Only the connection is requested here; retained trading intent and native gates remain unchanged.
  cancelledByUser=false;return flow.reconnectNow();
 }
 public void tick(){
  // Detector callbacks finish before world teardown, avoiding invalidating their iterators.
  if(flow.phase()==ReconnectFlow.Phase.DISCONNECTING){
   var listener=Minecraft.getInstance().getConnection();
   if(listener!=null&&!listener.getConnection().isConnected())listener.getConnection().handleDisconnection();
  }
  var mc=Minecraft.getInstance();
  if(mc.level!=null&&!mc.hasSingleplayerServer()&&mc.getCurrentServer()!=null){target=mc.getCurrentServer();cancelledByUser=false;donutSession=DonutServer.matches(target.ip);}
  regionEscape.tick(donutSession&&WatchConfig.get().enabled&&WatchConfig.get().autoDisconnect&&WatchConfig.get().regionSwitch);
  flow.tick(donutSession&&WatchConfig.get().enabled&&WatchConfig.get().autoDisconnect,WatchConfig.get().reconnectAddress(),dev.doughbay.fabric.DoughBayClient.restartOwnsConnection());
  dev.doughbay.fabric.ActionTrace.INSTANCE.record(dev.doughbay.fabric.ActionTrace.Scope.RESTART,dev.doughbay.fabric.ActionTrace.Event.PHASE,flow.phase(),"NONE",-1,-1,-1);
  if(!WatchConfig.get().enabled||!WatchConfig.get().autoDisconnect||cancelledByUser){tradingResume.cancel();return;}
  if(tradingResume.pending()){
   var session=dev.doughbay.fabric.DoughBayClient.automationSessionController();
   boolean ready=donutSession&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
     &&mc.getConnection().getConnection().isConnected()&&flow.phase()==ReconnectFlow.Phase.IDLE
     &&!schedule.active()&&!regionEscape.flow.ownsConnection()
     &&!dev.doughbay.fabric.DoughBayClient.restartOwnsConnection()
     &&!session.serverRestarting()
     &&dev.doughbay.fabric.SidebarBalance.freshSince(tradingResume.startedAt());
   var result=tradingResume.tick(System.currentTimeMillis(),ready,session.restartRecoveryMayResume(),session.snapshot().active(),session::resumeAfterRestart);
   if(result!=null&&result.ok()){
    dev.doughbay.fabric.DoughBayClient.LOGGER.info("Staff Watch reconnect: session resumed with retained caps");
    lastResumeBlocker="";
   }else if(result!=null&&!result.detail().equals(lastResumeBlocker)){
    lastResumeBlocker=result.detail();
    dev.doughbay.fabric.DoughBayClient.LOGGER.info("Staff Watch reconnect: safe resume pending ({}); retrying in 30 s",lastResumeBlocker);
   }
  }
 }
}
