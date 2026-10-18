package dev.holdfast.core.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Entity-owned voice: clients can stop an already playing recording when that walker dies. */
public record WalkerVocalPacket(int entityId, ResourceLocation sound, float volume, float pitch) {
    static void encode(WalkerVocalPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId); b.writeResourceLocation(m.sound); b.writeFloat(m.volume); b.writeFloat(m.pitch);
    }
    static WalkerVocalPacket decode(FriendlyByteBuf b) {
        return new WalkerVocalPacket(b.readVarInt(), b.readResourceLocation(), b.readFloat(), b.readFloat());
    }
    static void handle(WalkerVocalPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.holdfast.core.client.WalkerVoices.play(m)));
        ctx.get().setPacketHandled(true);
    }
}
