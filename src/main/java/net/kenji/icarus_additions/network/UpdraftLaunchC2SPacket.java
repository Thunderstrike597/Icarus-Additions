package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.mechanisms.updraft.ServerUpdraftHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "I jumped while charged". The server only accepts it if it agrees the player is charged. */
public record UpdraftLaunchC2SPacket() {

    public static void encode(UpdraftLaunchC2SPacket msg, FriendlyByteBuf buf) {
    }

    public static UpdraftLaunchC2SPacket decode(FriendlyByteBuf buf) {
        return new UpdraftLaunchC2SPacket();
    }

    public static void handle(UpdraftLaunchC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerUpdraftHandler.onLaunchRequest(player);

            }

        });
        ctx.get().setPacketHandled(true);
    }
}
