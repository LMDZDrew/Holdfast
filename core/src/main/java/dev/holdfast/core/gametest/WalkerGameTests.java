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
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new BlockPos(3, 1, 3));
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
}
