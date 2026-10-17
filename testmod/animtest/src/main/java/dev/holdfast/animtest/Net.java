package dev.holdfast.animtest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** One server-to-client message that starts a player clip and, for pairs, the attacker's clips on the same client frame. */
public final class Net {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HoldfastAnimTest.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {}

    static void register() {
        CHANNEL.registerMessage(0, Play.class, Play::encode, Play::decode, Play::handle);
    }

    /** Sent to the player and everyone tracking them, so remote players are part of the test too. */
    public static void send(ServerPlayer player, Play message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }

    /**
     * @param playerId    player entity id
     * @param playerClip  Player Animator clip key (empty = stop)
     * @param firstPerson Player Animator FirstPersonMode name
     * @param camera      bit 1 apply camera rotation track, 2 flip yaw, 4 flip roll, 8 flip pitch
     * @param walkerId    test walker entity id, or -1
     * @param body        walker body-layer or full clip; arms/head layer clips ("" = layer off)
     * @param nonce       walker nonce the server stored with the same clips, so the later entity-data sync doesn't restart them
     */
    public record Play(int playerId, String playerClip, String firstPerson, int camera,
                       int walkerId, String body, String arms, String head, int nonce, boolean crawler) {
        static void encode(Play m, FriendlyByteBuf b) {
            b.writeVarInt(m.playerId);
            b.writeUtf(m.playerClip);
            b.writeUtf(m.firstPerson);
            b.writeVarInt(m.camera);
            b.writeVarInt(m.walkerId + 1);
            b.writeUtf(m.body);
            b.writeUtf(m.arms);
            b.writeUtf(m.head);
            b.writeVarInt(m.nonce);
            b.writeBoolean(m.crawler);
        }

        static Play decode(FriendlyByteBuf b) {
            return new Play(b.readVarInt(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt() - 1,
                    b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readBoolean());
        }

        static void handle(Play m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.holdfast.animtest.client.PlayerClips.onPacket(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
