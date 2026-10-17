package dev.holdfast.core.client;

import com.eruannie_9.extragore.event.BloodOnHitHandler;
import com.eruannie_9.extragore.json.BloodAmalgamationPolicy;
import com.eruannie_9.extragore.json.BloodBrightnessMode;
import com.eruannie_9.extragore.json.BloodEntityConfig;
import com.eruannie_9.extragore.json.BloodStyle;
import com.eruannie_9.extragore.particle.ParticleBloodFactory;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.net.HitFeedbackPacket;
import dev.holdfast.core.rules.WalkerStrikeRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

/** Optional Extra Gore 5.3 renderer. Called only on the client, after the server confirms contact. */
public final class ExtraGoreEffects {
    private static boolean initialized;
    private static int diagnosticBursts;
    private static final BloodEntityConfig.Values BLOOD = new BloodEntityConfig.Values(
            "default", 0x650B0C, BloodStyle.HEAVY, BloodAmalgamationPolicy.GROUND,
            BloodBrightnessMode.WORLD, 0.045F, 0.10F, 400, 700, 18, 0.12F, 0.65F);

    private ExtraGoreEffects() {}

    public static void initialize() {
        if (initialized) return;
        // Extra Gore's health-change/click heuristics cannot judge Holdfast walker contact. The renderer remains
        // registered; only its automatic emitter is removed. Non-walkers receive core confirmed-hit packets too.
        MinecraftForge.EVENT_BUS.unregister(BloodOnHitHandler.getInstance());
        var capacity = com.eruannie_9.extragore.ModConfigurationClient.MAXIMUM_BLOOD_PARTICLES;
        if (capacity.get() > 1800) capacity.set(1800);
        if (!initialized) HoldfastCore.LOG.info("[holdfast] Extra Gore 5.3: confirmed-contact emitter enabled; automatic emitter disabled");
        initialized = true;
    }

    public static void play(HitFeedbackPacket hit) {
        initialize();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || mc.player.distanceToSqr(hit.x(), hit.y(), hit.z()) > 48 * 48) return;
        boolean kill = hit.outcome() == WalkerStrikeRules.Outcome.BRAIN_KILL;
        int count = switch (hit.contact()) {
            case BLUNT -> kill ? 24 : 8;
            case SLASH -> kill ? 32 : 18;
            case STAB -> kill ? 18 : 7;
            case GUNSHOT -> kill ? 36 : 14;
        };
        Vec3 forward = new Vec3(hit.dx(), hit.dy(), hit.dz());
        if (forward.lengthSqr() < 1e-6) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();
        Vec3 side = forward.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        var random = mc.level.random;
        int accepted = 0;
        for (int i = 0; i < count; i++) {
            double force = switch (hit.contact()) {
                case BLUNT -> 0.055;
                case SLASH -> 0.11;
                case STAB -> 0.14;
                case GUNSHOT -> 0.23;
            };
            double width = hit.contact() == dev.holdfast.core.rules.WeaponClass.Contact.SLASH ? 0.18 : 0.06;
            Vec3 velocity = forward.scale(force * (0.4 + random.nextDouble()))
                    .add(side.scale((random.nextDouble() - 0.5) * width))
                    .add(0, 0.015 + random.nextDouble() * 0.07, 0);
            var particle = ParticleBloodFactory.createParticle(mc.level,
                    hit.x(), hit.y(), hit.z(), velocity.x, velocity.y, velocity.z,
                    i % 2, BLOOD, BLOOD.scaleMin(), BLOOD.scaleMax());
            if (particle != null) {
                mc.particleEngine.add(particle);
                accepted++;
            }
        }
        if (diagnosticBursts++ < 12) HoldfastCore.LOG.info("[holdfast] Extra Gore contact {} {}: {}/{} particles at {},{},{}",
                hit.contact(), hit.outcome(), accepted, count, hit.x(), hit.y(), hit.z());
    }
}
