package dev.holdfast.core.net;

import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A contact the server has already decided: where, which contact family, which region, and the outcome. Clients
 * play the matching flesh/skull sound and blood spatter. Sent only after the rule resolved; never sent for misses.
 */
public record HitFeedbackPacket(int targetId, double x, double y, double z, double dx, double dy, double dz,
                                WeaponClass.Contact contact, BodyRegion region, WalkerStrikeRules.Outcome outcome) {
    public static HitFeedbackPacket of(int targetId, Vec3 at, Vec3 direction, WeaponClass.Contact contact, BodyRegion region,
                                       WalkerStrikeRules.Outcome outcome) {
        Vec3 d = direction.lengthSqr() < 1e-6 ? Vec3.ZERO : direction.normalize();
        return new HitFeedbackPacket(targetId, at.x, at.y, at.z, d.x, d.y, d.z, contact, region, outcome);
    }

    static void encode(HitFeedbackPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.targetId);
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeFloat((float) m.dx);
        b.writeFloat((float) m.dy);
        b.writeFloat((float) m.dz);
        b.writeEnum(m.contact);
        b.writeEnum(m.region);
        b.writeEnum(m.outcome);
    }

    static HitFeedbackPacket decode(FriendlyByteBuf b) {
        return new HitFeedbackPacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readEnum(WeaponClass.Contact.class), b.readEnum(BodyRegion.class), b.readEnum(WalkerStrikeRules.Outcome.class));
    }

    static void handle(HitFeedbackPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.core.client.HitEffects.play(m)));
        ctx.get().setPacketHandled(true);
    }
}
