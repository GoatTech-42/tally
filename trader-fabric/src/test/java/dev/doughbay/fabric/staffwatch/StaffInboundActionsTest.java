package dev.doughbay.fabric.staffwatch;

import dev.doughbay.fabric.staffwatch.module.impl.misc.AntiVanishModule;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaffInboundActionsTest {
    @Test void listedAndModeUpdatesBothReachTheExistingDetectorQueue() throws Exception {
        var detector = new AntiVanishModule();
        receive(detector, packet(EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE), false));
        assertEquals(List.of("TAB_HIDE", "GAMEMODE"), types(detector));
    }

    @Test void visibleEntryWithListedFlagStillDeliversItsModeChange() throws Exception {
        var detector = new AntiVanishModule();
        receive(detector, packet(EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE), true));
        assertEquals(List.of("GAMEMODE"), types(detector));
    }

    @Test void bundledUpdatesReachTheSameDetectorWithoutCancellingTheBundle() throws Exception {
        var detector = new AntiVanishModule();
        assertFalse(receive(detector, new ClientboundBundlePacket(List.of(
                packet(EnumSet.of(Action.UPDATE_GAME_MODE), true)))));
        assertEquals(List.of("GAMEMODE"), types(detector));
    }

    @Test void initialGlobalPlayerListStillCannotCreateLocalModeEvidence() throws Exception {
        var detector = new AntiVanishModule();
        receive(detector, packet(EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE), true));
        assertTrue(types(detector).isEmpty());
    }

    @Test void bundledCompletionReplyCannotCancelAnUnrelatedModeUpdate() throws Exception {
        var detector = new AntiVanishModule();
        var field = AntiVanishModule.class.getDeclaredField("completionInbox");
        field.setAccessible(true);
        var inbox = (LocalCompletionInbox) field.get(detector);
        inbox.register(30001);
        var range = StringRange.at(0);
        var reply = new ClientboundCommandSuggestionsPacket(30001,
                new Suggestions(range, List.of(new Suggestion(range, "FixtureName"))));
        assertFalse(receive(detector, new ClientboundBundlePacket(List.of(reply,
                packet(EnumSet.of(Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE), true)))));
        assertEquals(List.of("FixtureName"), inbox.take());
        assertEquals(List.of("GAMEMODE"), types(detector));
    }

    private static ClientboundPlayerInfoUpdatePacket packet(EnumSet<Action> actions, boolean listed) throws Exception {
        var packet = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        var entries = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField("entries");
        entries.setAccessible(true);
        entries.set(packet, List.of(new Entry(UUID.randomUUID(), null, listed, 0, GameType.SPECTATOR, null, false, 0, null)));
        return packet;
    }

    private static boolean receive(AntiVanishModule detector, Packet<?> packet) throws Exception {
        var method = AntiVanishModule.class.getDeclaredMethod("handleInbound", Packet.class);
        method.setAccessible(true);
        return (boolean) method.invoke(detector, packet);
    }

    private static List<String> types(AntiVanishModule detector) throws Exception {
        Field queue = AntiVanishModule.class.getDeclaredField("observations");
        queue.setAccessible(true);
        return ((Queue<?>) queue.get(detector)).stream().map(observation -> {
            try {
                var type = observation.getClass().getDeclaredField("type");
                type.setAccessible(true);
                return type.get(observation).toString();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }).toList();
    }
}
