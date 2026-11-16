package net.kenji.icarus_additions.client;

import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.mechanisms.tempest.TempestHelper;
import net.kenji.icarus_additions.mechanisms.updraft.UpdraftHelper;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.kenji.icarus_additions.network.TempestDashC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Tempest dash, local player. A fresh sprint press while flying with Tempest wings does what a Riptide trident does on
 * release (push along the look direction + startAutoSpinAttack(20)), except the velocity is held constant for the
 * whole spin: no gravity and no air drag, so it's a straight full-speed air burst. Wing flight pauses during the spin and is
 * restarted (Caelus-style, like the Updraft launch) when the spin ends.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTempestHandler {
    /** Ticks into the dash before a missing server spin flag counts as "the server ended it" (hit / rejected). */
    private static final int SERVER_FLAG_GRACE = 4;

    private static boolean wasSprintDown;
    private static int dashTicks;
    /** Velocity held constant for the whole dash: no gravity, no air drag. */
    private static Vec3 dashVelocity = Vec3.ZERO;

    private ClientTempestHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.CLIENT) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.player != mc.player) return;
        LocalPlayer player = mc.player;

        // Edge-detect the sprint key: holding it doesn't chain dashes, each dash needs a new press
        boolean sprintDown = mc.options.keySprint.isDown();
        boolean pressed = sprintDown && !wasSprintDown;
        wasSprintDown = sprintDown;

        if (dashTicks > 0) {
            tickDash(player);
        } else if (pressed && mc.screen == null && TempestHelper.canDash(player)) {
            startDash(player);
        }
    }

    private static void startDash(LocalPlayer player) {
        int level = TempestHelper.getLevel(player);
        Vec3 push = TempestHelper.getDashPush(player, level);

        // Riptide adds its push to your current velocity and then lets gravity + air drag eat it. For an air burst,
        // the dash is a straight line at constant speed instead: Riptide's push along the look direction, but never
        // slower than how fast you were already going that way (so a dash never slows a fast dive down).
        Vec3 direction = push.normalize();
        double alreadyGoing = player.getDeltaMovement().dot(direction);
        dashVelocity = direction.scale(Math.max(push.length(), alreadyGoing));

        player.stopFallFlying();
        player.setDeltaMovement(dashVelocity);
        player.startAutoSpinAttack(TempestHelper.SPIN_TICKS);
        TempestHelper.startCooldown(player); // locally too, so a quick double press can't fire twice before the sync
        dashTicks = TempestHelper.SPIN_TICKS;

        IcarusAdditionsNetwork.CHANNEL.sendToServer(new TempestDashC2SPacket(true));
    }

    private static void tickDash(LocalPlayer player) {
        dashTicks--;
        player.resetFallDistance();

        // Hitting a mob: vanilla's spin attack bounces you back (velocity * -0.2). Let that bounce happen.
        boolean bounced = player.getDeltaMovement().dot(dashVelocity) < 0.0D;
        // The server owns the spin flag. If it's gone early, the spin hit something or the server rejected the dash.
        boolean serverEnded = dashTicks < TempestHelper.SPIN_TICKS - SERVER_FLAG_GRACE && !player.isAutoSpinAttack();
        if (bounced || serverEnded || player.horizontalCollision || player.verticalCollision
                || player.onGround() || player.isInWater()) {
            endDash(player); // interrupted: keep whatever velocity the collision/bounce left
            return;
        }
        if (dashTicks <= 0) {
            // Finished the full dash: hand the full dash speed straight to wing flight, no last-tick drag/gravity loss
            player.setDeltaMovement(dashVelocity);
            endDash(player);
            return;
        }

        // Written at the end of the tick, so it's exactly how far the player moves next tick: gravity and air drag
        // are applied after the move and get overwritten here again, so the dash stays straight and full speed.
        player.setDeltaMovement(dashVelocity);
    }

    private static void endDash(LocalPlayer player) {
        dashTicks = 0;
        dashVelocity = Vec3.ZERO;
        if (UpdraftHelper.canStartWingFlight(player)) {
            player.startFallFlying();
            IcarusAdditionsNetwork.CHANNEL.sendToServer(new TempestDashC2SPacket(false));
        }
    }

    /** Hover shouldn't trigger mid-spin. */
    public static boolean isDashing(Player player) {
        return dashTicks > 0 && player == Minecraft.getInstance().player;
    }
}
