package dev.holdfast.core.net;

import dev.holdfast.core.rules.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HitFeedbackPacketTest {
    @Test void entryOnlyAndEntryExitRoundTripWithoutCoordinateLoss() {
        var entry=HitFeedbackPacket.of(7,new Vec3(21.25,3.5,-17.75),new Vec3(0,0,1),
                WeaponClass.Contact.GUNSHOT,BodyRegion.HEAD,WalkerStrikeRules.Outcome.BRAIN_KILL);
        for(var sent:new HitFeedbackPacket[]{entry,entry.withExit(new Vec3(21.25,3.5,-17.25))}) {
            var bytes=new FriendlyByteBuf(Unpooled.buffer());
            try {HitFeedbackPacket.encode(sent,bytes);assertEquals(sent,HitFeedbackPacket.decode(bytes));assertEquals(0,bytes.readableBytes());}
            finally {bytes.release();}
        }
    }
}
