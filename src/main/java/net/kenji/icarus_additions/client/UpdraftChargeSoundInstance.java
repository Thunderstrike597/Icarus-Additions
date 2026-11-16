package net.kenji.icarus_additions.client;

import net.kenji.icarus_additions.mechanisms.updraft.UpdraftPhase;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Looping elytra wind while a player is Updraft-charged. Fades in, follows the player, fades out when the charge ends. */
public class UpdraftChargeSoundInstance extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 0.6F;
    private static final float FADE_IN_TICKS = 20.0F;
    private static final float FADE_OUT_TICKS = 10.0F;

    private final Player player;
    private int time;
    private float fade; // 0..1

    public UpdraftChargeSoundInstance(Player player) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 0.8F; // a bit lower than flying: more of a deep draft
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true; // starts at volume 0 for the fade-in; without this the sound engine may skip it
    }

    @Override
    public void tick() {
        time++;
        if (player.isRemoved()) {
            stop();
            return;
        }

        boolean charged = ClientUpdraftHandler.getPhase(player) == UpdraftPhase.CHARGED;
        if (charged) {
            fade = Math.min(1.0F, fade + 1.0F / FADE_IN_TICKS);
        } else {
            fade -= 1.0F / FADE_OUT_TICKS;
            if (fade <= 0.0F) {
                stop();
                return;
            }
        }

        x = player.getX();
        y = player.getY();
        z = player.getZ();
        volume = MAX_VOLUME * fade;
    }
}