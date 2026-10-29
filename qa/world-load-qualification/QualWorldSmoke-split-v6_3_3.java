package dev.doughbay.fabric;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import static org.lwjgl.opengl.GL33.*;import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundSource;import net.minecraft.world.Difficulty;
import dev.doughbay.fabric.staffwatch.module.ModuleRegistry;import dev.doughbay.fabric.staffwatch.module.impl.misc.StaffListModule;
/** Offline disposable-world HUD/staff qualification on MC 1.21.11, test-only mod jar. SCOPED v3:
 *  world + sidebar + orbit + staff-HUD only; the loopback connection phases were REMOVED (already
 *  qualified separately by the v2 loopback harness, 407 checks) and this run claims nothing about them.
 *  One copied owned prewarmed singleplayer world in the attempt's unique rundir; original never opened; never opens LAN, never
 *  touches an external server or a real account.
 *  Honest-fixture rule: every forced gate is labelled. Orbit is driven by the real OrbitCamera with ONLY
 *  the automation-session gate forced (reflection-set armed flag, production bytes untouched); the ImGui
 *  HUD is drawn by the real WindowMixin/ImGuiManager path over a fixture-synthetic tab-list entry that
 *  the module's own scan classifies (synthetic entry, not live detection evidence). The sidebar and
 *  nametag masks run with zero forcing: real scoreboard commands, real render path.
 *  Cadence disclosure: tick sampling is 35 ticks during bootstrap (world create/load) and 1 tick after
 *  world load, so post-bootstrap waits and settles are real ticks, not 35-tick samples. Settle holds were
 *  shortened accordingly - their only purpose is letting real frames draw; every binary assertion
 *  (frames-differ, hudRows classification, presence ONLINE) is unchanged or stronger.
 *  qual.group (all|w1|sidebar|orbit|imgui, default all) selects one phase GROUP per run for pair-split qualification:
 *  w1=world-loaded capture; sidebar=visible->mask pair; orbit=detach+nametag visible->mask pair+standdown;
 *  imgui=baseline+hud pair. Pairs keep same-session before/after; cross-pair state is explicitly reset at
 *  bootstrap and asserted clean (orbit disarmed) before the imgui baseline. */
