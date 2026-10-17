package dev.holdfast.core.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Another player's injury movement state, so everyone sees their limp or crutches (presentation only). */
public record MovementPacket(int playerId, String movement) {
    static void encode(MovementPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.playerId);
        b.writeUtf(m.movement);
    }

    static MovementPacket decode(FriendlyByteBuf b) {
        return new MovementPacket(b.readVarInt(), b.readUtf());
    }

    static void handle(MovementPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.core.client.ClientClips.remoteMovement(m)));
        ctx.get().setPacketHandled(true);
    }
}
