package dev.doughbay.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real command transport with an isolated, non-networked client fixture. */
class ListingPacketHandoffTest {
    @Test
    void alreadySelectedListingSlotDoesNotGenerateAnotherSelectionPacket() throws Exception {
        Fixture fixture = fixture();
        fixture.transport.send("ah sell 1000");
        assertEquals(List.of("ah sell 1000"), fixture.connection.commands);
        assertEquals(0, fixture.connection.selections,
                "A sell must not force a redundant hotbar packet after normal selection already settled");
    }

    @Test
    void sellRetryMustNotReselectTheUnchangedSlot() throws Exception {
        Fixture fixture = fixture();
        fixture.transport.send("ah sell 1000");
        fixture.transport.send("ah sell 1000");
        assertEquals(2, fixture.connection.commands.size());
        assertEquals(0, fixture.connection.selections,
                "Retrying a command does not change the selected slot");
    }

    private record Fixture(AutomatedExecutionDriver.CommandTransport transport,
                           RecordingConnection connection) { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Fixture fixture() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Minecraft client = allocate(Minecraft.class);
        LocalPlayer player = allocate(LocalPlayer.class);
        RecordingConnection connection = allocate(RecordingConnection.class);
        connection.commands = new ArrayList<>();
        Inventory inventory = new Inventory(player, new EntityEquipment());
        inventory.setSelectedSlot(8);
        // Only exercise packet handoff here; stack authorization is covered by the command guard tests.
        set(Player.class, player, "inventory", inventory);
        InventoryMenu menu = allocate(InventoryMenu.class);
        set(Player.class, player, "inventoryMenu", menu);
        player.containerMenu = menu;
        set(LocalPlayer.class, player, "connection", connection);
        client.player = player;
        set(Minecraft.class, client, "gui", allocate(Gui.class));
        AutomatedExecutionDriver driver = new AutomatedExecutionDriver();
        Field operation = AutomatedExecutionDriver.class.getDeclaredField("operation");
        operation.setAccessible(true);
        operation.set(driver, Enum.valueOf((Class) operation.getType(), "LIST"));
        set(AutomatedExecutionDriver.class, driver, "listingCommandSlot", 8);
        var transport = AutomatedExecutionDriver.class.getDeclaredMethod("commandTransport", Minecraft.class);
        transport.setAccessible(true);
        return new Fixture((AutomatedExecutionDriver.CommandTransport) transport.invoke(driver, client), connection);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingConnection extends ClientPacketListener {
        List<String> commands;
        int selections;
        private RecordingConnection() { super(null, null, null); }
        @Override public void sendCommand(String command) { send(new ServerboundChatCommandPacket(command)); }
        @Override public void send(Packet<?> packet) {
            if (packet instanceof ServerboundSetCarriedItemPacket) selections++;
            if (packet instanceof ServerboundChatCommandPacket command) {
                commands.add(command.command());
                // Unit tests do not install mixins; simulate only its connection-handoff observation.
                CommandSendProbe.observe(command.command());
            }
        }
    }
}
