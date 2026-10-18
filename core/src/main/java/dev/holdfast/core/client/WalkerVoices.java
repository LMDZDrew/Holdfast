package dev.holdfast.core.client;

import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.net.WalkerVocalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.HashMap;
import java.util.Map;

/** At most one interruptible voice per walker. Corpses and unloaded entities are silent. */
public final class WalkerVoices {
    private static final Map<Integer, Voice> VOICES = new HashMap<>();
    private WalkerVoices() {}

    public static void play(WalkerVocalPacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.entityId()) instanceof WalkerEntity walker)
                || walker.isCorpse() || !walker.isAlive() || walker.isSilent()) return;
        Voice previous = VOICES.remove(packet.entityId());
        if (previous != null) previous.end();
        Voice next = new Voice(walker, packet);
        VOICES.put(packet.entityId(), next);
        mc.getSoundManager().play(next);
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        VOICES.values().removeIf(v -> {
            boolean ended = mc.level != v.walker.level() || !v.walker.isAlive() || v.walker.isCorpse()
                    || v.walker.isSilent() || (mc.level.getGameTime() - v.startedAt > 40 && !mc.getSoundManager().isActive(v));
            if (ended) v.end();
            return ended;
        });
    }

    private static final class Voice extends AbstractTickableSoundInstance {
        final WalkerEntity walker;
        final long startedAt;
        Voice(WalkerEntity walker, WalkerVocalPacket packet) {
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(packet.sound()), SoundSource.HOSTILE, RandomSource.create());
            this.walker = walker;
            startedAt = walker.level().getGameTime();
            volume = packet.volume(); pitch = packet.pitch();
            x = walker.getX(); y = walker.getEyeY(); z = walker.getZ();
        }
        void end() { stop(); }
        @Override public void tick() {
            if (walker.isRemoved() || walker.isCorpse() || !walker.isAlive() || walker.isSilent()) { stop(); return; }
            x = walker.getX(); y = walker.getEyeY(); z = walker.getZ();
        }
    }
}
