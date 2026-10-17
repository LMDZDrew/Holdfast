package dev.holdfast.core.gametest;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.Strikes;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.Posture;
import dev.holdfast.core.rules.WalkerStrikeRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Automated server checks (roadmap Stage 1 exit gate, item 5): run with {@code gradlew runGameTestServer}. They spawn
 * real walkers on a real server and drive the same code paths as player hits, without a client.
 */
@GameTestHolder(HoldfastCore.MODID)
@PrefixGameTestTemplate(false)
public final class WalkerGameTests {
    private WalkerGameTests() {}

    private static WalkerEntity walker(GameTestHelper h, Posture posture) {
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new BlockPos(3, 2, 3));
        w.setPosture(posture);
        w.setNoAi(true);
        return w;
    }

    private static Vec3 at(WalkerEntity w, double upPx) {
        return w.position().add(0, upPx / 16.0, 0);
    }

    @GameTest(template = "arena")
    public static void bodyHitsNeverKill(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT);
        for (int i = 0; i < 25; i++) {
            Strikes.apply(null, w, WeaponClass.AXE, at(w, 18), BodyRegion.TORSO, 1.0, 1.0);
            Strikes.apply(null, w, WeaponClass.RIFLE, at(w, 6), BodyRegion.LEG, 1.0, 0);
        }
        h.assertTrue(w.isAlive() && !w.isCorpse(), "a walker died from body hits");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void headHitWithBladeKills(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT);
        h.assertTrue(Strikes.region(w, at(w, 29)) == BodyRegion.HEAD, "29 px up is not the head");
        var out = Strikes.apply(null, w, WeaponClass.KNIFE, at(w, 29), BodyRegion.HEAD, 1.0, 0);
        h.assertTrue(out == WalkerStrikeRules.Outcome.BRAIN_KILL && w.isCorpse(), "knife to the head did not kill");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void unchargedBluntToTheHeadOnlyStaggers(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT);
        var out = Strikes.apply(null, w, WeaponClass.BAT, at(w, 29), BodyRegion.HEAD, 1.0, 0.3);
        h.assertTrue(out == WalkerStrikeRules.Outcome.STAGGER && !w.isCorpse(), "an uncharged bat killed");
        Strikes.apply(null, w, WeaponClass.BAT, at(w, 29), BodyRegion.HEAD, 1.0, 1.0);
        h.assertTrue(w.isCorpse(), "a fully charged bat to the head did not kill");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void vanillaDamageIsIgnored(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT);
        w.hurt(h.getLevel().damageSources().generic(), 1000.0F);
        w.hurt(h.getLevel().damageSources().inFire(), 1000.0F);
        w.hurt(h.getLevel().damageSources().fall(), 1000.0F);
        h.assertTrue(w.isAlive() && !w.isCorpse(), "ordinary damage killed a walker");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void crawlerSkullIsLowAndForward(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.CRAWLER);
        double yaw = Math.toRadians(w.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 skull = w.position().add(forward.scale(9.0 / 16.0)).add(0, 4.0 / 16.0, 0);
        h.assertTrue(Strikes.region(w, skull) == BodyRegion.HEAD, "crawler skull not found in front");
        h.assertTrue(Strikes.region(w, w.position().add(forward.scale(-10.0 / 16.0)).add(0, 0.25, 0)) == BodyRegion.TORSO,
                "crawler body behind not torso");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void corpseAndPostureSurviveSaveAndLoad(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.PRONE);
        w.setDormant(true);
        CompoundTag alive = new CompoundTag();
        w.saveWithoutId(alive);
        WalkerEntity copy = CoreEntities.WALKER.get().create(h.getLevel());
        h.assertTrue(copy != null, "could not create a walker");
        copy.load(alive);
        h.assertTrue(copy.posture() == Posture.PRONE && copy.dormant(), "posture or dormancy lost on reload");
        w.brainKill(null, 0);
        CompoundTag dead = new CompoundTag();
        w.saveWithoutId(dead);
        WalkerEntity corpse = CoreEntities.WALKER.get().create(h.getLevel());
        corpse.load(dead);
        h.assertTrue(corpse.isCorpse(), "corpse lost on reload");
        h.succeed();
    }

    // ------------------------------------------------------------ corpse grounding (Andrew 2026-10-17: deaths floated)

    /** Feet height above the arena floor (its stone is placed at structure y 1, top at y 2), blocks. */
    static double aboveFloor(GameTestHelper h, WalkerEntity w) {
        return w.getY() - h.absoluteVec(new Vec3(0, 2, 0)).y;
    }

    static void assertGrounded(GameTestHelper h, WalkerEntity w, String what) {
        double up = aboveFloor(h, w);
        h.assertTrue(Math.abs(up) < 1.0E-3 && w.onGround(), what + " corpse is " + String.format(java.util.Locale.ROOT, "%.3f", up)
                + " blocks above the floor (onGround " + w.onGround() + ", y " + w.getY() + ", origin " + h.absoluteVec(Vec3.ZERO).y
                + ", below " + h.getLevel().getBlockState(w.blockPosition().below()) + ")");
        h.assertFalse(w.isNoAi(), what + " corpse kept NoAI, which disables gravity");
    }

    /** An ordinary brain kill after the walker has ticked (vanilla's grow-lift only applies then) leaves it on the floor. */
    @GameTest(template = "arena", timeoutTicks = 80)
    public static void brainKilledCorpsesLieOnTheFloor(GameTestHelper h) {
        Posture[] postures = {Posture.UPRIGHT, Posture.PRONE, Posture.CRAWLER};
        WalkerEntity[] ws = new WalkerEntity[3];
        for (int i = 0; i < 3; i++) {
            ws[i] = h.spawn(CoreEntities.WALKER.get(), new BlockPos(1 + 2 * i, 2, 3));
            ws[i].setPosture(postures[i]);
            ws[i].setNoAi(true);
        }
        h.runAfterDelay(5, () -> {
            for (WalkerEntity w : ws) {
                double head = w.posture() == Posture.UPRIGHT ? 29 : 4;
                Strikes.apply(null, w, WeaponClass.KNIFE, at(w, head), BodyRegion.HEAD, 1.0, 0);
                h.assertTrue(w.isCorpse(), w.posture() + " head hit did not kill");
            }
        });
        h.runAfterDelay(30, () -> {
            for (WalkerEntity w : ws) assertGrounded(h, w, w.posture().name());
            h.succeed();
        });
    }

    /** A walker killed in the air (knocked off a ledge, NoAI hovering) falls and lies on the floor. */
    @GameTest(template = "arena", timeoutTicks = 80)
    public static void corpseKilledInTheAirFalls(GameTestHelper h) {
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new Vec3(3.5, 3.6, 3.5));
        w.setNoAi(true);
        h.runAfterDelay(3, () -> w.brainKill(null, 0));
        h.runAfterDelay(50, () -> {
            assertGrounded(h, w, "airborne");
            h.succeed();
        });
    }

    /** A corpse saved by the earlier build (lifted, NoAI) settles after loading; the reloaded state is still a corpse. */
    @GameTest(template = "arena", timeoutTicks = 80)
    public static void savedFloatingCorpseSettlesOnLoad(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT);
        w.brainKill(null, 0);
        CompoundTag tag = new CompoundTag();
        w.saveWithoutId(tag);
        tag.putBoolean("NoAI", true);
        w.discard();
        WalkerEntity copy = CoreEntities.WALKER.get().create(h.getLevel());
        h.assertTrue(copy != null, "could not create a walker");
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        Vec3 lifted = h.absoluteVec(new Vec3(3.5, 2.72, 3.5));
        copy.moveTo(lifted.x, lifted.y, lifted.z, 0, 0);
        h.getLevel().addFreshEntity(copy);
        h.runAfterDelay(40, () -> {
            h.assertTrue(copy.isCorpse(), "corpse lost on reload");
            assertGrounded(h, copy, "reloaded");
            h.succeed();
        });
    }
}
