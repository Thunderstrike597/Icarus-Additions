package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.mechanisms.hover.HoverPhase;
import net.kenji.icarus_additions.mechanisms.hover.ServerHoverHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "my hover phase is now X". The client owns its movement, so it decides when braking ends. */
public record HoverStateC2SPacket(HoverPhase phase) {

    public static void encode(HoverStateC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.phase.ordinal());
    }

    public static HoverStateC2SPacket decode(FriendlyByteBuf buf) {
        return new HoverStateC2SPacket(HoverPhase.byId(buf.readByte()));
    }

    public static void handle(HoverStateC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ServerHoverHandler.onClientPhase(player, msg.phase);
        });
        ctx.get().setPacketHandled(true);
    }
}
