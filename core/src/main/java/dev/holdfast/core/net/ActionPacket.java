package dev.holdfast.core.net;

import dev.holdfast.core.combat.Executions;
import dev.holdfast.core.combat.Grabs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The one context key (design.md: "a consistent context key"): pressed while grabbed it is an escape attempt;
 * otherwise, looking at a walker, an execution request; pressed during your own execution before commit, a cancel.
 * The server decides which it is and whether it's accepted, using its own arrival tick.
 */
public record ActionPacket(int lookedAtEntity) {
    static void encode(ActionPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.lookedAtEntity + 1);
    }

    static ActionPacket decode(FriendlyByteBuf b) {
        return new ActionPacket(b.readVarInt() - 1);
    }

    static void handle(ActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p == null) return;
            if (Grabs.isGrabbed(p)) {
                Grabs.escape(p);
            } else if (Executions.isExecuting(p)) {
                Executions.cancel(p);
            } else if (m.lookedAtEntity >= 0) {
                Executions.request(p, p.level().getEntity(m.lookedAtEntity));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
