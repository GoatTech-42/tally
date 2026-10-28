package dev.doughbay.fabric.staffwatch;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import dev.doughbay.fabric.staffwatch.module.ModuleRegistry;
import dev.doughbay.fabric.staffwatch.event.impl.Render2DEvent;
public final class StaffWatchClient {
 private static boolean initialized;
 private static Consumer<String> alertObserver=name->{};
 public static void initialize(Runnable open,Consumer<String> observer){
  if(initialized)return;
  alertObserver=observer;
  WatchConfig.load();ModuleRegistry.initialize();WatchConfig.get().apply();
  ClientCommandRegistrationCallback.EVENT.register((d,a)->d.register(literal("staffwatch").executes(c->{Minecraft.getInstance().execute(open);return 1;})));
  dev.doughbay.fabric.StaffWatchAlert.register();
  HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("doughbay","staff_watch"),(g,dt)->Render2DEvent.dispatch(g,dt.getGameTimeDeltaPartialTick(false)));
  initialized=true;
 }
 public static void detected(String name){alertObserver.accept(name);}
 public static void tick(){if(initialized)ReconnectController.INSTANCE.tick();}
 public static boolean controlsConnection(){return initialized&&ReconnectController.INSTANCE.controlsConnection();}
}
