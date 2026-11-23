package net.kenji.icarus_additions.compat.emf;

import dev.cammiescorner.icarus.util.IcarusHelper;
import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.client.ClientHoverHandler;
import net.kenji.icarus_additions.client.ClientTempestHandler;
import net.kenji.icarus_additions.client.ClientUpdraftHandler;
import net.kenji.icarus_additions.mechanisms.hover.HoverPhase;
import net.kenji.icarus_additions.mechanisms.updraft.UpdraftPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.util.valueproviders.SampledFloat;
import net.minecraft.world.entity.player.Player;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Entity Model Features compat: exposes our flight states as variables that EMF animation packs (Fresh Moves etc.)
 * can read in their .jem/.jpm expressions, e.g.  "varb.x": "icarus_hovering"  or  "if(icarus_hovering, ..., ...)".
 *
 * We don't pick animations here: the pack's expressions do. We just give them something to branch on.
 *
 * EMF variables are global suppliers with no arguments. EMF sets "the entity currently being animated" before it
 * evaluates a model, so each supplier asks EMFAnimationApi.getCurrentEntity() who it's being evaluated for. That's
 * what makes them per-player even though they're registered once.
 *
 * Client only, and only class-loaded when EMF is installed (see IcarusAdditions).
 */
public final class EMFCompat {
    private static final String MOD = IcarusAdditions.MOD_ID;

    private EMFCompat() {}

    public static void init() {
        try {
            // ---- booleans (use directly in conditions; EMF also registers "!name" for the negation)
            bool("icarus_has_wings", "True if the player is wearing Icarus wings",
                    IcarusHelper::hasWings);
            bool("icarus_flying", "True while flying with Icarus wings (fall-flying with wings, not an elytra)",
                    p -> p.isFallFlying() && IcarusHelper.hasWings(p));
            bool("icarus_hovering", "True while hovering or braking into a hover (crouch out of flight)",
                    p -> ClientHoverHandler.getPhase(p) != HoverPhase.NONE);
            bool("icarus_hover_braking", "True during the braking part of the hover (still bleeding off speed)",
                    ClientHoverHandler::isBreaking);
            bool("icarus_hover_still", "True once the hover has settled (hanging in the air)",
                    ClientHoverHandler::isHovering);
            bool("icarus_updraft_charged", "True while crouched with Updraft charged (wings spread, ready to launch)",
                    p -> ClientUpdraftHandler.getPhase(p) == UpdraftPhase.CHARGED);
            bool("icarus_updraft_launching", "True during the Updraft launch (big flap, rising)",
                    p -> ClientUpdraftHandler.getPhase(p) == UpdraftPhase.LAUNCHING);
            bool("icarus_dashing", "True during a Tempest dash",
                    ClientTempestHandler::isDashing);

            // ---- floats (blocks/tick, facing-relative; 0 when not hovering) for leaning/limb swing in the pack
            number("icarus_hover_forward", "Hover movement forward (negative = backward), blocks/tick",
                    p -> ClientHoverHandler.getHoverMovement(p).forward());
            number("icarus_hover_strafe", "Hover movement to the left (negative = right), blocks/tick",
                    p -> ClientHoverHandler.getHoverMovement(p).strafe());
            number("icarus_hover_vertical", "Hover movement up (negative = down), blocks/tick",
                    p -> ClientHoverHandler.getHoverMovement(p).vertical());
            number("icarus_hover_lean", "Extra body pitch (degrees) while easing from flying pose up to standing",
                    p -> ClientHoverHandler.getUprightLean(p, Minecraft.getInstance().getFrameTime()));

            IcarusAdditions.LOGGER.info("Registered Entity Model Features animation variables (EMF API v{})",
                    EMFAnimationApi.getApiVersion());
        } catch (Throwable t) {
            // Never let an EMF API change crash the game; the pack just won't see our variables
            IcarusAdditions.LOGGER.error("Failed to register Entity Model Features variables", t);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The player EMF is animating right now, or null (non-player entity, block entity, nothing). */
    private static Player currentPlayer() {
        EMFEntity e = EMFAnimationApi.getCurrentEntity();
        return e instanceof Player p ? p : null; // EMFEntity is mixed into Entity, so this cast is safe
    }

    private static void bool(String name, String explanation, Predicate<Player> test) throws Exception {
        BooleanSupplier supplier = () -> {
            Player p = currentPlayer();
            return p != null && test.test(p);
        };
        EMFAnimationApi.registerSingletonAnimationVariable(MOD, name, explanation, supplier);
    }

    private static void number(String name, String explanation, ToDoubleFunction<Player> value) throws Exception {
        SampledFloat supplier = (randomSource) -> {
            Player p = currentPlayer();
            return p == null ? 0.0F : (float) value.applyAsDouble(p);
        };
        EMFAnimationApi.registerSingletonAnimationVariable(MOD, name, explanation, supplier);
    }
}
