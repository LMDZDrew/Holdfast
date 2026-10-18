package dev.holdfast.core.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentationPacketTest {
    @Test void delayedSwingStopCannotClearNewGrabOrExecution() {
        var stop = PlayClipPacket.stopSwing(4, "swing_knife");
        assertTrue(stop.stops("swing_knife"));
        assertFalse(stop.stops("grabbed_seize"));
        assertFalse(stop.stops("grabbed_shove"));
        assertFalse(stop.stops("exec_katana"));
        assertFalse(stop.stops(null));
        assertTrue(PlayClipPacket.stop(4).stops("exec_katana"));
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayClipPacket.encode(stop, bytes);
            assertEquals(stop, PlayClipPacket.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }

    @Test void entityOwnedVoiceRetainsIdentityAndSound() {
        var voice = new WalkerVocalPacket(37, new ResourceLocation("holdfast_core", "walker.snarl"), .9F, 1.05F);
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            WalkerVocalPacket.encode(voice, bytes);
            assertEquals(voice, WalkerVocalPacket.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }
}
