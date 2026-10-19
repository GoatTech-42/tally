package dev.doughbay.fabric.staffwatch;

import com.mojang.authlib.GameProfile;
import dev.doughbay.fabric.staffwatch.module.impl.misc.StaffListModule;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombinedStaffPacketTest {
    @Test void actualPacketHookCountsKnownStaffButCombinedPacketsStillNeverQueueAlerts() throws Exception {
        var module=new StaffListModule();
        var health=(StaffPacketHealth)field("packetHealth").get(module);health.report(1000,0,0);
        var id=UUID.randomUUID();var known=new GameProfile(id,StaffListModule.DONUT_STAFF.getFirst());
        queue(module,packet(EnumSet.of(Action.ADD_PLAYER,Action.UPDATE_GAME_MODE),id,known,GameType.SURVIVAL));
        queue(module,packet(EnumSet.of(Action.ADD_PLAYER,Action.UPDATE_GAME_MODE),id,known,GameType.SPECTATOR));
        var other=UUID.randomUUID();
        queue(module,packet(EnumSet.of(Action.ADD_PLAYER,Action.UPDATE_GAME_MODE),other,new GameProfile(other,"TestNonStaff"),GameType.SPECTATOR));
        var switches=(java.util.Queue<?>)field("switches").get(module);
        assertTrue(switches.isEmpty(),"diagnostics must not promote global ADD packets into local alert evidence");
        String report=health.report(61000,0,0);
        assertTrue(report.contains("combinedStaffEntries=2 combinedStaffSpectators=1 combinedStaffModeChanges=1"));
        queue(module,packet(EnumSet.of(Action.UPDATE_GAME_MODE),id,null,GameType.SPECTATOR));
        assertEquals(1,switches.size(),"existing standalone mode detection stays wired");
    }
    private static ClientboundPlayerInfoUpdatePacket packet(EnumSet<Action> actions,UUID id,GameProfile profile,GameType mode)throws Exception {
        var p=new ClientboundPlayerInfoUpdatePacket(actions,List.of());
        var entries=ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");entries.setAccessible(true);
        entries.set(p,List.of(new Entry(id,profile,true,0,mode,null,false,0,null)));
        return p;
    }
    private static void queue(StaffListModule m,ClientboundPlayerInfoUpdatePacket p)throws Exception {
        var method=StaffListModule.class.getDeclaredMethod("queueSwitches",ClientboundPlayerInfoUpdatePacket.class);
        method.setAccessible(true);method.invoke(m,p);
    }
    private static Field field(String name)throws Exception {var f=StaffListModule.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
