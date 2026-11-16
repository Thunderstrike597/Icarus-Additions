package net.kenji.icarus_additions.network;

import net.kenji.icarus_additions.mechanisms.updraft.UpdraftPhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients (the player themself + everyone tracking them): Updraft phase changed. */
public record UpdraftSyncS2CPacket(int entityId, UpdraftPhase phase) {

    public static void encode(UpdraftSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.phase.ordinal());
    }

    public static UpdraftSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new UpdraftSyncS2CPacket(buf.readVarInt(), UpdraftPhase.byId(buf.readByte()));
    }

    public static void handle(UpdraftSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.kenji.icarus_additions.client.ClientUpdraftHandler.onSync(msg.entityId, msg.phase)));
        ctx.get().setPacketHandled(true);
    }
}
