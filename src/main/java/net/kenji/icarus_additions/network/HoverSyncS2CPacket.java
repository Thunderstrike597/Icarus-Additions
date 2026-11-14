package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.hover.HoverPhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> other clients: "player with this entity id is now in hover phase X" (for the upright pose + wings). */
public record HoverSyncS2CPacket(int entityId, HoverPhase phase) {

    public static void encode(HoverSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.phase.ordinal());
    }

    public static HoverSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new HoverSyncS2CPacket(buf.readVarInt(), HoverPhase.byId(buf.readByte()));
    }

    public static void handle(HoverSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.kenji.icarus_additions.client.ClientHoverHandler.onRemoteSync(msg.entityId, msg.phase)));
        ctx.get().setPacketHandled(true);
    }
}
