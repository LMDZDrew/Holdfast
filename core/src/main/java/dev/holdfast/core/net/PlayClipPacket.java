package dev.holdfast.core.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Start (or stop, with an empty clip) a Player Animator clip on a player, at the server's moment. Paired clips
 * (grabs, executions) carry the partner walker so both start on the same client frame; the walker's own clip is
 * in its synced entity data.
 */
public record PlayClipPacket(int playerId, String clip, int partnerId, boolean ownsArms, String partnerClip, int partnerNonce) {
    public PlayClipPacket(int playerId, String clip, int partnerId, boolean ownsArms) {
        this(playerId, clip, partnerId, ownsArms, "", -1);
    }
    public static PlayClipPacket stop(int playerId) {
        return new PlayClipPacket(playerId, "", -1, false);
    }

    static void encode(PlayClipPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.playerId);
        b.writeUtf(m.clip);
        b.writeVarInt(m.partnerId + 1);
        b.writeBoolean(m.ownsArms);
        b.writeUtf(m.partnerClip);
        b.writeVarInt(m.partnerNonce + 1);
    }

    static PlayClipPacket decode(FriendlyByteBuf b) {
        return new PlayClipPacket(b.readVarInt(), b.readUtf(), b.readVarInt() - 1, b.readBoolean(), b.readUtf(), b.readVarInt() - 1);
    }

    static void handle(PlayClipPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.core.client.ClientClips.play(m)));
        ctx.get().setPacketHandled(true);
    }
}
