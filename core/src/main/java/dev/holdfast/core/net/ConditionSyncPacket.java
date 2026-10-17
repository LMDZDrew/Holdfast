package dev.holdfast.core.net;

import dev.holdfast.core.rules.InfectionRules;
import dev.holdfast.core.state.PlayerCondition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The owner's read-only copy of their condition, for the HUD and locomotion clip choice. */
public record ConditionSyncPacket(boolean infected, String biteRegion, float progress, int amputationSecondsLeft,
                                  String movement, int blockedBites) {
    public static ConditionSyncPacket of(PlayerCondition c) {
        InfectionRules.Bite b = c.bite();
        int amp = b == null ? 0 : (int) (InfectionRules.amputationTicksLeft(b, c.onlineTicks()) / 20);
        return new ConditionSyncPacket(b != null, b == null ? "" : b.region().name(), (float) c.infectionProgress(), amp,
                c.movement().name(), c.blockedBites());
    }

    static void encode(ConditionSyncPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.infected);
        b.writeUtf(m.biteRegion);
        b.writeFloat(m.progress);
        b.writeVarInt(m.amputationSecondsLeft);
        b.writeUtf(m.movement);
        b.writeVarInt(m.blockedBites);
    }

    static ConditionSyncPacket decode(FriendlyByteBuf b) {
        return new ConditionSyncPacket(b.readBoolean(), b.readUtf(), b.readFloat(), b.readVarInt(), b.readUtf(), b.readVarInt());
    }

    static void handle(ConditionSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.core.client.ClientState.condition(m)));
        ctx.get().setPacketHandled(true);
    }
}
