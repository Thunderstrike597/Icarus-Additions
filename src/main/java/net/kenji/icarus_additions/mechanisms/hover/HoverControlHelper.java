package net.kenji.icarus_additions.mechanisms.hover;

import dev.cammiescorner.icarus.item.WingItem;
import dev.cammiescorner.icarus.util.IcarusHelper;
import net.kenji.icarus_additions.ConfigServer;
import net.kenji.icarus_additions.enchantment.IcarusAdditionsEnchantments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Side-agnostic helpers for the Zephyr (controllable) hover.
 *
 * Horizontal movement is NOT done by writing velocity: while a Zephyr hover is active, Player#getFlyingSpeed (the
 * acceleration vanilla's moveRelative uses for airborne input) is swapped for our own value (see PlayerHoverMixin).
 * Everything else is plain vanilla air movement: input impulses -> moveRelative(yRot) -> move -> 0.91 air drag. So
 * anything that hooks vanilla movement (camera-decouple mods remapping input/yRot, etc.) keeps working.
 * Only the vertical axis is written directly (there is no vanilla "air up/down" input to hook).
 */
public final class HoverControlHelper {
    /** Vanilla airborne horizontal drag (LivingEntity.travel, f3 = 0.91 off the ground). */
    private static final double AIR_DRAG = 0.91D;
    /** LivingEntity.aiStep scales xxa/zza by 0.98 before travel. */
    private static final double INPUT_SCALE = 0.98D;

    /**
     * Players currently in a Zephyr hover (BRAKING or HOVERING). Client side holds only the local player, server side
     * holds ServerPlayers; synchronized because singleplayer runs both on different threads.
     */
    private static final Map<Player, HoverPhase> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    private HoverControlHelper() {}

    /** Zephyr level on the equipped, usable Icarus wings, 0 if none. */
    public static int getLevel(Player player) {
        if (!IcarusHelper.hasWings(player)) return 0;
        ItemStack wings = IcarusHelper.getEquippedWings(player);
        if (wings == null || wings.isEmpty() || !(wings.getItem() instanceof WingItem wingItem) || !wingItem.isUsable(player, wings)) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(IcarusAdditionsEnchantments.ZEPHYR.get(), wings);
    }

    /** Server config switch. Safe to call before the server config is loaded (e.g. on a server without the mod). */
    public static boolean isEnabled() {
        return ConfigServer.SPEC.isLoaded() && ConfigServer.ZEPHYR_ENABLED.get();
    }

    /** Whether this player's hover should be the controllable Zephyr one. */
    public static boolean canControl(Player player) {
        return isEnabled() && getLevel(player) > 0;
    }

    // ---------------------------------------------------------------- active state

    public static void setActive(Player player, HoverPhase phase) {
        if (phase == HoverPhase.NONE) {
            ACTIVE.remove(player);
        } else {
            ACTIVE.put(player, phase);
        }
    }

    /** In a Zephyr hover (braking or hovering). Wing flight can't start meanwhile, so jump can be used to rise. */
    public static boolean isActive(Player player) {
        return ACTIVE.containsKey(player);
    }

    /** Hovering with free movement (past the braking phase). Horizontal speed is overridden only then. */
    public static boolean isControlling(Player player) {
        return ACTIVE.get(player) == HoverPhase.HOVERING;
    }

    // ---------------------------------------------------------------- speeds

    private static double levelMultiplier(int level) {
        return Math.pow(ConfigServer.ZEPHYR_SPEED_LEVEL_MULTIPLIER.get(), Math.max(0, level - 1));
    }

    /** Top horizontal speed in blocks/tick. */
    public static double getMoveSpeed(int level) {
        return ConfigServer.ZEPHYR_BASE_MOVE_SPEED.get() / 20.0D * levelMultiplier(level);
    }

    /** Rise/sink speed in blocks/tick. */
    public static double getVerticalSpeed(int level) {
        return ConfigServer.ZEPHYR_BASE_VERTICAL_SPEED.get() / 20.0D * levelMultiplier(level);
    }

    /**
     * The per-tick acceleration to hand vanilla's moveRelative so the player tops out at getMoveSpeed.
     * Vanilla air steady state: v = (v + a) * 0.91  =>  v = a * 0.91 / 0.09.
     */
    public static float getAirAcceleration(int level) {
        return (float) (getMoveSpeed(level) * (1.0D - AIR_DRAG) / AIR_DRAG / INPUT_SCALE);
    }
}
