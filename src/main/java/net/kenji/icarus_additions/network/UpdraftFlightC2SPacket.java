package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.mechanisms.updraft.ServerUpdraftHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: "the launch reached the top, start flying". Needed because Icarus flies through Caelus, which
 * doesn't use vanilla's START_FALL_FLYING command (that one checks the chest slot for an elytra and rejects wings).
 */
public record UpdraftFlightC2SPacket() {

    public static void encode(UpdraftFlightC2SPacket msg, FriendlyByteBuf buf) {
    }

    public static UpdraftFlightC2SPacket decode(FriendlyByteBuf buf) {
        return new UpdraftFlightC2SPacket();
    }

    public static void handle(UpdraftFlightC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ServerUpdraftHandler.onFlightRequest(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
