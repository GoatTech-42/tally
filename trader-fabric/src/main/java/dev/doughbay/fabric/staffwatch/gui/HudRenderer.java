package dev.doughbay.fabric.staffwatch.gui;

import java.util.*;import imgui.*;import net.fabricmc.api.Environment;import net.fabricmc.api.EnvType;

import net.minecraft.client.Minecraft;import net.minecraft.resources.Identifier;import net.minecraft.client.resources.DefaultPlayerSkin;

import com.mojang.blaze3d.textures.GpuTexture;import com.mojang.blaze3d.opengl.GlTexture;import org.lwjgl.opengl.GL11;

import net.minecraft.client.multiplayer.*;

import dev.doughbay.fabric.staffwatch.config.*;import dev.doughbay.fabric.staffwatch.imgui.ImGuiManager;import dev.doughbay.fabric.staffwatch.module.impl.misc.*;

public final class HudRenderer {

 private static final HudRenderer INSTANCE=new HudRenderer(); public static HudRenderer getInstance(){return INSTANCE;}

 private final Map<UUID,Identifier> staffSkins=new HashMap<>(); private final Set<GpuTexture> nearestSkins=Collections.newSetFromMap(new WeakHashMap<>());

 private final Map<String,Float> widths=new HashMap<>();
 private float stackHeight;
 public int alertTop(int guiHeight){var mc=Minecraft.getInstance();if(mc.level==null||mc.gui.screen() instanceof dev.doughbay.fabric.DoughBayScreen||mc.gui.hud.isHidden()||!dev.doughbay.fabric.staffwatch.WatchConfig.get().showHud||(!StaffListModule.shouldShowHud()&&!AntiVanishModule.shouldShowHud()))return 12;return 12+(int)Math.ceil(stackHeight*guiHeight/mc.getWindow().getHeight())+8;}

 private static float scaleOf(HudLayoutStore.HudElementState state){return Math.max(.75F,Minecraft.getInstance().getWindow().getHeight()/1080F);}

 private static float hudRounding(){return 6;}

 private static Surface hudSurface(HudLayoutStore.HudElementState state){return new Surface();}

 private float slot(String key,int px,float target,float dt){String k=key+px;float old=widths.getOrDefault(k,target);float v=old+(target-old)*(1-(float)Math.exp(-12*dt));widths.put(k,v);return v;}

 private void removeSlot(String key,int px){widths.remove(key+px);}

 public void render(){var mc=Minecraft.getInstance();if(mc.gui.screen() instanceof dev.doughbay.fabric.DoughBayScreen||mc.level==null||mc.gui.hud.isHidden()||!dev.doughbay.fabric.staffwatch.WatchConfig.get().showHud)return;

  var d=ImGui.getBackgroundDrawList();var state=new HudLayoutStore.HudElementState();state.x=12;float dt=ImGui.getIO().getDeltaTime();
  var staff=StaffListModule.shouldShowHud()?planStaff(state,false,dt):null;
  var vanish=AntiVanishModule.shouldShowHud()?planVanish(state,false,dt):null;
  float gap=staff!=null&&vanish!=null?8:0;
  float total=(staff==null?0:staff.height())+(vanish==null?0:vanish.height())+gap;
  stackHeight=total;
  state.y=12F*mc.getWindow().getHeight()/mc.getWindow().getGuiScaledHeight();
  if(staff!=null){state.width=staff.width();state.x=(mc.getWindow().getWidth()-state.width)/2F;state.height=staff.height();drawStaff(d,state,staff,true,new DrawTransform(0,0,1));state.y+=state.height+gap;}
  if(vanish!=null){state.width=vanish.width();state.x=(mc.getWindow().getWidth()-state.width)/2F;state.height=vanish.height();drawVanish(d,state,vanish,true,new DrawTransform(0,0,1));}

 }

   private static float rowsRadius(float s) {

      return Math.min(hudRounding(), 6.0F) * s;

   }



