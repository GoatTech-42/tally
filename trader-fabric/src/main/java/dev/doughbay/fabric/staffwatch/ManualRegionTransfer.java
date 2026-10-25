package dev.doughbay.fabric.staffwatch;

import dev.doughbay.fabric.DoughBayClient;
import dev.doughbay.fabric.staffwatch.event.EventPriority;
import dev.doughbay.fabric.staffwatch.event.impl.PacketReceiveNettyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared GUI/web/Discord adapter. All requests and ticks run on the client thread. */
public final class ManualRegionTransfer {
    private static final AtomicInteger IDS=new AtomicInteger(150_000);
    private static final RegionArrivalProof proof=new RegionArrivalProof();
    private static volatile String status="Choose a region, then Switch region";
    private static volatile String command="";
    private static long requestedAt;
    private static Object world, player;
    private static double x,y,z;
    private static boolean ownedHold, resume;
    private static final ManualRegionFlow FLOW=new ManualRegionFlow(new ManualRegionFlow.Client() {
        public boolean available() {
            var mc=Minecraft.getInstance(); var server=mc.getCurrentServer();
            return mc.player!=null && mc.level!=null && mc.getConnection()!=null && !mc.hasSingleplayerServer()
                && server!=null && DonutServer.matches(server.ip) && !DoughBayClient.restartOwnsConnection()
                && !ReconnectController.INSTANCE.controlsConnection() && !DoughBayClient.executionDriver().refusesTeleports()
                && (!ownedHold || !resume || DoughBayClient.automationSessionController().snapshot().state().name().equals("PAUSED"));
        }
        public boolean prepare() {
            var mc=Minecraft.getInstance();var session=DoughBayClient.automationSessionController();
            if(!session.safeToLeave() || !mc.player.containerMenu.getCarried().isEmpty()
                    || DoughBayClient.executionDriver().externalCommandQueued())return false;
            String state=session.snapshot().state().name();
            if(!List.of("SCANNING","MONITORING","PAUSED","STOPPED","IDLE").contains(state))return false;
            resume=state.equals("SCANNING")||state.equals("MONITORING");
            if(resume)session.pause("Manual region switch");
            session.holdForStaffTransfer(true);ownedHold=true;
            DoughBayClient.executionDriver().setExternalAllowed(true);
            if(mc.player.containerMenu!=mc.player.inventoryMenu)mc.player.closeContainer();
            return true;
        }
        public boolean checkpointReady() {
            var mc=Minecraft.getInstance();var session=DoughBayClient.automationSessionController();
            if(!session.safeToLeave()||!mc.player.containerMenu.getCarried().isEmpty())return false;
            if(mc.player.containerMenu!=mc.player.inventoryMenu)mc.player.closeContainer();
            return true;
        }
        public void send(String destination) {
            var mc=Minecraft.getInstance();command="rtp "+destination;requestedAt=System.currentTimeMillis();
            world=mc.level;player=mc.player;x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();
            proof.begin(requestedAt);DoughBayClient.executionDriver().sendWhenClear(mc,command);
        }
        public boolean arrived() {
            var mc=Minecraft.getInstance();var driver=DoughBayClient.executionDriver();
            boolean dispatched=driver.lastEscapeCommand().equals(command)&&driver.lastEscapeSentAt()>=requestedAt;
            double dx=mc.player.getX()-x,dy=mc.player.getY()-y,dz=mc.player.getZ()-z;
            boolean moved=mc.level!=world||mc.player!=player||dx*dx+dy*dy+dz*dz>=128*128;
            if(proof.needsProbe(moved)) { int id=IDS.incrementAndGet();proof.probe(id);
                mc.getConnection().send(new ServerboundCommandSuggestionPacket(id,StaffCompletionProbe.REQUEST)); }
            return proof.confirmed(dispatched,moved);
        }
        public void release() {
            if(!command.isEmpty())DoughBayClient.executionDriver().withdrawExternal(command);
            command="";
            if(ownedHold)DoughBayClient.automationSessionController().holdForStaffTransfer(false);
            ownedHold=false;
        }
        public void resumeOwnedPause() {
            if(resume)DoughBayClient.automationSessionController().resumeAfterManualRegionTransfer();
            resume=false;
        }
    },System::currentTimeMillis);
    private ManualRegionTransfer() {}
    public static void init() {
        PacketReceiveNettyEvent.TYPE.listen(ManualRegionTransfer.class,EventPriority.HIGHEST,event->{
            if(command.isEmpty())return;
            var driver=DoughBayClient.executionDriver();
            if((event.packet instanceof ClientboundRespawnPacket || event.packet instanceof ClientboundPlayerPositionPacket)
                    && driver.lastEscapeCommand().equals(command))proof.teleport(System.currentTimeMillis(),driver.lastEscapeSentAt());
            if(event.packet instanceof ClientboundCommandSuggestionsPacket reply && proof.complete(reply.id()))event.setCancelled(true);
        });
    }
    public static boolean request(String region) { boolean accepted=FLOW.request(region);status=FLOW.status();return accepted; }
    public static String status() { return status; }
    public static void tick(Minecraft mc) { FLOW.tick();status=FLOW.status(); }
    public static void cancel() { FLOW.cancel("Region switch cancelled by another connection action");resume=false;status=FLOW.status(); }
}
