package dev.holdfast.core.gametest;

import com.mojang.authlib.GameProfile;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.*;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.registry.*;
import dev.holdfast.core.rules.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(HoldfastCore.MODID)
@PrefixGameTestTemplate(false)
public final class DawnGameTests {
    private static ServerPlayer player(GameTestHelper h, ItemStack item) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_dawn"));
        var at = h.absoluteVec(new Vec3(3.5, 2, 1.5));
        p.moveTo(at.x, at.y, at.z, 0, -8);
        p.setItemInHand(InteractionHand.MAIN_HAND, item);
        return p;
    }
    private static WalkerEntity walker(GameTestHelper h) {
        var w = h.spawn(CoreEntities.WALKER.get(), new BlockPos(3, 2, 2));
        w.setNoAi(true);
        return w;
    }

    @GameTest(template="arena", timeoutTicks=40)
    public static void corpseCannotStartOrResumeAVoice(GameTestHelper h) {
        var w = walker(h);
        h.assertTrue(w.vocal(CoreSounds.WALKER_GROAN.get(), 1, 1), "living voice refused");
        w.brainKill(null, 0);
        h.assertFalse(w.vocal(CoreSounds.WALKER_SNARL.get(), 1, 1), "corpse started a voice");
        var saved = new net.minecraft.nbt.CompoundTag();
        w.saveWithoutId(saved);
        w.readAdditionalSaveData(saved);
        h.assertFalse(w.vocal(CoreSounds.WALKER_GROAN.get(), 1, 1), "loaded corpse resumed a voice");
        h.succeed();
    }

    private static void continuedGrab(GameTestHelper h, Posture posture, GrabRules.Kind kind, String seize, String hold) {
        var p = player(h, ItemStack.EMPTY);
        var w = walker(h);
        w.setPosture(posture);
        w.moveTo(p.getX(), p.getY(), p.getZ()+.75, 180, 0);
        w.playFull(seize, 1); // a nearly expired lunge must stay owned until the paired hold is installed
        int nonce = w.clipNonce();
        double distance = w.distanceTo(p);
        h.assertTrue(Grabs.startFromLunge(w, p, kind), "continuation refused");
        h.assertTrue(w.clipNonce()==nonce && w.fullClip().equals(seize), "seize restarted on first grip");
        h.assertTrue(w.distanceTo(p)<=distance+1e-5, "walker retreated to the pairing distance");
        h.runAfterDelay(2, () -> h.assertTrue(w.fullClip().equals(seize) && w.clipNonce()==nonce,
                "seize timed out to gait before hold handoff"));
        h.runAfterDelay(kind == GrabRules.Kind.UPRIGHT ? 5 : 11, () -> {
            h.assertTrue(w.fullClip().equals(hold), "hold not reached continuously: "+w.fullClip());
            h.assertTrue(Grabs.isGrabbed(p), "QTE ownership lost");
            Grabs.escape(p);
            h.succeed();
        });
    }
    @GameTest(template="arena", timeoutTicks=60)
    public static void uprightGripContinuesWithoutRestartOrRetreat(GameTestHelper h) {
        continuedGrab(h, Posture.UPRIGHT, GrabRules.Kind.UPRIGHT, "grab_seize", "grab_hold");
    }
    @GameTest(template="arena", timeoutTicks=60)
    public static void crawlerGripContinuesWithoutRestartOrRetreat(GameTestHelper h) {
        continuedGrab(h, Posture.CRAWLER, GrabRules.Kind.CRAWLER_ANKLE, "crawler_lunge", "crawler_ankle_hold");
    }

    @GameTest(template="arena", timeoutTicks=50)
    public static void grabCancelsOldSwingWithoutReplacingSeize(GameTestHelper h) {
        var p = player(h, new ItemStack(CoreItems.KNIFE.get()));
        var w = walker(h);
        h.assertTrue(MeleeSwings.start(p, WeaponClass.KNIFE, "swing_knife_heavy", 1, 0), "swing refused");
        h.assertTrue(Grabs.start(w, p, GrabRules.Kind.UPRIGHT), "grab refused");
        int nonce = w.clipNonce();
        MeleeSwings.tick(h.getLevel().getServer());
        h.assertFalse(MeleeSwings.pending(p), "old strike still pending");
        h.assertTrue(MeleeSwings.lastResult(p)==MeleeSwings.Result.CANCELLED, "not cancelled");
        h.assertTrue(w.clipNonce()==nonce && w.fullClip().equals("grab_seize"), "seize replaced");
        h.assertFalse(w.isCorpse(), "cancelled strike killed walker");
        Grabs.abandon(p);
        h.succeed();
    }

    @GameTest(template="arena", timeoutTicks=40)
    public static void twoHandedExecutionRefusesOccupiedOffhand(GameTestHelper h) {
        var p = player(h, new ItemStack(CoreItems.FIRE_AXE.get()));
        var w = walker(h);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        Executions.request(p, w);
        h.assertFalse(Executions.isExecuting(p), "two-handed execution with occupied offhand");
        h.assertTrue(Executions.lastRefusal(p).orElse(null)==ExecutionRules.Refusal.OFF_HAND_OCCUPIED, "wrong refusal");
        h.assertFalse(w.isCorpse(), "refusal killed target");
        h.succeed();
    }

    @GameTest(template="arena", timeoutTicks=60)
    public static void punchWaitsForWoundUpContact(GameTestHelper h) {
        var p = player(h, ItemStack.EMPTY);
        var w = walker(h);
        String clip = "swing_unarmed_heavy";
        int contact = CombatTimings.swing(clip).contactTick();
        h.assertTrue(contact >= 6, "punch has no readable preparation");
        h.assertTrue(MeleeSwings.start(p, WeaponClass.NONE, clip, 1, 0), "punch refused");
        h.runAfterDelay(contact-1, () -> h.assertTrue(MeleeSwings.lastResult(p)==MeleeSwings.Result.PENDING, "punch resolved before contact"));
        h.runAfterDelay(contact+1, () -> {
            h.assertTrue(MeleeSwings.lastResult(p)==MeleeSwings.Result.HIT, "punch missed contact");
            h.assertFalse(w.isCorpse(), "uncharged fist caused a brain kill");
            h.succeed();
        });
    }
}
