package net.kenji.icarus_additions.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The vanilla "Flying is not enabled on this server" kick counts ticks spent floating (not falling by at least
 * 1/32 block per tick, no elytra/levitation). A Zephyr hover holds still or rises on purpose, so the server resets
 * that counter while a validated Zephyr hover is active.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
    @Accessor("aboveGroundTickCount")
    void icarusAdditions$setAboveGroundTickCount(int ticks);
}
