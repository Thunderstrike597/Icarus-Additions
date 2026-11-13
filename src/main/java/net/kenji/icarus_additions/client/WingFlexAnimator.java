package net.kenji.icarus_additions.client;

import dev.cammiescorner.icarus.client.models.WingEntityModel;
import dev.cammiescorner.icarus.client.models.ZanzasWingsModel;
import net.kenji.icarus_additions.IcarusWingFlex;
import net.kenji.icarus_additions.WingFlexConfig;
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
            IcarusWingFlex.LOGGER.info("[Icarus Wing Flex] Hooked into Icarus wing rendering.");
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
        // Always track the flap (the dip uses it too, even on wings with no elbow like Light wings)
        float rootX = model.leftWing.xRot; // right wing mirrors the same xRot
        FlexState state = STATES.computeIfAbsent(entity, e -> new FlexState());
        state.update(rootX, ageInTicks, entity.isFallFlying(),
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

    /**
     * How far (in blocks, <= 0) this entity's model should currently be pushed down by its flapping.
     * 0 when not flapping, not wearing wings, or when the wings weren't rendered recently.
     */
    public static float getDip(Entity entity, float partialTick) {
        if (!(entity instanceof LivingEntity living)) return 0.0F;
        FlexState state = STATES.get(living);
        if (state == null || !state.initialised) return 0.0F;
        // Wings weren't rendered recently (unequipped, first person, culled...): don't leave a stale dip behind
        if (living.tickCount + partialTick - state.lastTime > 3.0F) return 0.0F;
        return state.dip;
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
            IcarusWingFlex.LOGGER.debug("[Icarus Wing Flex] No elbow found for {}, leaving it stiff", model.getClass().getSimpleName());
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

        void update(float rootX, float time, boolean flying, float upAmount, float downAmount, float lag) {
            float dt = time - lastTime;
            if (!initialised || dt < 0.0F || dt > 20.0F) {
                initialised = true;
                lastTime = time;
                center = rootX;
                bend = 0.0F;
                amplitude = 0.0F;
                dip = 0.0F;
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
            if (flying && WingFlexConfig.DIP_ENABLED.get()) {
                float flapping = Mth.clamp((amplitude - FLAP_AMP_MIN) / (FLAP_AMP_FULL - FLAP_AMP_MIN), 0.0F, 1.0F);
                if (flapping > 0.0F) {
                    // 0 = wings at the bottom of the stroke (normal height), 1 = wings at the top (lowest point)
                    float strokeHeight = Mth.clamp((flap / amplitude + 1.0F) * 0.5F, 0.0F, 1.0F);
                    targetDip = -WingFlexConfig.DIP_DEPTH.get().floatValue() * flapping * strokeHeight;
                }
            }
            dip += (targetDip - dip) * (1.0F - (float) Math.exp(-dt / DIP_SMOOTHING));
        }

        float bend(float maxBend) {
            return Mth.clamp(bend, -maxBend, maxBend);
        }
    }
}