public final class QualWorldSmoke implements ClientModInitializer{
 private int ticks,phase,checks,hold,settle,cadence=35;private final StringBuilder trail=new StringBuilder();
 private boolean s2Done,s1bDone; // case-16 capture sequencing guards (v6.3.3)
 private String sidebarLine="unverified",orbitLine="unverified",imguiLine="unverified",anchorLine="unverified";private String group=System.getProperty("qual.group","all");
 private boolean bannerSeen;private int bannerWait;
 private net.minecraft.client.multiplayer.PlayerInfo fixtureStaffInfo;private Field armedField;private Object session;private java.util.UUID fixtureStaffId;private Field playerInfoMapField,listedPlayersField;
 public void onInitializeClient(){
  // Same containment pins as the partial-GUI harness: no auto-join, no update traffic of any kind, no session resume.
  Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("update.check",0);Tuning.set("session.auto_resume",0);if(!(group.equals("all")||group.equals("w1")||group.equals("sidebar")||group.equals("orbit")||group.equals("imgui")||group.equals("banner")))throw new IllegalArgumentException("qual.group must be all|w1|sidebar|orbit|imgui|banner: "+group);
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void require(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException(message);}
 // Quiet ONLY on observed source state: a banner was raised (bannerSince>0) AND its render window
 // (BANNER_MILLIS+slide margin) has elapsed. If no banner ever raises (e.g. reused run dir with the
 // build stamp already recorded), the bounded budget in the caller fails the run instead of passing
 // on unprovable never-raised inference.
 private boolean bannerQuiet()throws Exception{
  Class<?> uc=Class.forName("dev.doughbay.fabric.UpdateCheck");
  Field bs=uc.getDeclaredField("bannerSince");bs.setAccessible(true);
  Field bm=uc.getDeclaredField("BANNER_MILLIS");bm.setAccessible(true);
  long since=bs.getLong(null);
  if(since<=0)return false;
  bannerSeen=true;
  return System.currentTimeMillis()-since>=bm.getLong(null)+400;
 }
 private void wait_(int limit,String what){require(++hold<limit,"timeout waiting for "+what);phase--;}
 private static byte[] bytes(String n)throws Exception{return Files.readAllBytes(Path.of(n));}
 private void tick(Minecraft mc){try{
  if(mc.getOverlay()!=null||++ticks<cadence)return;ticks=0;
  switch(phase++){
   case 0->{mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);
    mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);mc.resizeDisplay();
    anchorProbe();
    Tuning.set("privacy.hide_name",0);Tuning.set("camera.orbit",0); // explicit cross-pair state reset (defaults made explicit for split groups)
    mc.createWorldOpenFlows().openWorld("qual-world",()->{});phase=2;trail.append("OwnedPrewarmedWorld;");}
   case 2->{if(mc.level==null||mc.player==null||mc.screen!=null){wait_(900,"world load");break;}
    cadence=1; // bootstrap over: per-tick sampling from here on (disclosed in the class doc)
    if(settle<40){settle++;phase--;break;} // 40 ticks: spawn chunks settle and the first frames draw (was 40 samples at cadence 35)
    require(mc.getSingleplayerServer()!=null,"world is not an integrated-server singleplayer world");
    require(mc.getCurrentServer()==null,"client claims a remote server in singleplayer");
    if(group.equals("w1")||group.equals("all")){if(!capture("qual-w1-world-loaded.png")){phase--;break;}trail.append("InWorld;");hold=0;}
    if(group.equals("w1")){phase=13;break;}
    if(group.equals("sidebar")){phase=3;break;}
    if(group.equals("orbit")){phase=6;break;}
    if(group.equals("imgui")){phase=11;break;}
    if(group.equals("banner")){phase=14;break;}}
   case 3->{ // Sidebar mask, zero forcing: real scoreboard objective with a line holding the player name.
    Tuning.set("privacy.hide_name",0);
    var server=mc.getSingleplayerServer();require(server!=null,"integrated server gone");
    // Reset only the fixture's own scoreboard before rebuilding its visible/masked pair.
    var reset=server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
    server.getCommands().performPrefixedCommand(reset,"scoreboard objectives remove qual");
    var src=server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
    String me=mc.getUser().getName();
    server.getCommands().performPrefixedCommand(src,"scoreboard objectives add qual dummy Qual");
    server.getCommands().performPrefixedCommand(src,"scoreboard objectives setdisplay sidebar qual");
    server.getCommands().performPrefixedCommand(src,"scoreboard players set "+me+" qual 100");
    server.getCommands().performPrefixedCommand(src,"scoreboard players set OtherMarker qual 50");
    hold=0;}
   case 4->{if(hold<10){hold++;phase--;break;} // 10 ticks: several real frames (was 30 samples at cadence 35, disclosed)
    if(!capture("qual-w2-sidebar-visible.png")){phase--;break;}Tuning.set("privacy.hide_name",1);hold=0;}
   case 5->{if(hold<10){hold++;phase--;break;} // 10 ticks: several real frames (was 30 samples, disclosed)
    if(!capture("qual-w3-sidebar-masked.png")){phase--;break;}
    require(!Arrays.equals(bytes("qual-w2-sidebar-visible.png"),bytes("qual-w3-sidebar-masked.png")),"sidebar frames identical with mask on vs off");
    sidebarLine="sidebar=realobjective-visible-then-masked,frames-differ";hold=0;if(group.equals("sidebar")){phase=13;}}
   case 6->{ // Orbit: real OrbitCamera driven by production tick; ONLY the session gate is fixture-forced.
    Tuning.set("camera.orbit",1);Tuning.set("privacy.hide_name",0);
    session=DoughBayClient.automationSessionController();
    armedField=session.getClass().getDeclaredField("armed");armedField.setAccessible(true);
    armedField.setBoolean(session,true); // FIXTURE-FORCED: stands in for a running bot session, documented as such.
    hold=0;settle=0;}
   case 7->{if(!OrbitCamera.detached()||mc.getCameraEntity()==mc.player){wait_(200,"orbit camera attach");break;}
    if(settle<20){settle++;phase--;break;} // 20 ticks: camera drift + body render (was 40 samples at cadence 35, disclosed)
    boolean cam=false;for(var e:mc.level.entitiesForRendering())if(e instanceof CameraEntity)cam=true;
    require(cam,"orbit camera entity not in the client world");
    require(mc.getCameraEntity() instanceof CameraEntity,"view not sitting on the orbit camera entity");
    hold=0;}
   case 8->{if(hold<10){hold++;phase--;break;} // 10 ticks: several real frames (was 20 samples, disclosed)
    if(!bannerQuiet()){require(++bannerWait<1800,"timeout waiting for update banner raised-then-expired");phase--;break;}
    if(!capture("qual-w4-orbit-nametag-visible.png")){phase--;break;}Tuning.set("privacy.hide_name",1);hold=0;}
   case 9->{if(hold<10){hold++;phase--;break;} // 10 ticks: several real frames (was 20 samples, disclosed)
    if(!capture("qual-w5-orbit-nametag-masked.png")){phase--;break;}
    require(!Arrays.equals(bytes("qual-w4-orbit-nametag-visible.png"),bytes("qual-w5-orbit-nametag-masked.png")),"orbit frames identical with mask on vs off");
    orbitLine="orbit=banner-"+(bannerSeen?"raised-then-expired":"never-raised-this-session")+",detached-body-rendered,nametag-visible-then-masked,frames-differ(session-gate-forced)";hold=0;if(group.equals("orbit")){phase=10;}}
   case 10->{ // Stand the orbit down and prove the view returns to the player.
    Tuning.set("camera.orbit",0);if(armedField!=null&&session!=null)armedField.setBoolean(session,false);
    hold=0;settle=0;}
   case 11->{if(OrbitCamera.detached()||mc.getCameraEntity()!=mc.player){wait_(200,"orbit stand-down");break;}
    if(settle<10){settle++;phase--;break;} // real ticks after standdown: allow presented player-view frames before baseline
    if(group.equals("imgui")&&"unverified".equals(orbitLine))orbitLine="never-enabled-in-this-session(fresh-jvm,not a standdown proof)";
    else if(orbitLine.startsWith("orbit=banner-")&&orbitLine.endsWith("(session-gate-forced)"))orbitLine+=",standdown-clean"; // exactly-once label, orbit pair only
    session=DoughBayClient.automationSessionController();armedField=session.getClass().getDeclaredField("armed");armedField.setAccessible(true);
    require(!armedField.getBoolean(session),"orbit session armed at imgui baseline - cross-pair state leak");
    if(group.equals("orbit")){phase=13;break;}
    var module=ModuleRegistry.get(StaffListModule.class);require(module!=null,"staff list module not registered");
    require(!StaffListModule.shouldShowHud(),"staff hud unexpectedly showing before the fixture seeds it");
    if(!bannerQuiet()){require(++bannerWait<1800,"timeout waiting for update banner raised-then-expired");phase--;break;} // independent budget: raise<=SETTLE+tick, expiry=+BANNER_MILLIS
    if(!capture("qual-w6-imgui-baseline.png")){phase--;break;}
    module.setEnabled(true);
    seedStaff(mc); // fixture-synthetic tab entry injected at the connection level; the module's own scan classifies it.
    hold=0;settle=0;}
   case 12->{if(!StaffListModule.shouldShowHud()){wait_(40,"module scan classifying fixture tab entry");break;} // 40 ticks = up to 4 module scans (10-tick interval)
    var rows=StaffListModule.hudRows().rows();
    var mine=rows.stream().filter(r->fixtureStaffId.equals(r.id())).findFirst();
    require(mine.isPresent(),"hudRows has no row for the fixture staff id after shouldShowHud");
    require(mine.get().presence()==StaffListModule.Presence.ONLINE,"fixture staff presence not ONLINE: "+mine.get().presence());
    if(settle<10){settle++;phase--;break;} // 10 ticks AFTER classification: real frames draw the HUD row before capture (fps recorded below)
    if(!capture("qual-w7-imgui-hud.png")){phase--;break;}
    require(!Arrays.equals(bytes("qual-w6-imgui-baseline.png"),bytes("qual-w7-imgui-hud.png")),"imgui hud frames identical to baseline - hud did not draw");
    imguiLine="imgui=banner-"+(bannerSeen?"raised-then-expired":"never-raised-this-session")+",hudrow-classified(id-match,presence="+mine.get().presence()+",name="+mine.get().name()+",nameHidden="+mine.get().nameHidden()+"),hud-frame-differs-after-10tick-settle,fps="+mc.getFps()+"(synthetic-tab-entry,module-scan-classified)";
    var module=ModuleRegistry.get(StaffListModule.class);unseedStaff(mc);module.setEnabled(false);hold=0;settle=0;}
   case 14->{ // banner group: seed fixture staff INSIDE the banner window so the staff-join transition
    // (stale first frame) actually overlaps the raised banner - that is the deferral the fix introduces.
    // Timing grounded in source: scan every 10 ticks, alert gather 5 ticks, warning window 10s wall
    // (DISPLAY_NANOS), banner window 20s (BANNER_MILLIS) with bannerSince pinned during deferral, banner
    // onset = SETTLE_MILLIS after in-world. Seed at banner onset -> warning shows ~0.75s later, well
    // inside the banner window; warning expires ~9s before the banner does.
    if(!bannerSeen){
     Class<?> ucw=Class.forName("dev.doughbay.fabric.UpdateCheck");
     Field bsw=ucw.getDeclaredField("bannerSince");bsw.setAccessible(true);
     if(bsw.getLong(null)<=0){require(++bannerWait<1200,"timeout waiting for update banner raised (active-window group)");phase--;break;}
     bannerSeen=true;
    }
    var module=ModuleRegistry.get(StaffListModule.class);require(module!=null,"staff list module not registered");
    require(!StaffListModule.shouldShowHud(),"staff hud unexpectedly showing before the fixture seeds it");
    module.setEnabled(true);
    seedStaff(mc); // same fixture-synthetic tab entry as imgui group; module's own scan classifies it.
    // FIXTURE-FORCED warning entry point: StaffGhost.watching is the production SHARED detector entry
    // (same call StaffListModule.raise() and AntiVanishModule make on real detections). The scan-based
    // alert path does NOT fire for a synthetic tab entry: alert() only fires on game-mode SWITCH packets
    // (switches queue) or nearby body entities (trackBodies) - neither exists for a tab-list-only fixture.
    // What remains production-real from here: ReconnectController.alert + StaffWatchAlert.show + the
    // 10s window + warning-lane render geometry + banner clearance. Detection itself is NOT claimed.
    dev.doughbay.fabric.staffwatch.gui.StaffGhost.watching(fixtureStaffId,"Fallerfly",0);
    trail.append("warning=entry-point-forced(StaffGhost.watching,detection-not-exercised);");
    sidebarLine="not-run(banner group)";orbitLine="not-run(banner group)";imguiLine="not-run(banner group)"; // no standdown-clean claim: orbit never armed in this group
    hold=0;settle=0;}
   case 15->{ // staff now seeding inside the raised banner window: wait for the module scan to classify
    // the fixture entry, then capture the FIRST staff-visible frame inside the window. The stale-first-
    // transition is a single render-pass state and is NOT asserted from the tick thread (the pre-swap hook
    // observes post-decision frames); stale state at tick time recorded informationally only. Whether THIS
    // frame shows deferral (banner absent) or below-stack placement is a PIXEL semantic call for review,
    // fail-closed on any banner/stack overlap. Deferral pin check: bannerSince must NOT be expired yet.
    if(!StaffListModule.shouldShowHud()){wait_(40,"module scan classifying fixture tab entry");break;}
    Class<?> uc=Class.forName("dev.doughbay.fabric.UpdateCheck");
    Field bs=uc.getDeclaredField("bannerSince");bs.setAccessible(true);
    Field bm=uc.getDeclaredField("BANNER_MILLIS");bm.setAccessible(true);
    long since=bs.getLong(null);
    require(since>0,"bannerSince reset before staff-visible frame - deferral pin broken");
    require(System.currentTimeMillis()-since<bm.getLong(null),"banner window expired before staff-visible frame");
    var hudR=dev.doughbay.fabric.staffwatch.gui.HudRenderer.getInstance();
    boolean staleNow=hudR.stackGeometryStale();
    if(!capture("s1-banner-window-first-staff-frame.png")){phase--;break;}
    // Render-pass capture-time state from the pre-swap hook (the frame itself), plus tick-time values:
    // pin-loop (stale stuck true) vs fits/geometry (alertTop/height) vs timing each leaves a distinct signature.
    trail.append("banner-window-first-staff-frame(staleAtTick="+staleNow
      +",frameStale="+capStale
      +",frameBannerAgeMs="+capBannerAgeMs
      +",frameWarnActive="+capWarnActive
      +",frameWarnAgeMs="+capWarnAgeMs
      +",alertTop="+hudR.alertTop(mc.getWindow().getGuiScaledHeight())+");");
    hold=0;settle=0;}
   case 16->{ // settled frames INSIDE the overlap: warning (10s window from the forced entry point in
    // case 14) and banner (20s window, pinned through deferral) both live; stack measured by now (first
    // staff frame drawn >1s ago at fps=1). Warning capture FIRST so its shorter window cannot expire
    // mid-sequence. require(active) validates the warning stayed within its real 10s window at capture
    // time (the entry point itself was fixture-forced in case 14 - see trail label). Fail-closed
    // banner-window checks bind EVERY capture to the live banner window; hook-recorded frame values
    // are the render-pass evidence (tick-time reads alone cannot prove render-pass state).
    // Sequencing (v6.3.3): EACH named capture is requested exactly once (done-guards) and consumed
    // by its own call site; CaptureGate fails closed on any mismatched request/consumer.
    if(settle<15){settle++;phase--;break;} // ~0.75s: scan classify + stack measured + warning inside window
    var hudR=dev.doughbay.fabric.staffwatch.gui.HudRenderer.getInstance();
    require(!hudR.stackGeometryStale(),"stack still stale after settle - measured-on-draw lifecycle broken");
    Class<?> uc2=Class.forName("dev.doughbay.fabric.UpdateCheck");
    Field bs2=uc2.getDeclaredField("bannerSince");bs2.setAccessible(true);
    Field bm2=uc2.getDeclaredField("BANNER_MILLIS");bm2.setAccessible(true);
    long since2=bs2.getLong(null);
    require(since2>0&&System.currentTimeMillis()-since2<bm2.getLong(null),"banner not in live window at warning-lane capture");
    require(StaffWatchAlert.active(),"staff warning not active inside banner window");
    if(!s2Done){
     if(!capture("s2-warning-lane-plus-banner.png")){phase--;break;} // warning lane + banner + stack all live
     s2Done=true;
     trail.append("s2(frameBannerAgeMs="+capBannerAgeMs+",frameStale="+capStale+",frameWarnActive="+capWarnActive+",frameWarnAgeMs="+capWarnAgeMs+");");
    }
    require(StaffWatchAlert.active(),"staff warning expired before banner-below-stack capture");
    since2=bs2.getLong(null);
    require(since2>0&&System.currentTimeMillis()-since2<bm2.getLong(null),"banner not in live window at below-stack capture");
    if(!capture("s1b-banner-below-measured-stack.png")){phase--;break;} // banner below measured stack
    trail.append("s1b(frameBannerAgeMs="+capBannerAgeMs+",frameStale="+capStale+",frameWarnActive="+capWarnActive+",frameWarnAgeMs="+capWarnAgeMs+");");
    hold=0;settle=0;}
   case 17->{ // post-banner-expiry regression baseline from THIS fixed candidate (not old b05 frames).
    // Honest scope: the 10s warning window has ALSO expired by now, so this frame proves stack-only
    // post-expiry geometry; warning-lane geometry evidence is s2 above, NOT this frame.
    if(!bannerQuiet()){require(++bannerWait<1800,"timeout waiting for update banner raised-then-expired");phase--;break;}
    if(settle<10){settle++;phase--;break;}
    if(!capture("s3-post-expiry-stack-only-geometry.png")){phase--;break;}
    trail.append("s3=stack-only-post-banner-expiry(warning-also-expired);");
    var module=ModuleRegistry.get(StaffListModule.class);unseedStaff(mc);module.setEnabled(false);hold=0;settle=0;phase=13;}
   case 13->{Files.writeString(Path.of("qual-world-success.txt"),
     "Offline disposable-world HUD/staff qualification on 1.21.11 (scoped: world+sidebar+orbit+staff HUD; loopback phases removed - qualified separately by the v2 loopback harness): owned copied prewarmed creative world (integrated server, disk-backed disposable copy), real sidebar objective mask pair, orbit camera detach with nametag mask pair, module-scan-classified staff HUD frame; trail="+trail+" "+sidebarLine+" "+orbitLine+" "+imguiLine+" "+anchorLine+" capture=pre-swap-back-buffer(about-to-present frame incl ImGui via Window.updateDisplay hook, request/wait synchronized; front unreadable on this Xvfb/Mesa stack per in-env probe),baseline=staff-disabled-after-10tick-standdown,no-w5-difference-required world=owned-prewarmed-copy,cadence=bootstrap35-to-1 group="+group+" checks="+checks);
    mc.stop();}
  }
 }catch(Throwable e){try{Files.writeString(Path.of("qual-world-error.txt"),e+" trail="+trail+" "+sidebarLine+" "+orbitLine+" "+imguiLine+" "+anchorLine);}catch(Exception ignored){}mc.stop();}}
 /** Injects one fixture-synthetic tab-list entry at the connection level, replicating the end state
  *  vanilla's handlePlayerInfoUpdate produces for ADD_PLAYER + UPDATE_LISTED: a PlayerInfo (same public
  *  GameProfile ctor vanilla's handler uses, game mode SURVIVAL) in the connection's playerInfoMap and
  *  the PlayerInfo in listedPlayers. 1.21.11 has no client-constructible packet ctor that takes entries
  *  (mojmap-verified: the only EnumSet ctor takes Collection<ServerPlayer>), so the packet route does
  *  not exist client-side. The two fields resolve through the loader's mapping resolver and are pinned
  *  to intermediary class_634 / field_3693 (playerInfoMap) / field_40489 (listedPlayers), hard fail on
  *  drift - the same anchor discipline as the rest of this harness. NO module internals are touched:
  *  the module's own 10-tick scan performs every classification step (isStaffName/isAccount/rank/
  *  presence/sortView) itself. The name must be a real DonutSMP staff name because isStaffName requires
  *  membership; randomUUID() is v4 and passes isAccount (rejects only v2). Synthetic tab entry in a
  *  disposable offline world - not live detection evidence. */
 private void seedStaff(Minecraft mc)throws Exception{
  var conn=mc.getConnection();require(conn!=null,"no client connection for staff fixture");
  fixtureStaffId=UUID.randomUUID();
  var info=new net.minecraft.client.multiplayer.PlayerInfo(new com.mojang.authlib.GameProfile(fixtureStaffId,"Fallerfly"),false);
  require(info.getGameMode()==net.minecraft.world.level.GameType.SURVIVAL,"PlayerInfo default game mode drift");
  fixtureStaffInfo=info;
  var resolver=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver();
  String owner=resolver.mapClassName("official","hig");
  require("net.minecraft.class_634".equals(owner),"ClientPacketListener intermediary drift: "+owner);
  playerInfoMapField=conn.getClass().getDeclaredField(resolver.mapFieldName("official","hig","D","Ljava/util/Map;"));
  require("field_3693".equals(playerInfoMapField.getName()),"playerInfoMap intermediary drift: "+playerInfoMapField.getName());
  listedPlayersField=conn.getClass().getDeclaredField(resolver.mapFieldName("official","hig","E","Ljava/util/Set;"));
  require("field_40489".equals(listedPlayersField.getName()),"listedPlayers intermediary drift: "+listedPlayersField.getName());
  playerInfoMapField.setAccessible(true);listedPlayersField.setAccessible(true);
  ((java.util.Map)playerInfoMapField.get(conn)).put(fixtureStaffId,info);
  ((java.util.Set)listedPlayersField.get(conn)).add(info);
 }
 private void unseedStaff(Minecraft mc)throws Exception{
  var conn=mc.getConnection();
  if(conn==null||fixtureStaffId==null||playerInfoMapField==null)return;
  ((java.util.Map)playerInfoMapField.get(conn)).remove(fixtureStaffId);
  ((java.util.Set)listedPlayersField.get(conn)).remove(fixtureStaffInfo);
 }
 /** Positive anchor proof for every require=0 world mixin plus the staffwatch mixin targets; same
  *  resolver pattern as the partial-GUI harness: official (obfuscated) source rows pinned to the
  *  exact intermediary names the packaged runtime must produce, then runtime existence checks. */
 private void anchorProbe()throws Exception{
  var resolver=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver();
  String[][] anchors={
   {"hwp","a","(Lcgk;Lidf;F)V","net.minecraft.class_897","method_62354"},   // EntityRenderer.extractRenderState - NameTagMixin
   {"gir","a","(Lgio;Ljava/lang/String;IIIZ)V","net.minecraft.class_332","method_51433"}, // GuiGraphics.drawString shadowed - GuiTextNameMixin
   {"gir","a","(Lgio;Ljava/lang/String;III)V","net.minecraft.class_332","method_25300"},  // GuiGraphics.drawCenteredString(String) - GuiTextNameMixin
   {"gir","b","(Lgio;Ljava/lang/String;III)V","net.minecraft.class_332","method_25303"},  // GuiGraphics.drawString(String) - GuiTextNameMixin
   {"gir","a","(Lgio;Lyh;IIIZ)V","net.minecraft.class_332","method_51439"}, // GuiGraphics.drawString(Component) - SidebarNameMixin invoke target
   {"giq","a","(Lgir;Lfuj;)V","net.minecraft.class_329","method_1757"},     // Gui.displayScoreboardSidebar - SidebarNameMixin
   {"hoh","a","(Lger;Lhtx;Lgez;Likq;)V","net.minecraft.class_761","method_72917"}, // LevelRenderer.extractVisibleEntities - LevelExtractorMixin
   {"ger","h","()Lcgk;","net.minecraft.class_4184","method_19331"},          // Camera.entity - LevelExtractorMixin invoke target ordinal 3
   {"gfj","x","()V","net.minecraft.class_310","method_1574"},               // Minecraft.tick - MinecraftMixin
   {"gfj","d","(Z)V","net.minecraft.class_310","method_1523"},              // Minecraft.runTick - WindowMixin
   {"fyk","a","(Lfwf;)V","net.minecraft.class_1041","method_15998"},        // Window.updateDisplay - WindowMixin invoke target
  };
  int found=0;
  for(String[] a:anchors){
   String owner=resolver.mapClassName("official",a[0]);
   require(a[3].equals(owner),"anchor owner mapped wrong: "+a[0]+" -> "+owner);
   String mapped=resolver.mapMethodName("official",a[0],a[1],a[2]);
   require(a[4].equals(mapped),"anchor method mapped wrong: "+a[0]+"."+a[1]+a[2]+" -> "+mapped);
   boolean has=false;for(var m:Class.forName(owner).getDeclaredMethods())if(m.getName().equals(mapped))has=true;
   require(has,"anchor method absent at runtime: "+owner+"."+mapped);
   found++;
  }
  anchorLine="anchors=resolver "+found+"/"+anchors.length+" ok";
 }
 // Request/wait capture, sequenced by CaptureGate: the tick phase asks for a frame, then HOLDS
 // (phase--) until the Window.updateDisplay hook has captured a FRESH frame rendered after the
 // request. Assertions only ever read a post-request, about-to-present frame - never async stale
 // state (fail-closed on hook error via the gate). Each named capture is requested exactly once
 // (caller done-guards) and consumed by its own requester; a mismatched request or consumer FAILS
 // CLOSED instead of silently overwriting a pending capture. v6.3.2 defect this closes: the old
 // unconditional pendingCapture=name let case-16's s2 request overwrite the pending s1b request
 // every tick, s1b could never complete, and the spin burned the 10s warning window.
 private static final CaptureGate GATE=new CaptureGate();
 private static boolean capture(String name){return GATE.request(name);}
 // Mixin hook entry, render thread, HEAD of Window.updateDisplay (intermediary method_15998):
 // after the world+HUD+ImGui render has completed into the back buffer, BEFORE glfwSwapBuffers
 // presents it. Reads GL_BACK from the default framebuffer: under GLX copy-swap semantics (this
 // Xvfb/Mesa stack, verified by the in-env GLSnap probe) the back buffer here holds the full
 // about-to-present frame. GL_FRONT is NOT read: the probe showed GL_FRONT reads all-zero here.
 public static volatile long capBannerAgeMs=-1;public static volatile boolean capStale;public static volatile boolean capWarnActive;public static volatile long capWarnAgeMs=-1;
 public static void captureBeforeSwap(){
  String name=GATE.pendingForHook();if(name==null)return;
  try{
   try{ // render-pass capture-time state: this is the frame the assertions care about
    Class<?> uc=Class.forName("dev.doughbay.fabric.UpdateCheck");
    java.lang.reflect.Field bs=uc.getDeclaredField("bannerSince");bs.setAccessible(true);
    long sn=bs.getLong(null);
    capBannerAgeMs=sn>0?System.currentTimeMillis()-sn:-1;
    capStale=dev.doughbay.fabric.staffwatch.gui.HudRenderer.getInstance().stackGeometryStale();
    // StaffWatchAlert.render() draws iff active() (same nanoTime window; the bar's `remaining` is
    // cosmetic) - so shownAt/age + active at pre-swap IS the draw truth for this frame, not a flag.
    java.lang.reflect.Field sa=Class.forName("dev.doughbay.fabric.StaffWatchAlert").getDeclaredField("shownAt");sa.setAccessible(true);
    long shownAt=sa.getLong(null);
    capWarnAgeMs=shownAt!=0?(System.nanoTime()-shownAt)/1_000_000L:-1;
    capWarnActive=dev.doughbay.fabric.StaffWatchAlert.active();
   }catch(Throwable ignored){capBannerAgeMs=-1;capWarnAgeMs=-1;}
   var mc=Minecraft.getInstance();int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
   int readBuffer=glGetInteger(GL_READ_BUFFER);
   int fb=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),align=glGetInteger(GL_PACK_ALIGNMENT);
   int row=glGetInteger(GL_PACK_ROW_LENGTH),sx=glGetInteger(GL_PACK_SKIP_PIXELS),sy=glGetInteger(GL_PACK_SKIP_ROWS);
   var bytes=MemoryUtil.memAlloc(w*h*4);
   try{
    glBindFramebuffer(GL_READ_FRAMEBUFFER,0);glReadBuffer(GL_BACK);glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
    glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);
    glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=(y*w+x)*4;image.setRGB(x,h-1-y,((bytes.get(p)&255)<<16)|((bytes.get(p+1)&255)<<8)|(bytes.get(p+2)&255));}
    ImageIO.write(image,"png",Path.of(name).toFile());
   }finally{MemoryUtil.memFree(bytes);glBindFramebuffer(GL_READ_FRAMEBUFFER,fb);glReadBuffer(readBuffer);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glPixelStorei(GL_PACK_ALIGNMENT,align);glPixelStorei(GL_PACK_ROW_LENGTH,row);glPixelStorei(GL_PACK_SKIP_PIXELS,sx);glPixelStorei(GL_PACK_SKIP_ROWS,sy);}
  }catch(Throwable t){GATE.fail(String.valueOf(t));}
  GATE.complete();
 }
}
