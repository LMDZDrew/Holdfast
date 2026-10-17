package dev.holdfast.core.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Cosmetic shot only. The server has already decided the execution kill; this never fires a projectile. */
public record GunShotPacket(int shooterId, ItemStack gun) {
    static void encode(GunShotPacket m, FriendlyByteBuf b) { b.writeVarInt(m.shooterId); b.writeItem(m.gun); }
    static GunShotPacket decode(FriendlyByteBuf b) { return new GunShotPacket(b.readVarInt(), b.readItem()); }
    static void handle(GunShotPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.holdfast.core.client.TaczShotVisual.play(m.shooterId, m.gun)));
        ctx.get().setPacketHandled(true);
    }
}
