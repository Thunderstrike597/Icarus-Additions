package net.kenji.icarus_additions.client;

import dev.cammiescorner.icarus.util.IcarusHelper;
import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.ConfigClient;
import net.kenji.icarus_additions.mechanisms.hover.HoverControlHelper;
import net.kenji.icarus_additions.mechanisms.hover.HoverHelper;
import net.kenji.icarus_additions.mechanisms.hover.HoverMoveDirection;
import net.kenji.icarus_additions.mechanisms.hover.HoverMovement;
import net.kenji.icarus_additions.mechanisms.hover.HoverPhase;
import net.kenji.icarus_additions.mixin.ToggleKeyMappingInvoker;
import net.kenji.icarus_additions.network.HoverStateC2SPacket;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerFlyableFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jline.utils.Log;
import org.lwjgl.glfw.GLFW;

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
    /** Zephyr: how quickly vertical speed eases toward the target (fraction per tick). */
    private static final double ZEPHYR_VERTICAL_EASE = 0.3D;

    // Zephyr crouch presses, recorded from raw key events so quick taps between two ticks (and toggle-crouch's
    // "off" press, which isn't a rising edge of isShiftKeyDown) are never missed. Consumed every local tick.
    private static boolean crouchPressPending;
    private static boolean doubleTapPending;
    private static long lastCrouchPressMs = -1L;

    public static final class HoverState {
        HoverPhase phase = HoverPhase.NONE;
        boolean wasIcarusFlying;
        int phaseTicks;
        float entryPitch;
        float entryTime;
        /** Velocity carried through the braking phase. */
        double carryX, carryY, carryZ;
        boolean blockUntilUncrouch;
        /** Zephyr: this hover is the controllable one (decided on entry). */
        boolean controlled;
        /** Zephyr: crouch was down when the hover started; it has to be let go before crouch means "sink". */
        boolean crouchLatched;
        /** Zephyr: vertical speed we carry ourselves (eased toward the jump/crouch target). */
        double hoverVy;
        /** Zephyr, toggle-crouch mode: sinking switched on/off by crouch presses (our own state, not vanilla's). */
        boolean descendToggled;
        /** Movement over the last full tick, cached at the END of the player tick (after the move happened). */
        HoverMovement movement = HoverMovement.ZERO;
    }

    private static final Map<Player, HoverState> STATES = new WeakHashMap<>();

    private ClientHoverHandler() {}

    // ---------------------------------------------------------------- ticking
    @SubscribeEvent
    public static void onFallFlying(InputEvent.InteractionKeyMappingTriggered event){
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if(player == null)return;
        if(event.getKeyMapping() == minecraft.options.keyJump) {
            HoverState s = STATES.computeIfAbsent(player, p -> new HoverState());

            if (s.phase == HoverPhase.HOVERING || s.phase == HoverPhase.BRAKING) {
                player.setShiftKeyDown(false);
            }
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (mc.options.keyShift.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode()))) {
            onCrouchPressed();
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (mc.options.keyShift.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            onCrouchPressed();
        }
    }

    private static void onCrouchPressed() {
        long now = Util.getMillis();
        crouchPressPending = true;
        if (lastCrouchPressMs >= 0L && now - lastCrouchPressMs <= ConfigClient.HOVER_DOUBLE_TAP_WINDOW_MS.get()) {
            doubleTapPending = true;
            lastCrouchPressMs = -1L; // a third quick press starts a new pair instead of chaining
        } else {
            lastCrouchPressMs = now;
        }
    }

    /** The press that starts a hover must not count as the first half of the exit double-tap. */
    private static void resetDoubleTap() {
        doubleTapPending = false;
        lastCrouchPressMs = -1L;
    }

    /**
     * Zephyr: keep the vanilla crouch state out of it. During a Zephyr hover the crouch key only drives our own
     * "sink" input, so the player never actually sneaks: no sneak pose/slowdown, no sneak sent to the server, and
     * other players see them standing. Fires right after vanilla reads the keys in LocalPlayer.aiStep.
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() == Minecraft.getInstance().player && HoverControlHelper.isActive(event.getEntity())) {
            event.getInput().shiftKeyDown = false;
        }
    }

    /**
     * Whether the crouch key is physically held right now. Not keyShift.isDown(): with toggle-crouch that's the
     * toggled state, and isShiftKeyDown() is forced false during a Zephyr hover (see onMovementInput).
     */
    private static boolean isCrouchKeyHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        InputConstants.Key key = mc.options.keyShift.getKey();
        if (key == InputConstants.UNKNOWN) return false;
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    private static boolean isToggleCrouch() {
        return Minecraft.getInstance().options.toggleCrouch().get();
    }

    /** Zephyr hover available for the local player right now. Needs the mod on the server (it prevents the fly-kick). */
    private static boolean canControlHover(LocalPlayer player) {
        return serverHasMod() && HoverControlHelper.canControl(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.CLIENT) return;
        if (event.player == Minecraft.getInstance().player) {
            tickLocal((LocalPlayer) event.player);
        } else {
            tickRemote(event.player);
        }

        // Cache the movement now, while x - xo is this tick's real move. Read mid-tick (e.g. from Epic Fight's
        // updateMotion, which runs before aiStep moves the player) x - xo would still be 0.
        HoverState s = STATES.get(event.player);
        if (s != null) {
            s.movement = s.phase == HoverPhase.NONE ? HoverMovement.ZERO : HoverMovement.of(event.player);
        }
    }

    private static void tickLocal(LocalPlayer player) {
        HoverState s = STATES.computeIfAbsent(player, p -> new HoverState());
        boolean enabled = ConfigClient.HOVER_ENABLED.get();
        boolean crouching = player.isShiftKeyDown();
        boolean crouchHeld = isCrouchKeyHeld();
        boolean controllable = canControlHover(player);

        boolean crouchPressed = crouchPressPending;
        boolean doubleTapped = doubleTapPending;
        crouchPressPending = false;
        doubleTapPending = false;

        // (1) Re-arm once crouch has been let go (or toggled off)
        if (s.blockUntilUncrouch && !crouching) {
            s.blockUntilUncrouch = false;
        }
        if (s.crouchLatched && !crouchHeld) {
            s.crouchLatched = false;
        }

        if (s.phase == HoverPhase.NONE) {
            // Zephyr: entered with a fresh crouch PRESS (held crouch doesn't count). Normal hover: crouch held.
            boolean wantsHover = controllable ? crouchPressed : crouching && !s.blockUntilUncrouch;
            if (enabled && wantsHover
                    && (player.isFallFlying() || s.wasIcarusFlying) && HoverHelper.canHover(player)
                    && !ClientUpdraftHandler.blocksHover(player)
                    && !ClientTempestHandler.isDashing(player)) {
                s.entryPitch = player.getXRot();
                s.entryTime = player.tickCount;
                Vec3 entryVelocity = player.getDeltaMovement();
                s.carryX = entryVelocity.x;
                s.carryY = entryVelocity.y;
                s.carryZ = entryVelocity.z;
                s.controlled = controllable;
                if (controllable) {
                    // Back to standing: toggle-crouch is switched off, a held crouch is ignored until it's let go
                    s.crouchLatched = true;
                    s.descendToggled = false;
                    releaseToggledCrouch();
                    resetDoubleTap();
                }
                if (player.isFallFlying()) player.stopFallFlying();
                setLocalPhase(player, s, HoverPhase.BRAKING);
            }
        } else if (s.controlled && player.isFallFlying()) {
            // Zephyr: jump is "rise", never "take off". startFallFlying is blocked during the hover, but if flight
            // still got switched on (e.g. the server's copy of the flag), switch it straight back off and keep hovering.
            player.stopFallFlying();
            tickHoverBody(player, s, crouchPressed, crouchHeld);
        } else if (player.isFallFlying()) {
            // (3) Jumped out of the hover into flight: end it, and don't let a still-held/toggled crouch re-trigger it
            // (Zephyr hovers block flight starts, so this is the normal hover only, or something external)
            setLocalPhase(player, s, HoverPhase.NONE);
            s.blockUntilUncrouch = true;
            releaseToggledCrouch();
        } else if (!enabled || !HoverHelper.canHover(player)
                || (s.controlled ? doubleTapped || !controllable : !crouching)) {
            // Zephyr: double-tap crouch or land (canHover) to leave; also if the enchantment went away mid-hover
            boolean leftByDoubleTap = s.controlled && doubleTapped;
            setLocalPhase(player, s, HoverPhase.NONE);
            if (leftByDoubleTap) {
                releaseToggledCrouch(); // the 2nd tap may have toggled crouch on; don't drop out of the sky crouching
            }
        } else {
            tickHoverBody(player, s, crouchPressed, crouchHeld);
        }

        s.wasIcarusFlying = player.isFallFlying() && IcarusHelper.hasWings(player);
    }

    /** One tick of an ongoing hover (braking or hovering). */
    private static void tickHoverBody(LocalPlayer player, HoverState s, boolean crouchPressed, boolean crouchHeld) {
        s.phaseTicks++;
        HoverHelper.forceUpright(player);
        player.resetFallDistance();

        if (s.controlled && isToggleCrouch()) {
            // Our own toggle replaces vanilla's: flip it per press, and keep vanilla's toggle off so the player
            // isn't left crouching when the hover ends
            if (crouchPressed) s.descendToggled = !s.descendToggled;
            releaseToggledCrouch();
        }

        Vec3 v = player.getDeltaMovement();
        if (s.phase == HoverPhase.BRAKING) {
            double speed = brake(player, s, v, ConfigClient.HOVER_SLOW_DOWN_INTENSITY.get());
            if (speed < STOPPED_SPEED || s.phaseTicks >= MAX_BRAKE_TICKS) {
                s.hoverVy = s.carryY;
                setLocalPhase(player, s, HoverPhase.HOVERING);
            }

        } else if (s.controlled) {
            tickControlledHover(player, s, crouchHeld, v);
        } else {
            player.setDeltaMovement(v.x * HOVER_DRIFT_DAMP, v.y, v.z * HOVER_DRIFT_DAMP);
            // Applied locally too, so the physics kick in this tick instead of after a server round trip
            // (and so it still works on servers without the mod)
            HoverHelper.applySlowFall(player);
        }
    }

    /**
     * Zephyr hover tick. Horizontal: nothing here at all. Vanilla air movement handles WASD (moveRelative with the
     * player's yRot, 0.91 drag), with the speed set through Player#getFlyingSpeed in PlayerHoverMixin.
     * Vertical: written at the end of the tick, so it's exactly how far the player moves next tick (gravity and
     * drag are applied after the move and overwritten here again).
     */
    private static void tickControlledHover(LocalPlayer player, HoverState s, boolean crouchHeld, Vec3 v) {
        double speed = HoverControlHelper.getVerticalSpeed(HoverControlHelper.getLevel(player));
        boolean up = player.input.jumping;
        // Follows the vanilla crouch setting: toggle-crouch = press to start/stop sinking, hold = sink while held
        boolean down = isToggleCrouch() ? s.descendToggled : crouchHeld && !s.crouchLatched;
        double target = up == down ? 0.0D : (up ? speed : -speed);

        // Bumped a ceiling: don't keep pushing into it
        if (player.verticalCollision && s.hoverVy > 0.0D) s.hoverVy = 0.0D;

        s.hoverVy += (target - s.hoverVy) * ZEPHYR_VERTICAL_EASE;
        if (target == 0.0D && Math.abs(s.hoverVy) < 1.0E-3D) s.hoverVy = 0.0D;

        player.setDeltaMovement(v.x, s.hoverVy, v.z);
    }

    private static void releaseToggledCrouch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.toggleCrouch().get() && mc.options.keyShift instanceof ToggleKeyMapping toggle && toggle.isDown()) {
            ((ToggleKeyMappingInvoker) toggle).icarusAdditions$reset();        }
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
        HoverControlHelper.setActive(player, s.controlled ? phase : HoverPhase.NONE);
        if (phase == HoverPhase.NONE) {
            s.controlled = false;
            s.crouchLatched = false;
            s.descendToggled = false;
            HoverHelper.removeSlowFall(player);
        } else {
            HoverHelper.forceUpright(player);
            if (phase == HoverPhase.HOVERING && !s.controlled) HoverHelper.applySlowFall(player);
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
        if (s.phase == HoverPhase.NONE && phase != net.kenji.icarus_additions.mechanisms.hover.HoverPhase.NONE) {
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
        if (!(entity instanceof Player player)) return net.kenji.icarus_additions.mechanisms.hover.HoverPhase.NONE;
        HoverState s = STATES.get(player);
        return s == null ? HoverPhase.NONE : s.phase;
    }

    public static boolean isHovering(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        HoverState s = STATES.get(player);
        return s != null && s.phase == HoverPhase.HOVERING;
    }

    /** True while the player is in a Zephyr (controllable) hover. Local player only (others: use isHovering). */
    public static boolean isControlledHover(Entity entity) {
        return entity instanceof Player player && HoverControlHelper.isActive(player);
    }

    /**
     * How a braking/hovering player moved over the last tick, relative to the way they face (see HoverMovement).
     * Cached at the end of each player tick, so it's valid whenever you call it (render, Epic Fight motion
     * updates, ...). Works for the local player and other players. ZERO when not hovering.
     */
    public static HoverMovement getHoverMovement(Entity entity) {
        if (!(entity instanceof Player player)) return HoverMovement.ZERO;
        HoverState s = STATES.get(player);
        return s == null || s.phase == HoverPhase.NONE ? HoverMovement.ZERO : s.movement;
    }

    /**
     * Main direction a hovering player is moving: FORWARD / BACKWARD / LEFT / RIGHT / UP / DOWN, NONE when still or
     * not hovering. Facing-relative, so with a decoupled camera "running at the camera" reports FORWARD.
     * Use getHoverMovement(entity).horizontalDirection() / verticalDirection() if you need both at once.
     */
    public static HoverMoveDirection getHoverMoveDirection(Entity entity) {
        return getHoverMovement(entity).dominant();
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