   private void drawRows(

      ImDrawList dl, List<HudRenderer.Step> steps, Surface surface, float x, float y, float w, float h, float radius, float s, boolean shadow

   ) {

      if (shadow) {

         drawRowsShadow(dl, steps, radius, s);

      }



      int fill = GuiPalette.fill(surface);



      for (int i = 0; i < steps.size(); i++) {

         HudRenderer.Step q = steps.get(i);

         HudRenderer.Step up = i > 0 ? steps.get(i - 1) : null;

         HudRenderer.Step dn = i < steps.size() - 1 ? steps.get(i + 1) : null;

         int corners = convexCorners(q, up, dn);

         int color = HudChrome.scaleAlpha(fill, q.alpha());

         if (corners == 256) {

            dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), color);

         } else {

            dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), color, radius, corners);

         }



         SurfaceBackground.drawPart(dl, surface, x, y, w, h, q.l(), q.t(), q.r() - q.l(), q.b() - q.t(), q.alpha(), radius, corners);

         float stepH = q.b() - q.t();

         if (up != null && up.l() < q.l() - 0.5F) {

            fillet(dl, q.l(), q.t(), -1, 1, Math.min(radius, Math.min(q.l() - up.l(), stepH)), color);

         }



         if (up != null && up.r() > q.r() + 0.5F) {

            fillet(dl, q.r(), q.t(), 1, 1, Math.min(radius, Math.min(up.r() - q.r(), stepH)), color);

         }



         if (dn != null && dn.l() < q.l() - 0.5F) {

            fillet(dl, q.l(), q.b(), -1, -1, Math.min(radius, Math.min(q.l() - dn.l(), stepH)), color);

         }



         if (dn != null && dn.r() > q.r() + 0.5F) {

            fillet(dl, q.r(), q.b(), 1, -1, Math.min(radius, Math.min(dn.r() - q.r(), stepH)), color);

         }

      }

   }



   private static int convexCorners(HudRenderer.Step q, HudRenderer.Step up, HudRenderer.Step dn) {

      int flags = 0;

      if (up == null || up.l() > q.l() + 0.5F) {

         flags |= 16;

      }



      if (up == null || up.r() < q.r() - 0.5F) {

         flags |= 32;

      }



      if (dn == null || dn.l() > q.l() + 0.5F) {

         flags |= 64;

      }



      if (dn == null || dn.r() < q.r() - 0.5F) {

         flags |= 128;

      }



      return flags == 0 ? 256 : flags;

   }



   private static void fillet(ImDrawList dl, float px, float py, int dirX, int dirY, float r, int color) {

      if (!(r < 1.0F)) {

         float cx = px + dirX * r;

         float cy = py + dirY * r;

         float a0 = dirX < 0 ? 0.0F : (float) Math.PI;

         float a1 = dirY > 0 ? (dirX < 0 ? (float) (-Math.PI / 2) : (float) (Math.PI * 3.0 / 2.0)) : (float) (Math.PI / 2);

         dl.pathClear();

         dl.pathLineTo(px, py);

         dl.pathArcTo(cx, cy, r, a0, a1, 8);

         dl.pathFillConcave(color);

      }

   }



   private static void drawRowsShadow(ImDrawList dl, List<HudRenderer.Step> steps, float radius, float s) {

      for (int k = 3; k >= 1; k--) {

         float spread = k * Math.max(1.0F, s);



         for (int i = 0; i < steps.size(); i++) {

            HudRenderer.Step q = steps.get(i);

            boolean first = i == 0;

            boolean last = i == steps.size() - 1;

            int corners = convexCorners(q, first ? null : steps.get(i - 1), last ? null : steps.get(i + 1));

            float top = q.t() - (first ? spread : 0.0F);

            float bottom = q.b() + (last ? spread + Math.max(1.0F, s) : 0.0F);

            int color = HudChrome.scaleAlpha(218103808, q.alpha());

            if (corners == 256) {

               dl.addRectFilled(q.l() - spread, top, q.r() + spread, bottom, color);

            } else {

               dl.addRectFilled(q.l() - spread, top, q.r() + spread, bottom, color, radius + spread, corners);

            }

         }

      }

   }



   private HudRenderer.VanishPlan planVanish(HudLayoutStore.HudElementState state, boolean editMode, float dt) {

      float s = scaleOf(state);

      HudRenderer.Type t = HudRenderer.Type.of(s);

      float rowFull = 22.0F * s;

      List<HudRenderer.VanishLine> lines = new ArrayList<>();



      for (AntiVanishModule.HudEntry entry : AntiVanishModule.hudEntries()) {

         String tag = AntiVanishModule.hudTag(entry);

         String value = AntiVanishModule.hudValue(entry);

         boolean urgent = "ALERT".equals(tag) || "VANISH".equals(tag);

         lines.add(new HudRenderer.VanishLine(tag, value, urgent, HudChrome.width(t.label(), t.labelPx(), tag), HudChrome.width(t.text(), t.textPx(), value)));

      }



      if (state.anchorBottom) {

         Collections.reverse(lines);

      }



      if (lines.isEmpty()) {

         this.removeSlot("vanish", t.textPx());

         float w = editMode ? 8.0F * s * 2.0F + HudChrome.width(t.label(), t.labelPx(), "No vanish alerts") : 1.0F;

         return new HudRenderer.VanishPlan(lines, t, 0.0F, (float)Math.round(w), (float)Math.round(rowFull));

      } else {

         float tagColumn = 0.0F;

         float valueW = 0.0F;



         for (HudRenderer.VanishLine line : lines) {

            tagColumn = Math.max(tagColumn, line.tagW());

            valueW = Math.max(valueW, line.valueW());

         }



         float target = 8.0F * s * 2.0F + tagColumn + 8.0F * s + valueW;

         float w = this.slot("vanish", t.textPx(), (float)Math.ceil(target), dt);

         return new HudRenderer.VanishPlan(lines, t, tagColumn, (float)Math.round(w), (float)Math.round(rowFull * lines.size()));

      }

   }



   private void drawVanish(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.VanishPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {

      float a = tr.alpha();

      if (!(a <= 0.001F)) {

         float s = scaleOf(state);

         float x = Math.round(state.x + tr.xOffset());

         float y = Math.round(state.y + tr.yOffset());

         float w = state.width;

         float h = state.height;

         HudRenderer.Type t = plan.type();

         float rowFull = 22.0F * s;

         float pad = 8.0F * s;

         int count = Math.max(1, plan.lines().size());

         List<HudRenderer.Step> steps = new ArrayList<>(count);



         for (int i = 0; i < count; i++) {

            steps.add(

               new HudRenderer.Step(plan.lines().isEmpty() ? -1 : i, x, (float)Math.round(y + rowFull * i), x + w, (float)Math.round(y + rowFull * (i + 1)), a)

            );

         }



         this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);

         if (plan.lines().isEmpty()) {

            HudChrome.text(

               dl,

               t.label(),

               t.labelPx(),

               x + pad,

               y + (float)Math.floor((rowFull - t.labelPx()) * 0.5F),

               HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),

               "No vanish alerts",

               true

            );

         } else {

            float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);



            for (HudRenderer.Step q : steps) {

               HudRenderer.VanishLine line = plan.lines().get(q.item());

               float ty = q.t() + textInset;

               float labelTop = ty + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx());

               float tx = q.l() + pad;

               HudChrome.text(

                  dl, t.label(), t.labelPx(), tx, labelTop, HudChrome.scaleAlpha(line.urgent() ? HudChrome.WARN : HudChrome.TEXT_LABEL, a), line.tag(), true

               );

               HudChrome.text(dl, t.text(), t.textPx(), tx + plan.tagColumn() + 8.0F * s, ty, HudChrome.scaleAlpha(-1, a), line.value(), true);

            }

         }

      }

   }



   private HudRenderer.StaffPlan planStaff(HudLayoutStore.HudElementState state, boolean editMode, float dt) {

      float s = scaleOf(state);

      HudRenderer.Type t = HudRenderer.Type.of(s);

      float rowFull = 22.0F * s;

      StaffListModule.HudList list = StaffListModule.hudRows();

      List<HudRenderer.StaffLine> lines = new ArrayList<>(list.rows().size() + 1);



      for (StaffListModule.HudRow row : list.rows()) {

         int face = this.staffFace(row.id(), row.nameHidden());

         String status = row.presence() == StaffListModule.Presence.VANISHED

            ? "VANISHED"

            : (row.presence() == StaffListModule.Presence.SPECTATOR ? "SPECTATOR" : "");

         float statusW = status.isEmpty() ? 0.0F : HudChrome.width(t.label(), t.labelPx(), status);

         if (row.sameServer()) {

            String value = row.nearby() ? row.distanceLabel() : row.sameServerAge();

            float valueW = HudChrome.tabularWidth(t.text(), t.textPx(), value);

            float serverTagW = HudChrome.width(t.label(), t.labelPx(), "WATCHING YOU");

            float nameW = HudChrome.width(t.text(), t.textPx(), row.name()) + (status.isEmpty() ? 0.0F : 8.0F * s + statusW);

            lines.add(

               new HudRenderer.StaffLine(

                  row.name(),

                  face,

                  "WATCHING YOU",

                  false,

                  nameW,

                  serverTagW + 4.0F * s + valueW,

                  true,

                  row.nearby(),

                  row.alertLevel(),

                  status,

                  statusW,

                  value,

                  valueW,

                  serverTagW

               )

            );

         } else {

            lines.add(HudRenderer.StaffLine.plain(row.name(), face, status, false, HudChrome.width(t.text(), t.textPx(), row.name()), statusW));

         }

      }



      if (list.more() > 0) {

         String more = "+" + list.more() + " more";

         lines.add(HudRenderer.StaffLine.plain(more, 0, "", true, HudChrome.width(t.label(), t.labelPx(), more), 0.0F));

      }



      if (state.anchorBottom) {

         Collections.reverse(lines);

      }



      if (lines.isEmpty()) {

         this.removeSlot("staff", t.textPx());

         boolean placeholder = editMode || StaffListModule.shouldShowHud();

         float w = placeholder ? 8.0F * s * 2.0F + HudChrome.width(t.label(), t.labelPx(), "No staff online") : 1.0F;

         return new HudRenderer.StaffPlan(lines, t, "", (float)Math.round(w), (float)Math.round(rowFull));

      } else {

         String count = Integer.toString(list.rows().size() + list.more());

         float headerW = HudChrome.width(t.label(), t.labelPx(), "STAFF") + 5.0F * s + HudChrome.tabularWidth(t.text(), t.textPx(), count);

         float nameColumn = 0.0F;

         float stateColumn = 0.0F;



         for (HudRenderer.StaffLine line : lines) {

            nameColumn = Math.max(nameColumn, line.nameW());

            stateColumn = Math.max(stateColumn, line.stateW());

         }



         float rowW = faceSize(s) + 6.0F * s + nameColumn + (stateColumn > 0.0F ? 14.0F * s + stateColumn : 0.0F);

         float target = 8.0F * s * 2.0F + Math.max(headerW, rowW);

         float w = this.slot("staff", t.textPx(), (float)Math.ceil(target), dt);

         return new HudRenderer.StaffPlan(lines, t, count, (float)Math.round(w), (float)Math.round(20.0F * s + rowFull * lines.size()));

      }

   }



   private void drawStaff(ImDrawList dl, HudLayoutStore.HudElementState state, HudRenderer.StaffPlan plan, boolean shadow, HudRenderer.DrawTransform tr) {

      float a = tr.alpha();

      if (!(a <= 0.001F)) {

         float s = scaleOf(state);

         float x = Math.round(state.x + tr.xOffset());

         float y = Math.round(state.y + tr.yOffset());

         float w = state.width;

         float h = state.height;

         HudRenderer.Type t = plan.type();

         float rowFull = 22.0F * s;

         float pad = 8.0F * s;

         float labelInset = (float)Math.floor((rowFull - t.labelPx()) * 0.5F);

         if (plan.lines().isEmpty()) {

            List<HudRenderer.Step> empty = List.of(new HudRenderer.Step(-1, x, y, x + w, (float)Math.round(y + rowFull), a));

            this.drawRows(dl, empty, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);

            HudChrome.text(dl, t.label(), t.labelPx(), x + pad, y + labelInset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), "No staff online", true);

         } else {

            float headerH = 20.0F * s;

            List<HudRenderer.Step> steps = new ArrayList<>(plan.lines().size() + 1);

            steps.add(new HudRenderer.Step(-1, x, y, x + w, (float)Math.round(y + headerH), a));



            for (int i = 0; i < plan.lines().size(); i++) {

               steps.add(new HudRenderer.Step(i, x, (float)Math.round(y + headerH + rowFull * i), x + w, (float)Math.round(y + headerH + rowFull * (i + 1)), a));

            }



            this.drawRows(dl, steps, hudSurface(state), x, y, w, h, rowsRadius(s), s, shadow);



            for (int i = 1; i < steps.size(); i++) {

               HudRenderer.Step q = steps.get(i);

               HudRenderer.StaffLine line = plan.lines().get(q.item());

               if (line.server()) {

                  int corners = convexCorners(q, steps.get(i - 1), i < steps.size() - 1 ? steps.get(i + 1) : null);

                  float glow = 0.14F;

                  if (line.alert() > 0.0F) {

                     float elapsed = (1.0F - line.alert()) * 4000.0F;

                     float wave = 0.5F + 0.5F * (float)Math.cos(elapsed * 2.0 * Math.PI / 500.0);

                     glow += 0.24F * line.alert() * wave;

                  }



                  int tint = HudChrome.scaleAlpha(HudChrome.WARN, a * glow);

                  if (corners == 256) {

                     dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), tint);

                  } else {

                     dl.addRectFilled(q.l(), q.t(), q.r(), q.b(), tint, rowsRadius(s), corners);

                  }



                  dl.addRectFilled(q.l() + 3.0F * s, q.t() + 4.0F * s, q.l() + 5.0F * s, q.b() - 4.0F * s, HudChrome.scaleAlpha(HudChrome.WARN, a), 1.0F * s);

               }

            }



            HudRenderer.Step head = steps.get(0);

            float countTop = head.t() + (float)Math.floor((head.b() - head.t() - t.textPx()) * 0.5F);

            HudChrome.text(

               dl,

               t.label(),

               t.labelPx(),

               x + pad,

               countTop + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx()),

               HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a),

               "STAFF",

               true

            );

            HudChrome.tabular(

               dl,

               t.text(),

               t.textPx(),

               x + pad + HudChrome.width(t.label(), t.labelPx(), "STAFF") + 5.0F * s,

               countTop,

               HudChrome.scaleAlpha(-1, a),

               plan.count(),

               true

            );

            float face = faceSize(s);

            float textInset = (float)Math.floor((rowFull - t.textPx()) * 0.5F);

            float nameX = pad + face + 6.0F * s;



            for (int ix = 1; ix < steps.size(); ix++) {

               HudRenderer.Step q = steps.get(ix);

               HudRenderer.StaffLine line = plan.lines().get(q.item());

               if (line.more()) {

                  HudChrome.text(

                     dl, t.label(), t.labelPx(), q.l() + nameX, q.t() + labelInset, HudChrome.scaleAlpha(HudChrome.TEXT_LABEL, a), line.name(), true

                  );

               } else {

                  if (line.face() != 0) {

                     drawFace(dl, line.face(), Math.round(q.l() + pad), Math.round(q.t() + (q.b() - q.t() - face) * 0.5F), face, s, a);

                  }



                  float ty = q.t() + textInset;

                  float labelTop = ty + HudChrome.baselineOffset(t.text(), t.textPx(), t.label(), t.labelPx());

                  HudChrome.text(dl, t.text(), t.textPx(), q.l() + nameX, ty, HudChrome.scaleAlpha(-1, a), line.name(), true);

                  if (line.server()) {

                     if (!line.note().isEmpty()) {

                        HudChrome.text(

                           dl,

                           t.label(),

                           t.labelPx(),

                           q.l() + nameX + line.nameW() - line.noteW(),

                           labelTop,

                           HudChrome.scaleAlpha(HudChrome.WARN, a),

                           line.note(),

                           true

                        );

                     }



                     float sx = q.r() - pad - line.stateW();

                     HudChrome.text(dl, t.label(), t.labelPx(), sx, labelTop, HudChrome.scaleAlpha(HudChrome.WARN, a), line.state(), true);

                     HudChrome.tabular(

                        dl,

                        t.text(),

                        t.textPx(),

                        sx + line.serverTagW() + 4.0F * s,

                        ty,

                        HudChrome.scaleAlpha(line.nearby() ? -1 : HudChrome.TEXT_LABEL, a),

                        line.value(),

                        true

                     );

                  } else if (!line.state().isEmpty()) {

                     HudChrome.text(

                        dl, t.label(), t.labelPx(), q.r() - pad - line.stateW(), labelTop, HudChrome.scaleAlpha(HudChrome.WARN, a), line.state(), true

                     );

                  }

               }

            }

         }

      }

   }



   private static float faceSize(float s) {

      int size = Math.round(Math.round(16.0F * s) / 8.0F) * 8;

      if (size > 22.0F * s - 4.0F * s) {

         size -= 8;

      }



      return Math.max(8, size);

   }



   private static void drawFace(ImDrawList dl, int texture, float x, float y, float size, float s, float a) {

      float r = Math.max(1.0F, (float)Math.round(2.0F * s));

      int tint = HudChrome.scaleAlpha(-1, a);

      dl.addImageRounded(texture, x, y, x + size, y + size, 0.125F, 0.125F, 0.25F, 0.25F, tint, r, 240);

      dl.addImageRounded(texture, x, y, x + size, y + size, 0.625F, 0.125F, 0.75F, 0.25F, tint, r, 240);

      dl.addRect(x, y, x + size, y + size, HudChrome.scaleAlpha(402653183, a), r, 0, 1.0F);

   }



   int staffFace(UUID id, boolean hidden) {

      Minecraft mc = Minecraft.getInstance();

      ClientPacketListener conn = mc.getConnection();

      PlayerInfo info = conn == null ? null : conn.getPlayerInfo(id);

      Identifier skin;

      if (info != null) {

         skin = info.getSkin().body().texturePath();

         this.staffSkins.put(id, skin);

      } else {

         skin = this.staffSkins.get(id);

         if (skin == null) {

            skin = DefaultPlayerSkin.get(id).body().texturePath();

         }

      }



      GpuTexture texture;

      try {

         texture = mc.getTextureManager().getTexture(skin).getTexture();

      } catch (IllegalStateException var10) {

         return 0;

      }



      if (texture instanceof GlTexture gl && !gl.isClosed()) {

         if (this.nearestSkins.add(texture)) {

            int bound = GL11.glGetInteger(32873);

            GL11.glBindTexture(3553, gl.glId());

            GL11.glTexParameteri(3553, 10241, 9728);

            GL11.glTexParameteri(3553, 10240, 9728);

            GL11.glBindTexture(3553, bound);

         }



         return gl.glId();

      } else {

         return 0;

      }

   }





 private record DrawTransform(float xOffset,float yOffset,float alpha){}

   private record StaffLine(

      String name,

      int face,

      String state,

      boolean more,

      float nameW,

      float stateW,

      boolean server,

      boolean nearby,

      float alert,

      String note,

      float noteW,

      String value,

      float valueW,

      float serverTagW

   ) {

      static HudRenderer.StaffLine plain(String name, int face, String state, boolean more, float nameW, float stateW) {

         return new HudRenderer.StaffLine(name, face, state, more, nameW, stateW, false, false, 0.0F, "", 0.0F, "", 0.0F, 0.0F);

      }

   }



   @Environment(EnvType.CLIENT)

   private record StaffPlan(List<HudRenderer.StaffLine> lines, HudRenderer.Type type, String count, float width, float height) {

   }



   @Environment(EnvType.CLIENT)

   private record Step(int item, float l, float t, float r, float b, float alpha) {

   }



   @Environment(EnvType.CLIENT)

   private record Type(ImFont value, int valuePx, ImFont text, int textPx, ImFont label, int labelPx) {

      static HudRenderer.Type of(float scale) {

         ImGuiManager m = ImGuiManager.getInstance();

         return new HudRenderer.Type(

            m.hudValueFont(), HudChrome.px(18.0F, scale), m.hudTextFont(), HudChrome.px(17.0F, scale), m.hudLabelFont(), HudChrome.px(14.0F, scale)

         );

      }

   }



   @Environment(EnvType.CLIENT)

   private record VanishLine(String tag, String value, boolean urgent, float tagW, float valueW) {

   }



   @Environment(EnvType.CLIENT)

   private record VanishPlan(List<HudRenderer.VanishLine> lines, HudRenderer.Type type, float tagColumn, float width, float height) {

   }

}
