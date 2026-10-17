package dev.holdfast.core.client;

import dev.holdfast.core.net.HitFeedbackPacket;
import dev.holdfast.core.registry.CoreSounds;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/**
 * Cosmetic response to a server-confirmed contact: a flesh or skull sound for the contact family (separate from the
 * swing or gunfire), and a blood spatter shaped by it. Blunt: a short dark burst; slash: a spray across the swing;
 * stab: a tight squirt; gunshot: a small entry puff and an exit spray along the shot. Brain kills add a heavier
 * spray. Nothing here affects gameplay.
 */
public final class HitEffects {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.36F, 0.02F, 0.02F), 1.1F);
    private static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.18F, 0.03F, 0.02F), 1.5F);
    private static final BlockParticleOption GORE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());

    private HitEffects() {}

    public static void play(HitFeedbackPacket m) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean kill = m.outcome() == WalkerStrikeRules.Outcome.BRAIN_KILL;
        boolean skull = m.region() == BodyRegion.HEAD && kill;
        if (m.outcome() == WalkerStrikeRules.Outcome.GLANCE && m.contact() != WeaponClass.Contact.GUNSHOT) {
            mc.level.playLocalSound(m.x(), m.y(), m.z(), CoreSounds.IMPACT_BLUNT.get(), SoundSource.PLAYERS, 0.4F, 1.3F, false);
            return;
        }
        RandomSource r = mc.level.random;
        float pitch = 0.88F + r.nextFloat() * 0.24F;
        mc.level.playLocalSound(m.x(), m.y(), m.z(), CoreSounds.contact(m.contact(), skull), SoundSource.PLAYERS, 1.0F, pitch, false);
        if (net.minecraftforge.fml.ModList.get().isLoaded("extragore")) {
            ExtraGoreEffects.play(m);
            return; // One emitter: do not stack the former dust/block particles over Extra Gore.
        }
        int n;
        double spread, push;
        switch (m.contact()) {
            case BLUNT -> { n = kill ? 22 : 8; spread = 0.12; push = 0.05; }
            case SLASH -> { n = kill ? 26 : 14; spread = 0.22; push = 0.12; }
            case STAB -> { n = kill ? 16 : 6; spread = 0.05; push = 0.18; }
            default -> { n = kill ? 30 : 10; spread = 0.06; push = 0.35; }     // gunshot: exit spray along the shot
        }
        for (int i = 0; i < n; i++) {
            double sx = m.dx() * push + (r.nextDouble() - 0.5) * spread;
            double sy = m.dy() * push + r.nextDouble() * 0.08;
            double sz = m.dz() * push + (r.nextDouble() - 0.5) * spread;
            mc.level.addParticle(i % 3 == 0 ? BLOOD_DARK : BLOOD, m.x(), m.y(), m.z(), sx, sy, sz);
        }
        if (kill) {
            for (int i = 0; i < 6; i++) {
                mc.level.addParticle(GORE, m.x(), m.y(), m.z(), m.dx() * 0.2 + (r.nextDouble() - 0.5) * 0.1, 0.05, m.dz() * 0.2 + (r.nextDouble() - 0.5) * 0.1);
            }
        }
    }
}
