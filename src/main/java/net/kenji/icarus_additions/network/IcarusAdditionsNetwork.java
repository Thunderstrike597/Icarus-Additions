package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.IcarusAdditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Optional on both sides (acceptMissingOr): a server without the mod still lets players join, and the client
 * falls back to doing the hover locally.
 */
public final class IcarusAdditionsNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IcarusAdditions.MOD_ID, "main"),
            () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION),
            NetworkRegistry.acceptMissingOr(VERSION));

    private IcarusAdditionsNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, HoverStateC2SPacket.class, HoverStateC2SPacket::encode, HoverStateC2SPacket::decode,
                HoverStateC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, HoverSyncS2CPacket.class, HoverSyncS2CPacket::encode, HoverSyncS2CPacket::decode,
                HoverSyncS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
