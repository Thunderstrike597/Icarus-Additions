package net.kenji.icarus_additions.client;

import dev.cammiescorner.icarus.util.IcarusHelper;
import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.WingFlexConfig;
import net.kenji.icarus_additions.hover.HoverHelper;
import net.kenji.icarus_additions.hover.HoverPhase;
import net.kenji.icarus_additions.network.HoverStateC2SPacket;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Crouch-to-hover while flying with Icarus wings.
 *
 * Local player: crouching while Icarus-flying (or on the tick right after Icarus itself ended the flight because of
 * the crouch, when its canSlowFall option is on) stops the glide and enters BRAKING: upright, wings spread, speed
 * bled off gradually (hover.slowDownIntensity). Once horizontal speed is ~0 it switches to HOVERING, which applies hidden Slow Falling.
 * Releasing crouch, landing, water, or taking off again (jump) ends it.
 *
 * Crouching without having been Icarus-flying does nothing: entry needs isFallFlying() or wasIcarusFlying.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientHoverHandler {

    /*
     * Braking carries the flight velocity ourselves instead of letting vanilla air physics eat it: every tick the
     * carried velocity is decelerated and written back, which replaces vanilla's 0.91 air drag (and ignores movement
     * input) so the slowdown is smooth and controlled purely by hover.slowDownIntensity.
     * Per tick at intensity 1: speed * exp(-0.05) - 0.005. ~2s from fast flight; intensity 2 halves that.
     */
    private static final double BRAKE_EXP_PER_INTENSITY = 0.05D;
    private static final double BRAKE_LINEAR_PER_INTENSITY = 0.005D;
    /** Vertical speed eases toward a gentle sink while braking (also scaled by intensity). */
    private static final double BRAKE_SINK_SPEED = -0.04D;
    private static final double BRAKE_VERTICAL_EASE_PER_INTENSITY = 0.15D;
    /** Blocks/tick of horizontal speed that counts as "stopped". */
    private static final double STOPPED_SPEED = 0.06D;
    /** Safety cap on braking time (10 s), whatever the intensity. */
    private static final int MAX_BRAKE_TICKS = 200;
    /** Light horizontal damping while hovering, so the player settles instead of drifting. */
    private static final double HOVER_DRIFT_DAMP = 0.85D;
    /** Ticks to rotate from the horizontal flying pose to upright. */
    private static final float UPRIGHT_TICKS = 8.0F;

    public static final class HoverState {
        HoverPhase phase = HoverPhase.NONE;
        boolean wasIcarusFlying;
        int phaseTicks;
        float entryPitch;
        float entryTime;
        /** Velocity carried through the braking phase. */
        double carryX, carryY, carryZ;
    }

    private static final Map<Player, HoverState> STATES = new WeakHashMap<>();

    private ClientHoverHandler() {}

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.CLIENT) return;
        if (event.player == Minecraft.getInstance().player) {
            tickLocal((LocalPlayer) event.player);
        } else {
            tickRemote(event.player);
        }
    }

    private static void tickLocal(LocalPlayer player) {
        HoverState s = STATES.computeIfAbsent(player, p -> new HoverState());
        boolean enabled = WingFlexConfig.HOVER_ENABLED.get();
        boolean crouching = player.isShiftKeyDown();

        if (s.phase == HoverPhase.NONE) {
            if (enabled && crouching && (player.isFallFlying() || s.wasIcarusFlying) && HoverHelper.canHover(player)) {
                s.entryPitch = player.getXRot();
                s.entryTime = player.tickCount;
                Vec3 entryVelocity = player.getDeltaMovement();
                s.carryX = entryVelocity.x;
                s.carryY = entryVelocity.y;
                s.carryZ = entryVelocity.z;
                if (player.isFallFlying()) player.stopFallFlying();
                setLocalPhase(player, s, HoverPhase.BRAKING);
            }
        } else if (!enabled || !crouching || player.isFallFlying() || !HoverHelper.canHover(player)) {
            setLocalPhase(player, s, HoverPhase.NONE);
        } else {
            s.phaseTicks++;
            HoverHelper.forceUpright(player);
            player.resetFallDistance();

            Vec3 v = player.getDeltaMovement();
            if (s.phase == HoverPhase.BRAKING) {
                double speed = brake(player, s, v, WingFlexConfig.HOVER_SLOW_DOWN_INTENSITY.get());
                if (speed < STOPPED_SPEED || s.phaseTicks >= MAX_BRAKE_TICKS) {
                    setLocalPhase(player, s, HoverPhase.HOVERING);
                }
            } else {
                player.setDeltaMovement(v.x * HOVER_DRIFT_DAMP, v.y, v.z * HOVER_DRIFT_DAMP);
                // Applied locally too, so the physics kick in this tick instead of after a server round trip
                // (and so it still works on servers without the mod)
                HoverHelper.applySlowFall(player);
            }
        }

        s.wasIcarusFlying = player.isFallFlying() && IcarusHelper.hasWings(player);
    }

    /** Decelerates the carried velocity one tick and applies it. Returns the new horizontal speed. */
    private static double brake(LocalPlayer player, HoverState s, Vec3 actual, double intensity) {
        // Walls / ceilings: Entity.move zeroes the blocked axis, so stop carrying speed into it
        if (player.horizontalCollision) {
            if (Math.abs(actual.x) < 1.0E-3D) s.carryX = 0.0D;
            if (Math.abs(actual.z) < 1.0E-3D) s.carryZ = 0.0D;
        }
        if (player.verticalCollision && s.carryY > 0.0D) s.carryY = 0.0D;

        double speed = Math.sqrt(s.carryX * s.carryX + s.carryZ * s.carryZ);
        double newSpeed = Math.max(0.0D,
                speed * Math.exp(-BRAKE_EXP_PER_INTENSITY * intensity) - BRAKE_LINEAR_PER_INTENSITY * intensity);
        double scale = speed > 1.0E-6D ? newSpeed / speed : 0.0D;
        s.carryX *= scale;
        s.carryZ *= scale;
        s.carryY += (BRAKE_SINK_SPEED - s.carryY) * (1.0D - Math.exp(-BRAKE_VERTICAL_EASE_PER_INTENSITY * intensity));

        player.setDeltaMovement(s.carryX, s.carryY, s.carryZ);
        return newSpeed;
    }

    private static void setLocalPhase(LocalPlayer player, HoverState s, HoverPhase phase) {
        if (s.phase == phase) return;
        s.phase = phase;
        s.phaseTicks = 0;
        if (phase == HoverPhase.NONE) {
            HoverHelper.removeSlowFall(player);
        } else {
            HoverHelper.forceUpright(player);
            if (phase == HoverPhase.HOVERING) HoverHelper.applySlowFall(player);
        }
        if (serverHasMod()) {
            IcarusAdditionsNetwork.CHANNEL.sendToServer(new HoverStateC2SPacket(phase));
        }
    }

    private static void tickRemote(Player player) {
        HoverState s = STATES.get(player);
        if (s == null || s.phase == HoverPhase.NONE) return;
        if (player.onGround() || player.isFallFlying() || player.isInWater()) {
            s.phase = HoverPhase.NONE; // server will confirm; don't keep a stale pose meanwhile
            return;
        }
        // Remote players re-run updatePlayerPose locally, which would put them back into the crouch pose
        HoverHelper.forceUpright(player);
    }

    /** Server told us another player's hover phase changed. */
    public static void onRemoteSync(int entityId, HoverPhase phase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player) || player == mc.player) return;

        HoverState s = STATES.computeIfAbsent(player, p -> new HoverState());
        if (s.phase == HoverPhase.NONE && phase != HoverPhase.NONE) {
            s.entryPitch = player.getXRot();
            s.entryTime = player.tickCount;
        }
        s.phase = phase;
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && IcarusAdditionsNetwork.CHANNEL.isRemotePresent(connection.getConnection());
    }

    // ---------------------------------------------------------------- render accessors

    public static HoverPhase getPhase(Entity entity) {
        if (!(entity instanceof Player player)) return HoverPhase.NONE;
        HoverState s = STATES.get(player);
        return s == null ? HoverPhase.NONE : s.phase;
    }

    public static boolean isHovering(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        HoverState s = STATES.get(player);
        return s != null && s.phase == HoverPhase.HOVERING;
    }

    public static boolean isBreaking(Entity entity){
        if (!(entity instanceof Player player)) return false;
        HoverState s = STATES.get(player);
        return s != null && s.phase == HoverPhase.BRAKING;
    }

    /**
     * Extra body pitch (degrees, same convention as vanilla's elytra lean) that eases the player from the horizontal
     * flying pose up to standing over the first few ticks of the hover. 0 once upright or when not hovering.
     */
    public static float getUprightLean(Entity entity, float partialTick) {
        if (!(entity instanceof Player player)) return 0.0F;
        HoverState s = STATES.get(player);
        if (s == null || s.phase == HoverPhase.NONE) return 0.0F;

        float progress = (player.tickCount + partialTick - s.entryTime) / UPRIGHT_TICKS;
        if (progress >= 1.0F) return 0.0F;
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress); // smoothstep
        return (1.0F - eased) * (-90.0F - s.entryPitch);
    }
}
