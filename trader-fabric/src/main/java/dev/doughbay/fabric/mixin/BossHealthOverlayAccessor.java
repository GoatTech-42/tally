package dev.doughbay.fabric.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Read access to the boss bars on screen.
 *
 * <p>A wither announces itself to everyone in range with a bar across the top
 * of the screen, before it is close enough to be seen and long before the
 * first hit. The overlay keeps the bars private. This exposes them read-only;
 * nothing is modified.</p>
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> doughbay$events();
}
