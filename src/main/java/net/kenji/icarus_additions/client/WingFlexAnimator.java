package net.kenji.icarus_additions.client;

import dev.cammiescorner.icarus.client.models.DiscordsWingsModel;
import dev.cammiescorner.icarus.client.models.FeatheredWingsModel;
import dev.cammiescorner.icarus.client.models.FlandresWingsModel;
import dev.cammiescorner.icarus.client.models.LeatherWingsModel;
import dev.cammiescorner.icarus.client.models.WingEntityModel;
import dev.cammiescorner.icarus.client.models.ZanzasWingsModel;
import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.WingFlexConfig;
import net.kenji.icarus_additions.hover.HoverPhase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

/**
 * Splits each Icarus wing into an inner and outer half by rotating the "elbow" bone.
 *
 * Icarus only animates the root bone of each wing (leftWing / rightWing). The flap is an oscillation of that
 * root's xRot, i.e. a rotation about the root's local X axis. Every model is already a bone chain
 * (root -> 01 -> 02 -> 03 ...), so we take the oscillating part of the root flap, scale it, and apply it as an
 * extra rotation about the SAME axis, but pivoting at the elbow. The outer half then rotates further than the
 * inner half, which reads as the wing bending.
 */
public final class WingFlexAnimator {

    /** Bones between the wing root and the elbow (exclusive), then the elbow itself. */
    private record Joint(ModelPart root, ModelPart[] chain, ModelPart elbow, float baseX, float baseY, float baseZ) {}

    /** Per-model joints. Null entry = model has no usable elbow (e.g. Light wings, which are separate blades). */
    private static final Map<WingEntityModel<?>, Joint[]> JOINTS = new WeakHashMap<>();
    /** Per-entity filter state, so each player's wings have their own follow-through. */
    private static final Map<LivingEntity, FlexState> STATES = new WeakHashMap<>();

    /** Ticks for the slow average of the root rotation. Anything slower than this is "pose", faster is "flap". */
    private static final float CENTER_TIME = 20.0F;

