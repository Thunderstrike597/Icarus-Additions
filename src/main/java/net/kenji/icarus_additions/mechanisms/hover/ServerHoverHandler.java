package net.kenji.icarus_additions.mechanisms.hover;

import net.kenji.icarus_additions.IcarusAdditions;
import net.kenji.icarus_additions.network.HoverSyncS2CPacket;
import net.kenji.icarus_additions.network.IcarusAdditionsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of the hover: applies the hidden Slow Falling, keeps the player upright (pose is synced to everyone),
 * prevents fall damage, and tells nearby players about the hover so they render it too.
 * The client decides the phase (it owns its movement); the server only validates it.
 */
@Mod.EventBusSubscriber(modid = IcarusAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerHoverHandler {
    public static final Map<ServerPlayer, HoverPhase> PHASES = new WeakHashMap<>();

    private ServerHoverHandler() {}

    public static void onClientPhase(ServerPlayer player, HoverPhase phase) {
        if (phase != HoverPhase.NONE && !HoverHelper.canHover(player)) {
            phase = HoverPhase.NONE;
        }
        if (phase != HoverPhase.NONE && player.isFallFlying()) {
            player.stopFallFlying();
        }
        setPhase(player, phase);
    }

    private static void setPhase(ServerPlayer player, HoverPhase phase) {
        HoverPhase old = PHASES.getOrDefault(player, HoverPhase.NONE);
        if (old == phase) return;

        if (phase == HoverPhase.NONE) {
            PHASES.remove(player);
            HoverHelper.removeSlowFall(player);
        } else {
            PHASES.put(player, phase);
        }
        IcarusAdditionsNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new HoverSyncS2CPacket(player.getId(), phase));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        HoverPhase phase = PHASES.get(player);
        if (phase == null) return;

        // Fail-safes, in case the client's NONE packet is late or never comes
        if (!HoverHelper.canHover(player) || player.isFallFlying() || !player.isShiftKeyDown()) {
            setPhase(player, HoverPhase.NONE);
            return;
        }

        HoverHelper.forceUpright(player);
        player.resetFallDistance();
        if (phase == HoverPhase.HOVERING) {
            HoverHelper.applySlowFall(player);
        }
    }

    /** Someone new started seeing a hovering player: tell them, so they don't render them crouching. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            HoverPhase phase = PHASES.get(target);
            if (phase != null) {
                IcarusAdditionsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                        new HoverSyncS2CPacket(target.getId(), phase));
            }
        }
    }
}
