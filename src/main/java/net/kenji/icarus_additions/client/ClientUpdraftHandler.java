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
 * Client half of Updraft. CHARGED / NONE come from the server (it owns the crouch timer). The local player's jump
 * while charged starts the launch immediately (movement is client-authoritative) and tells the server. The client
 * lifts the player to the target height, then starts Icarus flight exactly like vanilla does when you jump mid-air.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientUpdraftHandler {

    /** Upward speed = remaining height x this (per tick), so it eases out as it nears the top... */
    private static final double ASCENT_RATE = 0.22D;
    /** ...but never slower than this (blocks/tick), and never faster than the max. Both scale with the speed config. */
    private static final double ASCENT_MIN_SPEED = 0.25D;
    private static final double ASCENT_MAX_SPEED = 1.4D;
    /** Close enough to the target to count as the top. */
    private static final double TOP_EPSILON = 0.2D;
    private static final int MAX_ASCENT_TICKS = 100;
    /** Ticks for the single heavy flap. */
    public static final float BEAT_TICKS = 10.0F;

    private static final class State {
        UpdraftPhase phase = UpdraftPhase.NONE;
        float launchTime;
        double targetY;
        int ascentTicks;
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

        if (player == Minecraft.getInstance().player) {
            tickLocal((LocalPlayer) player, s);
        } else if (s.phase == UpdraftPhase.LAUNCHING) {
            HoverHelper.forceUpright(player);
        }
    }

    private static void tickLocal(LocalPlayer player, State s) {
        if (s.blockHoverUntilUncrouch && !player.isShiftKeyDown()) {
            s.blockHoverUntilUncrouch = false;
        }

        if (s.phase == UpdraftPhase.CHARGED && player.input.jumping) {
            int level = UpdraftHelper.getLevel(player);
            if (level > 0) {
                if (IcarusAdditions.EFM_LOADED) {
                    EpicFightUpdraftAnimations.playLaunch(player);
                }
                s.phase = UpdraftPhase.LAUNCHING;
                s.launchTime = player.tickCount;
                s.targetY = player.getY() + UpdraftHelper.getLiftHeight(level);
                s.ascentTicks = 0;
                s.blockHoverUntilUncrouch = true;
                IcarusAdditionsNetwork.CHANNEL.sendToServer(new UpdraftLaunchC2SPacket());
            }
        }

        if (s.phase != UpdraftPhase.LAUNCHING) return;

        s.ascentTicks++;
        HoverHelper.forceUpright(player);
        player.resetFallDistance();

        double remaining = s.targetY - player.getY();
        boolean hitCeiling = s.ascentTicks > 2 && player.verticalCollision && player.getDeltaMovement().y <= 0.0D;
        if (player.isFallFlying()) { // double-tapped jump mid-ascent: flight already started
            s.phase = UpdraftPhase.NONE;
            return;
        }
        if (remaining <= TOP_EPSILON || hitCeiling || player.isInWater() || s.ascentTicks >= MAX_ASCENT_TICKS) {
            finishLaunch(player, s);
            return;
        }

        // Written at the end of the tick, so it's exactly how far the player moves next tick (gravity/drag come after)
        double speedMult = ConfigServer.ASCENT_SPEED_MULTIPLIER.get();
        double vy = Mth.clamp(remaining * Math.min(0.9D, ASCENT_RATE * speedMult),
                ASCENT_MIN_SPEED * speedMult, ASCENT_MAX_SPEED * speedMult);
        vy = Math.min(vy, remaining + 0.05D);
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.6D, vy, v.z * 0.6D);
    }

    /** Top reached: hand over to regular Icarus flight (same as jumping mid-air with wings). */
    private static void finishLaunch(LocalPlayer player, State s) {
        s.phase = UpdraftPhase.NONE;
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