    private static final Vector3f ROOT_FLAP_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);

    // Scratch objects, render thread only
    private static final Quaternionf CHAIN = new Quaternionf();
    private static final Quaternionf TMP = new Quaternionf();
    private static final Quaternionf BEND = new Quaternionf();
    private static final Vector3f AXIS = new Vector3f();
    private static final Vector3f C0 = new Vector3f();
    private static final Vector3f C1 = new Vector3f();
    private static final Vector3f C2 = new Vector3f();

    private static boolean loggedHook = false;

    private WingFlexAnimator() {}

    /** Called right before Icarus' setupAnim. Puts the elbow back to its unbent pose so our bend never accumulates. */
    public static void beforeSetup(WingEntityModel<?> model) {
        if (!loggedHook) {
            loggedHook = true;
            IcarusAdditions.LOGGER.info("[Icarus Additions] Hooked into Icarus wing rendering.");
        }
        Joint[] joints = JOINTS.computeIfAbsent(model, WingFlexAnimator::resolveJoints);
        if (joints == null) return;
        for (Joint j : joints) {
            j.elbow.xRot = j.baseX;
            j.elbow.yRot = j.baseY;
            j.elbow.zRot = j.baseZ;
        }
    }

    /** Called right after Icarus' setupAnim, which has already positioned the root bones for this frame. */
    public static void afterSetup(WingEntityModel<?> model, LivingEntity entity, float ageInTicks) {
        FlexState state = STATES.computeIfAbsent(entity, e -> new FlexState());

        // Hover: spread the wings like in flight (Icarus thinks we're just standing/crouching) with a hovering flap.
        // Blended in/out so the wings open and fold smoothly.
        // Braking: held open like a glide, no flapping. Hovering: the flap fades in.
        HoverPhase phase = ClientHoverHandler.getPhase(entity);
        float hoverBlend = state.updateHoverBlend(ageInTicks, phase != HoverPhase.NONE, phase == HoverPhase.HOVERING);
        if (hoverBlend > 0.0F) {
            applyHoverPose(model, ageInTicks, hoverBlend, state.hoverFlap);
        }

        // Always track the flap (the dip uses it too, even on wings with no elbow like Light wings)
        float rootX = model.leftWing.xRot; // right wing mirrors the same xRot
        state.update(rootX, ageInTicks, entity.isFallFlying(), phase == HoverPhase.HOVERING,
                WingFlexConfig.UP_BEND_AMOUNT.get().floatValue(),
                WingFlexConfig.DOWN_BEND_AMOUNT.get().floatValue(),
                WingFlexConfig.FOLLOW_LAG.get().floatValue());

        if (!WingFlexConfig.ENABLED.get()) return;
        Joint[] joints = JOINTS.get(model);
        if (joints == null) return;
        if (model instanceof ZanzasWingsModel && !WingFlexConfig.BEND_ZANZA.get()) return;

        float bend = state.bend((float) Math.toRadians(WingFlexConfig.MAX_BEND_DEGREES.get()));
        if (Math.abs(bend) < 1.0E-4F) return;
        for (Joint j : joints) applyBend(j, bend);
    }

    /** Icarus' fully-spread flying root pose (its FLYING branch with no downward velocity). */
    private static final float HOVER_ROOT_X = 0.35F;
    private static final float HOVER_ROOT_Z = -1.25F;
    /** Hover flap: a bit slower and shallower than Icarus' powered flap (0.4 rad/tick, 1.0 rad). */
    private static final float HOVER_FLAP_SPEED = 0.35F;
    private static final float HOVER_FLAP_SIZE = 0.7F;

    private static void applyHoverPose(WingEntityModel<?> model, float ageInTicks, float blend, float flap) {
        float targetX = HOVER_ROOT_X + Mth.sin(ageInTicks * HOVER_FLAP_SPEED) * HOVER_FLAP_SIZE * flap;
        ModelPart left = model.leftWing;
        left.xRot = Mth.lerp(blend, left.xRot, targetX);
        left.yRot = Mth.lerp(blend, left.yRot, 0.0F);
        left.zRot = Mth.lerp(blend, left.zRot, HOVER_ROOT_Z);
        // Mirror exactly like Icarus does
        ModelPart right = model.rightWing;
        right.xRot = left.xRot;
        right.yRot = -left.yRot;
        right.zRot = -left.zRot;

        // Icarus roots the wings fairly low on the back (tuned for the horizontal flying pose). Upright, that reads
        // as mid/lower back, so lift them while braking/hovering. Model space is y-down: subtracting moves up.
        float lift = WingFlexConfig.HOVER_WING_LIFT.get().floatValue();
        left.y = Mth.lerp(blend, left.y, left.y - lift);
        right.y = left.y;

        // These models also switch their elbow angle by state (folded when idle, open when flying); open it
        float elbowFlyingX = elbowFlyingX(model);
        if (!Float.isNaN(elbowFlyingX)) {
            try {
                ModelPart l03 = left.getChild("leftWing01").getChild("leftWing02").getChild("leftWing03");
                ModelPart r03 = right.getChild("rightWing01").getChild("rightWing02").getChild("rightWing03");
                l03.xRot = Mth.lerp(blend, l03.xRot, elbowFlyingX);
                r03.xRot = l03.xRot;
            } catch (NoSuchElementException ignored) {
            }
        }
    }

    /** The FLYING-state elbow xRot each model sets in its own setupAnim, or NaN if the model doesn't do that. */
    private static float elbowFlyingX(WingEntityModel<?> model) {
        if (model instanceof FlandresWingsModel) return (float) Math.toRadians(45.0D);
        if (model instanceof FeatheredWingsModel || model instanceof LeatherWingsModel || model instanceof DiscordsWingsModel) {
            return (float) Math.toRadians(-32.5D);
        }
        return Float.NaN;
    }

    /**
     * How far (in blocks, <= 0) this entity's model should currently be pushed down by its flapping.
     * 0 when not flapping, not wearing wings, or when the wings weren't rendered recently.
     */
    public static float getDip(Entity entity, float partialTick) {
        FlexState state = freshState(entity, partialTick);
        return state == null ? 0.0F : state.dip;
    }

    /** How far (in blocks, >= 0) this entity's model should currently be lifted by its hover flapping. */
    public static float getPop(Entity entity, float partialTick) {
        FlexState state = freshState(entity, partialTick);
        return state == null ? 0.0F : state.pop;
    }

    private static FlexState freshState(Entity entity, float partialTick) {
        if (!(entity instanceof LivingEntity living)) return null;
        FlexState state = STATES.get(living);
        if (state == null || !state.initialised) return null;
        // Wings weren't rendered recently (unequipped, first person, culled...): don't leave a stale offset behind
        if (living.tickCount + partialTick - state.lastTime > 3.0F) return null;
        return state;
    }

    private static void applyBend(Joint j, float bend) {
        // Rotation from the root's local frame into the elbow's parent frame
        CHAIN.identity();
        for (ModelPart p : j.chain) {
            CHAIN.mul(TMP.rotationZYX(p.zRot, p.yRot, p.xRot));
        }
        // The root flaps around its local +X; express that axis in the elbow's parent frame
        CHAIN.conjugate();
        CHAIN.transform(ROOT_FLAP_AXIS, AXIS).normalize();

        // New elbow rotation = extra flap about that axis (pivoting at the elbow) * elbow's current rotation
        BEND.rotationAxis(bend, AXIS.x, AXIS.y, AXIS.z)
                .mul(TMP.rotationZYX(j.elbow.zRot, j.elbow.yRot, j.elbow.xRot));

        setEulerZYX(j.elbow, BEND);
    }

    /** Writes a quaternion back into ModelPart's Euler angles (ModelPart renders with rotationZYX(z, y, x)). */
    private static void setEulerZYX(ModelPart part, Quaternionf q) {
        q.transform(C0.set(1, 0, 0));
        q.transform(C1.set(0, 1, 0));
        q.transform(C2.set(0, 0, 1));
        part.yRot = (float) Math.asin(Mth.clamp(-C0.z, -1.0F, 1.0F));
        part.xRot = (float) Math.atan2(C1.z, C2.z);
        part.zRot = (float) Math.atan2(C0.y, C0.x);
    }

    private static Joint[] resolveJoints(WingEntityModel<?> model) {
        String[] leftPath, rightPath;
        if (model instanceof ZanzasWingsModel) {
            leftPath = new String[]{"lWing01", "lWing02"};
            rightPath = new String[]{"rWing01", "rWing02"};
        } else {
            // Feathered, Mechanical, Leather/Dragon, Discord's and Flandre's wings all share this skeleton:
            // 01 shoulder, 02 upper arm (inner membrane/feathers), 03 elbow (outer struts/feathers hang off it)
            leftPath = new String[]{"leftWing01", "leftWing02", "leftWing03"};
            rightPath = new String[]{"rightWing01", "rightWing02", "rightWing03"};
        }
        Joint left = resolve(model.leftWing, leftPath);
        Joint right = resolve(model.rightWing, rightPath);
        if (left == null || right == null) {
            // e.g. Light wings: blades are siblings, there's no elbow to bend
            IcarusAdditions.LOGGER.debug("[Icarus Additions] No elbow found for {}, leaving it stiff", model.getClass().getSimpleName());
            return null;
        }
        return new Joint[]{left, right};
    }

    private static Joint resolve(ModelPart root, String[] path) {
        try {
            ModelPart[] chain = new ModelPart[path.length - 1];
            ModelPart current = root;
            for (int i = 0; i < path.length; i++) {
                current = current.getChild(path[i]);
                if (i < path.length - 1) chain[i] = current;
            }
            // Snapshot the baked pose; resolve runs before Icarus' first setupAnim on this model
            return new Joint(root, chain, current, current.xRot, current.yRot, current.zRot);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * High-passes the root flap (so pose changes like idle -> flying don't bend the wing, only the flapping does),
     * scales it, then low-passes the result so the outer half trails slightly behind. Also drives the flap dip.
     * Time-based, so it's frame-rate independent.
     */
    private static final class FlexState {
        /** Ticks for the flap-amplitude envelope to fall off once flapping stops. */
        private static final float ENVELOPE_DECAY = 20.0F;
        /** Icarus' gliding sway peaks at ~0.1 rad; powered flapping (holding forward) is much bigger. */
        private static final float FLAP_AMP_MIN = 0.15F;
        private static final float FLAP_AMP_FULL = 0.35F;
        /** Ticks of smoothing on the dip itself. */
        private static final float DIP_SMOOTHING = 1.0F;

        private boolean initialised;
        private float lastTime;
        private float center;
        private float bend;
        private float amplitude;
        float dip;
        /** Hover pop: >= 0, the player rises on each downstroke while hovering. */
        float pop;

        /** Ticks for the wings to open/fold when entering/leaving a hover. */
        private static final float HOVER_BLEND_TIME = 3.0F;
        /** Ticks for the hovering flap to fade in once braking is done. */
        private static final float HOVER_FLAP_FADE_TIME = 4.0F;
        private float hoverBlend;
        float hoverFlap;
        private float hoverTime = Float.NaN;

        float updateHoverBlend(float time, boolean spread, boolean flapping) {
            float dt = Float.isNaN(hoverTime) ? 0.0F : time - hoverTime;
            hoverTime = time;
            float target = spread ? 1.0F : 0.0F;
            float flapTarget = flapping ? 1.0F : 0.0F;
            if (dt < 0.0F || dt > 20.0F) {
                hoverBlend = target; // long gap (wasn't rendered), just snap
                hoverFlap = flapTarget;
            } else if (dt > 0.0F) {
                hoverBlend += (target - hoverBlend) * (1.0F - (float) Math.exp(-dt / HOVER_BLEND_TIME));
                hoverFlap += (flapTarget - hoverFlap) * (1.0F - (float) Math.exp(-dt / HOVER_FLAP_FADE_TIME));
            }
            if (!spread && hoverBlend < 0.001F) hoverBlend = 0.0F;
            return hoverBlend;
        }

        void update(float rootX, float time, boolean flying, boolean hovering, float upAmount, float downAmount, float lag) {
            float dt = time - lastTime;
            if (!initialised || dt < 0.0F || dt > 20.0F) {
                initialised = true;
                lastTime = time;
                center = rootX;
                bend = 0.0F;
                amplitude = 0.0F;
                dip = 0.0F;
                pop = 0.0F;
                return;
            }
            lastTime = time;
            if (dt <= 0.0F) return; // same frame rendered twice (e.g. inventory screen), keep values

            center += (rootX - center) * (1.0F - (float) Math.exp(-dt / CENTER_TIME));
            // Positive = above the stroke's centre (root xRot up, verified: +xRot moves the tips to model +Z,
            // which is world-up while fall flying). Scaled separately so the upstroke can beat the rest droop.
            float flap = rootX - center;

            // --- elbow bend ---
            float target = flap * (flap > 0.0F ? upAmount : downAmount);
            if (lag > 0.0F) {
                bend += (target - bend) * (1.0F - (float) Math.exp(-dt / lag));
            } else {
                bend = target;
            }

            // --- flap dip ---
            // Envelope of the flap amplitude: tells real flapping apart from the small gliding sway
            amplitude = Math.max(Math.abs(flap), amplitude * (float) Math.exp(-dt / ENVELOPE_DECAY));
            float targetDip = 0.0F;
            float targetPop = 0.0F;
            float flapping = Mth.clamp((amplitude - FLAP_AMP_MIN) / (FLAP_AMP_FULL - FLAP_AMP_MIN), 0.0F, 1.0F);
            if (flapping > 0.0F) {
                // 0 = wings at the bottom of the stroke, 1 = wings at the top
                float strokeHeight = Mth.clamp((flap / amplitude + 1.0F) * 0.5F, 0.0F, 1.0F);
                if (flying && WingFlexConfig.DIP_ENABLED.get()) {
                    // Gliding: lowest when the wings are up, normal height when they're down
                    targetDip = -WingFlexConfig.DIP_DEPTH.get().floatValue() * flapping * strokeHeight;
                } else if (hovering && WingFlexConfig.HOVER_POP_ENABLED.get()) {
                    // Hovering: the mirror image. Highest when the wings are down (end of the downstroke),
                    // back to normal height when they're up
                    targetPop = WingFlexConfig.HOVER_POP_HEIGHT.get().floatValue() * flapping * (1.0F - strokeHeight);
                }
            }
            float smoothing = 1.0F - (float) Math.exp(-dt / DIP_SMOOTHING);
            dip += (targetDip - dip) * smoothing;
            pop += (targetPop - pop) * smoothing;
        }

        float bend(float maxBend) {
            return Mth.clamp(bend, -maxBend, maxBend);
        }
    }
}
