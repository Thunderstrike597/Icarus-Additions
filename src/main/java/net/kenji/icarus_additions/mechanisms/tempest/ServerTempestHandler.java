package net.kenji.icarus_additions.mechanisms.tempest;

import net.kenji.icarus_additions.mechanisms.updraft.UpdraftHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Server half of the Tempest dash. The client applies the push (it owns its movement); the server validates, sets the
 * spin (isAutoSpinAttack is a server-owned synced flag: it drives the spin rendering for everyone and vanilla's
 * riptide hit damage), plays the riptide sound for everyone, and restarts wing flight when the dash ends.
 */
public final class ServerTempestHandler {
    private ServerTempestHandler() {}

    public static void onDashStart(ServerPlayer player) {
        if (!TempestHelper.canDash(player)) return; // not flying / no enchant / on cooldown: ignore

        int level = TempestHelper.getLevel(player);
        // Spin and wing flight can't overlap (the renderer would apply both rotations), so flight pauses for the spin
        player.stopFallFlying();
        player.startAutoSpinAttack(TempestHelper.SPIN_TICKS);
        player.resetFallDistance();
        TempestHelper.startCooldown(player);
        // null "except" = everyone nearby hears it, including the dashing player
        player.level().playSound(null, player, TempestHelper.getSound(level), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Spin is over: back into wing flight, same as Caelus' own flight packet. */
    public static void onDashEnd(ServerPlayer player) {
        player.resetFallDistance();
        if (UpdraftHelper.canStartWingFlight(player)) {
            player.startFallFlying();
        }
    }
}
