package dev.holdfast.core.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The escape prompt for the grabbed player: display only. {@code windowTicks} = 0 clears it. */
public record GrabPromptPacket(int windowTicks, boolean ground, String result) {
    public static GrabPromptPacket clear(String result) {
        return new GrabPromptPacket(0, false, result);
    }

    static void encode(GrabPromptPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.windowTicks);
        b.writeBoolean(m.ground);
        b.writeUtf(m.result);
    }

    static GrabPromptPacket decode(FriendlyByteBuf b) {
        return new GrabPromptPacket(b.readVarInt(), b.readBoolean(), b.readUtf());
    }

    static void handle(GrabPromptPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.holdfast.core.client.ClientState.prompt(m)));
        ctx.get().setPacketHandled(true);
    }
}
