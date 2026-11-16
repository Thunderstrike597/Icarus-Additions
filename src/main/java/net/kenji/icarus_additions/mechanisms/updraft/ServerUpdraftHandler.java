package net.kenji.icarus_additions.mechanisms.updraft;

import net.kenji.icarus_additions.ConfigServer;
import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.mechanisms.hover.HoverHelper;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.kenji.icarus_additions.network.UpdraftSyncS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-authoritative half of Updraft: counts the crouch-still timer with the server's config, decides when the
 * player is CHARGED (or loses it), validates the launch the client asks for, spawns the draft particles for
 * everyone, and keeps the player fall-damage free while being lifted.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerUpdraftHandler {

    /** Squared horizontal blocks per tick still counted as "standing still". */
    private static final double STILL_EPSILON_SQR = 1.0E-4D;
    /** Give up on a launch that never turned into flight. */
    private static final int MAX_LAUNCH_TICKS = 200;

    private static final class State {
        UpdraftPhase phase = UpdraftPhase.NONE;
        int chargeTicks;
        int phaseTicks;
        double lastX, lastZ;
        boolean hasLast;
    }

    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    private ServerUpdraftHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        State s = STATES.computeIfAbsent(player, p -> new State());

        double dx = player.getX() - s.lastX;
        double dz = player.getZ() - s.lastZ;
        boolean still = s.hasLast && dx * dx + dz * dz < STILL_EPSILON_SQR;
        s.lastX = player.getX();
        s.lastZ = player.getZ();
        s.hasLast = true;

        boolean enabled = ConfigServer.ENABLED.get();
        int level = enabled ? UpdraftHelper.getLevel(player) : 0;
        boolean crouchingStill = level > 0 && player.isShiftKeyDown() && player.onGround() && still
                && UpdraftHelper.canUse(player);

        switch (s.phase) {
            case NONE -> {
                if (crouchingStill) {
                    s.chargeTicks++;
                    if (s.chargeTicks >= Math.round(UpdraftHelper.getChargeTimeFromLevel(level) * 20.0D)) {
                        setPhase(player, s, UpdraftPhase.CHARGED);
                    }
                } else {
                    s.chargeTicks = 0; // moved / stood up before the threshold: restart the count
                }
            }
            case CHARGED -> {
                // Moving, standing up, or leaving the ground without launching cancels the charge.
                // (A launch arrives as a packet before the client's jump movement, so this doesn't eat real launches.)
                if (!crouchingStill) {
                    setPhase(player, s, UpdraftPhase.NONE);
                } else if (ConfigServer.PARTICLES.get()) {
                    spawnDraftParticles(player);
                }
            }
            case LAUNCHING -> {
                s.phaseTicks++;
                player.resetFallDistance();
                HoverHelper.forceUpright(player); // crouch may still be held while rising
                boolean landed = player.onGround() && s.phaseTicks > 5;
                if (player.isFallFlying() || landed || player.isInWater() || s.phaseTicks > MAX_LAUNCH_TICKS) {
                    setPhase(player, s, UpdraftPhase.NONE);
                }
            }
        }
    }

    /** Client pressed jump while charged. */
    public static void onLaunchRequest(ServerPlayer player) {
        State s = STATES.get(player);
        if (s == null || s.phase != UpdraftPhase.CHARGED) {
            // Not charged as far as the server knows (e.g. lag): correct the client
            IcarusAdditionsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new UpdraftSyncS2CPacket(player.getId(), s == null ? UpdraftPhase.NONE : s.phase));
            return;
        }
        setPhase(player, s, UpdraftPhase.LAUNCHING);
        player.resetFallDistance();
        if (ConfigServer.PARTICLES.get()) spawnLaunchBurst(player);
    }

    /** Client reached the top of the launch: start wing flight server-side, the same way Caelus' own packet does. */
    public static void onFlightRequest(ServerPlayer player) {
        player.stopFallFlying();
        if (UpdraftHelper.canStartWingFlight(player)) {
            player.startFallFlying();
        }
        State s = STATES.get(player);
        if (s != null && s.phase == UpdraftPhase.LAUNCHING) {
            setPhase(player, s, UpdraftPhase.NONE);
        }
    }

    private static void setPhase(ServerPlayer player, State s, UpdraftPhase phase) {
        if (s.phase == phase) return;
        s.phase = phase;
        s.phaseTicks = 0;
        s.chargeTicks = 0;
        IcarusAdditionsNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new UpdraftSyncS2CPacket(player.getId(), phase));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            State s = STATES.get(target);
            if (s != null && s.phase != UpdraftPhase.NONE) {
                IcarusAdditionsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                        new UpdraftSyncS2CPacket(target.getId(), s.phase));
            }
        }
    }

    // ---------------------------------------------------------------- particles (sent to everyone nearby)

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



    private static void spawnLaunchBurst(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        for (int i = 0; i < 16; i++) {
            float angle = i / 16.0F * Mth.TWO_PI + random.nextFloat() * 0.3F;
            double x = player.getX() + Mth.cos(angle) * 0.4D;
            double z = player.getZ() + Mth.sin(angle) * 0.4D;
            level.sendParticles(ParticleTypes.POOF, x, player.getY() + 0.1D, z, 0,
                    Mth.cos(angle) * 0.25D, 0.02D, Mth.sin(angle) * 0.25D, 1.0D);
        }
    }
}
