package dev.holdfast.core.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayClipPacketTest {
    @Test void pairedStartCarriesTheExactPartnerClipAndRestartNonce() {
        var sent = new PlayClipPacket(7, "exec_knife", 18, true, "react_knife", 42);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayClipPacket.encode(sent, buffer);
            assertEquals(sent, PlayClipPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
