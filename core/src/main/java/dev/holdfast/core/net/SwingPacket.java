package dev.holdfast.core.net;

import dev.holdfast.core.combat.MeleeSwings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A left click into the air (vanilla sends the server nothing it can act on). It only asks to start a swing; the
 * server applies its own cooldown and resolves contact later from its own state. It names no target and no outcome.
 */
public record SwingPacket() {
    static void encode(SwingPacket m, FriendlyByteBuf b) {}

    static SwingPacket decode(FriendlyByteBuf b) {
        return new SwingPacket();
    }

    static void handle(SwingPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p != null) MeleeSwings.click(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
