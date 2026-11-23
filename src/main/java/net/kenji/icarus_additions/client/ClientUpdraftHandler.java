package net.kenji.icarus_additions.client;

import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.compat.epicfight.client.EpicFightUpdraftAnimations;
import net.kenji.icarus_additions.mechanisms.hover.HoverHelper;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.kenji.icarus_additions.network.UpdraftFlightC2SPacket;
import net.kenji.icarus_additions.network.UpdraftLaunchC2SPacket;
import net.kenji.icarus_additions.ConfigServer;
import net.kenji.icarus_additions.mechanisms.updraft.UpdraftHelper;
import net.kenji.icarus_additions.mechanisms.updraft.UpdraftPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client half of Updraft. CHARGED / NONE come from the server (it owns the crouch timer). The local player's jump
 * while charged starts the launch (movement is client-authoritative) and tells the server. The wings wind up first
 * (vanilla jump held back), then on the downstroke the client lifts the player to the target height, braking smoothly
 * as it gets close, and starts Icarus flight exactly like vanilla does when you jump mid-air.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientUpdraftHandler {

    /** Launch speed (blocks/tick) at liftoff, scaled by the speed config. The lift only slows down from here. */
    private static final double LAUNCH_SPEED = 1.4D;
    /**
     * Shape of the slow-down over the whole climb. Speed = exit + (launch - exit) * (1 - progress)^EASE_EXPONENT,
     * where progress goes 0 -> 1 from the ground to the lift height.
     * 0.5 = constant deceleration (exactly like a vanilla-gravity jump, but it reaches the top still fairly fast).
     * Higher = slows down harder early on and hangs longer near the top (floatier apex). 1.0 = very floaty.
     */
    private static final double EASE_EXPONENT = 0.75D;
    /** Upward speed left at the top, handed to flight so it carries on smoothly into the glide. */
    private static final double EXIT_SPEED = 0.15D;
    /** Close enough to the target to count as the top. */
    private static final double TOP_EPSILON = 0.2D;
    /** Safety cap only (a slow launch speed with a big lift height can legitimately take a while). */
    private static final int MAX_ASCENT_TICKS = 400;
    /** Ticks for the single heavy flap. */
    public static final float BEAT_TICKS = 10.0F;

    private static final class State {
        UpdraftPhase phase = UpdraftPhase.NONE;
        float launchTime;
        /** Ticks left before the lift starts (wings winding up for the downstroke). */
        int windupTicks;
        boolean lifting;
        double liftHeight;
        double targetY;
        int ascentTicks;
        /** The jump that started the launch is still held: keep it from reaching vanilla until it's let go. */
        boolean suppressJump;
        /** After a launch, crouch has to be let go once before hover can trigger (so a held crouch doesn't hover instantly). */
        boolean blockHoverUntilUncrouch;
    }

    private static final Map<Player, State> STATES = new WeakHashMap<>();

    private ClientUpdraftHandler() {}

    // ---------------------------------------------------------------- sync from server

    public static void onSync(int entityId, UpdraftPhase phase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player)) return;
        State s = STATES.computeIfAbsent(player, p -> new State());

        if (player == mc.player) {
            // We start LAUNCHING ourselves; the server echoing it back changes nothing. A NONE while we're still
            // rising is the server saying the launch is over (flight started / rejected).
            if (phase == UpdraftPhase.LAUNCHING) return;
            if (phase == UpdraftPhase.NONE && s.phase == UpdraftPhase.LAUNCHING && s.ascentTicks < MAX_ASCENT_TICKS
                    && !player.isFallFlying()) {
                return; // let our own ascent finish and hand over to flight
            }
        } else if (phase == UpdraftPhase.LAUNCHING && s.phase != UpdraftPhase.LAUNCHING) {
            s.launchTime = player.tickCount; // remote player: start their wing beat now
            if (IcarusAdditions.EFM_LOADED) {
                EpicFightUpdraftAnimations.playLaunch(player); // and their Epic Fight launch animation
            }
        }
        if (phase == UpdraftPhase.CHARGED && s.phase != UpdraftPhase.CHARGED) {
            mc.getSoundManager().play(new UpdraftChargeSoundInstance(player));
        }
        s.phase = phase;
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.CLIENT) return;
        Player player = event.player;
        State s = STATES.get(player);
        if (s == null) return;

        if (s.phase == UpdraftPhase.CHARGED) {
            spawnDraftParticles(player); // every client draws its own, for every charged player it can see
        }

        if (player == Minecraft.getInstance().player) {
            tickLocal((LocalPlayer) player, s);
        } else if (s.phase == UpdraftPhase.LAUNCHING) {
            HoverHelper.forceUpright(player);
        }
    }

    private static void spawnDraftParticles(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (!ConfigServer.PARTICLES.get() || mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        if (player.tickCount % 2 != 0) return; // rate: every 2nd tick

        RandomSource random = player.getRandom();
        float angle = random.nextFloat() * Mth.TWO_PI;
        double radius = 1.0D + Math.sqrt(random.nextDouble()) * 1.5D;   // 1.0 – 2.5 blocks out
        double x = player.getX() + Mth.cos(angle) * radius;
        double z = player.getZ() + Mth.sin(angle) * radius;
        double y = player.getY() + random.nextDouble() * 0.4D;

        Particle particle = mc.particleEngine.createParticle(ParticleTypes.POOF, x, y, z,
                Mth.cos(angle) * 0.005D, 0.06D + random.nextDouble() * 0.175D, Mth.sin(angle) * 0.005D); // slight outward drift
        if (particle != null) {
            particle.scale(0.5F); // half size
        }
    }

    /**
     * Lift speed for this tick (blocks/tick) with {@code remaining} of {@code height} blocks still to go. Starts at the
     * launch speed and eases down over the whole climb (see {@link #EASE_EXPONENT}), arriving at the lift height with
     * just {@link #EXIT_SPEED} left. Never overshoots the height by more than that exit speed.
     */
    public static double ascentSpeed(double remaining, double height, double speedMult) {
        remaining = Math.max(0.0D, remaining);
        double launch = Math.max(LAUNCH_SPEED * speedMult, EXIT_SPEED);
        double left = height > 0.0D ? Mth.clamp(remaining / height, 0.0D, 1.0D) : 0.0D; // 1 at liftoff, 0 at the top
        double vy = EXIT_SPEED + (launch - EXIT_SPEED) * Math.pow(left, EASE_EXPONENT);
        return Math.min(vy, remaining + EXIT_SPEED);
    }

    public static int estimateAscentTicks(double height, double speedMult) {
        double y = 0.0D;
        int ticks = 0;
        while (height - y > TOP_EPSILON && ticks < MAX_ASCENT_TICKS) {
            y += ascentSpeed(height - y, height, speedMult);
            ticks++;
        }
        return ticks;
    }

    /** Whole launch length in ticks: wind-up delay + ascent. */
    public static int estimateLaunchTicks(double height, double speedMult) {
        return ConfigServer.LAUNCH_DELAY_TICKS.get() + estimateAscentTicks(height, speedMult);
    }

    // ---------------------------------------------------------------- input

    /**
     * Fires right after vanilla reads the keys in LocalPlayer.aiStep, before the jump is used. A jump while charged
     * starts the launch here, and the vanilla jump is held back so the player stays planted while the wings wind up.
     * It also stays blocked while the launch's jump is still held: a ground jump on the lift tick would overwrite the
     * lift velocity, and letting a held jump through once airborne would look like a fresh press and start flight.
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        State s = STATES.get(player);
        if (s == null) return;
        Input input = event.getInput();

        if (s.phase == UpdraftPhase.CHARGED && input.jumping) {
            startLaunch(player, s);
        }
        if (s.phase != UpdraftPhase.LAUNCHING) {
            s.suppressJump = false;
            return;
        }
        if (!input.jumping) s.suppressJump = false; // let go: the next press is a real one (e.g. double-tap to fly)
        if (s.suppressJump || s.windupTicks > 0 || player.onGround()) {
            input.jumping = false;
        }
    }

    private static void startLaunch(LocalPlayer player, State s) {
        int level = UpdraftHelper.getLevel(player);
        if (level <= 0) return;
        if (IcarusAdditions.EFM_LOADED) {
            EpicFightUpdraftAnimations.playLaunch(player);
        }
        s.phase = UpdraftPhase.LAUNCHING;
        s.launchTime = player.tickCount; // the heavy wing beat starts now
        s.windupTicks = ConfigServer.LAUNCH_DELAY_TICKS.get();
        s.lifting = false;
        s.liftHeight = UpdraftHelper.getLiftHeight(level);
        s.ascentTicks = 0;
        s.blockHoverUntilUncrouch = true;
        s.suppressJump = true;
        IcarusAdditionsNetwork.CHANNEL.sendToServer(new UpdraftLaunchC2SPacket());
    }

    // ---------------------------------------------------------------- local ticking

    private static void tickLocal(LocalPlayer player, State s) {
        if (s.blockHoverUntilUncrouch && !player.isShiftKeyDown()) {
            s.blockHoverUntilUncrouch = false;
        }

        if (s.phase != UpdraftPhase.LAUNCHING) return;

        HoverHelper.forceUpright(player);
        player.resetFallDistance();

        if (player.isFallFlying()) { // double-tapped jump mid-ascent: flight already started
            s.phase = UpdraftPhase.NONE;
            return;
        }

        // Wind-up: wings are raising / starting the downstroke, the player stays put
        if (s.windupTicks > 0) {
            if (player.isInWater()) {
                s.phase = UpdraftPhase.NONE;
                return;
            }
            s.windupTicks--;
            if (s.windupTicks > 0) return;
        }
        if (!s.lifting) { // downstroke: leave the ground now
            s.lifting = true;
            s.targetY = player.getY() + s.liftHeight;
            s.ascentTicks = 0;
        }

        s.ascentTicks++;
        double remaining = s.targetY - player.getY();
        boolean hitCeiling = s.ascentTicks > 2 && player.verticalCollision && player.getDeltaMovement().y <= 0.0D;
        if (remaining <= TOP_EPSILON || hitCeiling || player.isInWater() || s.ascentTicks >= MAX_ASCENT_TICKS) {
            finishLaunch(player, s, hitCeiling);
            return;
        }

        // Written at the end of the tick, so it's exactly how far the player moves next tick (gravity/drag come after)
        double vy = ascentSpeed(remaining, s.liftHeight, ConfigServer.ASCENT_SPEED_MULTIPLIER.get());
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.6D, vy, v.z * 0.6D);
    }

    /** Top reached: hand over to regular Icarus flight (same as jumping mid-air with wings). */
    private static void finishLaunch(LocalPlayer player, State s, boolean hitCeiling) {
        s.phase = UpdraftPhase.NONE;
        s.lifting = false;
        if (!hitCeiling) {
            // Carry the brake's exit speed into flight (gravity already ate some of it this tick), so the glide picks
            // up from a gentle rise instead of a dead stop
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, Math.max(v.y, EXIT_SPEED), v.z);
        }
        // Not tryToStartFallFlying() + START_FALL_FLYING: those check the chest slot for an elytra, so they always
        // fail for Icarus wings. Do what Caelus does for its own jump-to-fly instead: start locally, tell the server.
        if (UpdraftHelper.canStartWingFlight(player)) {
            player.startFallFlying();
            IcarusAdditionsNetwork.CHANNEL.sendToServer(new UpdraftFlightC2SPacket());
        }
    }

    // ---------------------------------------------------------------- accessors

    public static UpdraftPhase getPhase(Entity entity) {
        if (!(entity instanceof Player player)) return UpdraftPhase.NONE;
        State s = STATES.get(player);
        return s == null ? UpdraftPhase.NONE : s.phase;
    }

    /** Ticks since the launch started (for the heavy wing beat), or -1 if not launching. */
    public static float getLaunchAge(Entity entity, float ageInTicks) {
        if (!(entity instanceof Player player)) return -1.0F;
        State s = STATES.get(player);
        if (s == null || s.phase != UpdraftPhase.LAUNCHING) return -1.0F;
        return ageInTicks - s.launchTime;
    }

    /** Hover shouldn't trigger while being lifted, or until crouch has been released once after a launch. */
    public static boolean blocksHover(Player player) {
        State s = STATES.get(player);
        return s != null && (s.phase == UpdraftPhase.LAUNCHING || s.blockHoverUntilUncrouch);
    }
}
