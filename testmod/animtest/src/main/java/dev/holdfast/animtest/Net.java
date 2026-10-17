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
    private static final String VERSION = "2";
    private static final java.util.Map<java.util.UUID, Play> LAST = new java.util.HashMap<>();
    private record Pending(int tick, net.minecraft.world.item.ItemStack gun) { }
    private static final java.util.Map<java.util.UUID, Pending> SHOTS = new java.util.HashMap<>();
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HoldfastAnimTest.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private Net() {}

    static void register() {
        CHANNEL.registerMessage(0, Play.class, Play::encode, Play::decode, Play::handle);
        CHANNEL.registerMessage(1, Freeze.class, Freeze::encode, Freeze::decode, Freeze::handle);
        CHANNEL.registerMessage(2, Shot.class, Shot::encode, Shot::decode, Shot::handle);
    }

    /** Sent to the player and everyone tracking them, so remote players are part of the test too. */
    public static void send(ServerPlayer player, Play message) {
        SHOTS.remove(player.getUUID());
        Integer tick = Clips.PISTOL_SHOT_TICKS.get(message.playerClip());
        if (tick != null) SHOTS.put(player.getUUID(), new Pending(player.server.getTickCount() + tick, player.getMainHandItem().copy()));
        if (!message.playerClip().isEmpty()) LAST.put(player.getUUID(), message);
        else LAST.remove(player.getUUID());
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }

    public static boolean freeze(ServerPlayer player, double seconds) {
        SHOTS.remove(player.getUUID());
        Play last = LAST.get(player.getUUID());
        if (last == null) return false;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Freeze(player.getId(), last.walkerId(), seconds));
        return true;
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        SHOTS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true;
            if (server.getTickCount() < entry.getValue().tick) return false;
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Shot(player.getId(), entry.getValue().gun));
            HoldfastAnimTest.LOG.info("[holdfast-test] cosmetic pistol shot at server tick {}", server.getTickCount());
            return true;
        });
    }

    public record Shot(int playerId, net.minecraft.world.item.ItemStack gun) {
        static void encode(Shot m, FriendlyByteBuf b) { b.writeVarInt(m.playerId); b.writeItem(m.gun); }
        static Shot decode(FriendlyByteBuf b) { return new Shot(b.readVarInt(), b.readItem()); }
        static void handle(Shot m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.holdfast.animtest.client.TaczShotVisual.play(m.playerId, m.gun)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record Freeze(int playerId, int walkerId, double seconds) {
        static void encode(Freeze m, FriendlyByteBuf b) { b.writeVarInt(m.playerId); b.writeInt(m.walkerId); b.writeDouble(m.seconds); }
        static Freeze decode(FriendlyByteBuf b) { return new Freeze(b.readVarInt(), b.readInt(), b.readDouble()); }
        static void handle(Freeze m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> dev.holdfast.animtest.client.PlayerClips.freeze(m)));
            ctx.get().setPacketHandled(true);
        }
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
