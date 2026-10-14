package dev.doughbay.fabric.staffwatch.event.impl;

import dev.doughbay.fabric.staffwatch.event.Event;
import dev.doughbay.fabric.staffwatch.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TickEvent extends Event {
   public static final EventType<TickEvent> TYPE = new EventType<>("TickEvent");
   private static final TickEvent INSTANCE = new TickEvent();

   private TickEvent() {
   }

   public static void dispatch() {
      TYPE.dispatch(INSTANCE);
   }

   public static void dispatchPost() {
      TYPE.dispatchPost(INSTANCE);
   }
}
