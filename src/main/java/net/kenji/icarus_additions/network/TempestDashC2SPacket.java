package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.mechanisms.tempest.ServerTempestHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Tempest dash started (start = true) or finished, so flight should resume (start = false). */
public record TempestDashC2SPacket(boolean start) {

    public static void encode(TempestDashC2SPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.start);
    }

    public static TempestDashC2SPacket decode(FriendlyByteBuf buf) {
        return new TempestDashC2SPacket(buf.readBoolean());
    }

    public static void handle(TempestDashC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.start) {
                ServerTempestHandler.onDashStart(player);
            } else {
                ServerTempestHandler.onDashEnd(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
