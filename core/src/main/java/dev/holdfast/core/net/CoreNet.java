package dev.holdfast.core.net;

import dev.holdfast.core.HoldfastCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Server-to-client packets announce decided results (condition, clips, hit feedback, grab prompt); client-to-server
 * packets are requests the server validates (escape, execute, cancel, swing into the air). No client packet carries an outcome.
 */
public final class CoreNet {
    private static final String VERSION = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HoldfastCore.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private CoreNet() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ConditionSyncPacket.class, ConditionSyncPacket::encode, ConditionSyncPacket::decode,
                ConditionSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PlayClipPacket.class, PlayClipPacket::encode, PlayClipPacket::decode,
                PlayClipPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, HitFeedbackPacket.class, HitFeedbackPacket::encode, HitFeedbackPacket::decode,
                HitFeedbackPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GrabPromptPacket.class, GrabPromptPacket::encode, GrabPromptPacket::decode,
                GrabPromptPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode,
                ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MovementPacket.class, MovementPacket::encode, MovementPacket::decode,
                MovementPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SwingPacket.class, SwingPacket::encode, SwingPacket::decode,
                SwingPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, GunShotPacket.class, GunShotPacket::encode, GunShotPacket::decode,
                GunShotPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WalkerVocalPacket.class, WalkerVocalPacket::encode, WalkerVocalPacket::decode,
                WalkerVocalPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
