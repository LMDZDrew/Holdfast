package dev.holdfast.core.gametest;

import com.mojang.authlib.GameProfile;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.MeleeSwings;
import dev.holdfast.core.combat.Strikes;
import dev.holdfast.core.rules.ContactGeometry;
import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.registry.CoreItems;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.Posture;
import dev.holdfast.core.rules.SwingRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Free melee contact timing (Andrew 2026-10-17: hits registered on click, not when the weapon lands). A Forge fake
 * player with latency 0 swings at a walker one block ahead, aiming 8 degrees up so the view ray meets the head
 * cube. The swing is started through {@link MeleeSwings#start} with a full-strength swing (a fake player doesn't
 * tick its attack-strength timer); the contact tick is the generated {@link CombatTimings#SWINGS} entry.
 */
@GameTestHolder(HoldfastCore.MODID)
@PrefixGameTestTemplate(false)
public final class MeleeGameTests {
    private MeleeGameTests() {}

    private static ServerPlayer player(GameTestHelper h, ItemStack weapon) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_swing"));
        Vec3 at = h.absoluteVec(new Vec3(3.5, 2, 1.5));
        p.moveTo(at.x, at.y, at.z, 0.0F, -8.0F);          // facing +z, eyes up toward the walker's head
        p.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        return p;
    }

    private static WalkerEntity walker(GameTestHelper h, int z) {
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new BlockPos(3, 2, z));
        w.setPosture(Posture.UPRIGHT);
        w.setNoAi(true);
        return w;
    }

    private static int contact(String clip) {
        return CombatTimings.swing(clip).contactTick();
    }

    /** No consequence at the click; the brain kill lands on the contact tick, exactly once. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void knifeSwingKillsAtContactNotClick(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.KNIFE.get()));
        WalkerEntity w = walker(h, 2);
        String clip = SwingRules.clipFor(WeaponClass.KNIFE, true);
        int damageBefore = p.getMainHandItem().getDamageValue();
        h.assertTrue(MeleeSwings.contact(p).walker() == w, "the view ray does not meet the walker");
        h.assertTrue(MeleeSwings.start(p, WeaponClass.KNIFE, clip, 1.0, 0), "swing refused");
        h.assertFalse(w.isCorpse(), "the walker died on the click");
        h.assertFalse(MeleeSwings.start(p, WeaponClass.KNIFE, clip, 1.0, 0), "a second click was accepted mid-swing");
        h.runAfterDelay(contact(clip) - 1, () -> {
            h.assertFalse(w.isCorpse(), "the walker died before contact");
            h.assertTrue(p.getMainHandItem().getDamageValue() == damageBefore, "durability spent before contact");
            h.assertTrue(MeleeSwings.pending(p), "swing resolved before its contact tick");
        });
        h.runAfterDelay(contact(clip) + 1, () -> {
            h.assertTrue(w.isCorpse(), "the knife to the head did not kill at contact");
            h.assertTrue(MeleeSwings.lastResult(p) == MeleeSwings.Result.HIT, "result " + MeleeSwings.lastResult(p));
            h.assertTrue(p.getMainHandItem().getDamageValue() == damageBefore + 1, "durability not spent once at contact");
            assertSurfaceFeedback(h,w,BodyRegion.HEAD);
        });
        h.runAfterDelay(CombatTimings.swing(clip).lengthTicks() + 2, () -> {
            h.assertTrue(p.getMainHandItem().getDamageValue() == damageBefore + 1, "the swing resolved more than once");
            h.assertTrue(MeleeSwings.start(p, WeaponClass.KNIFE, clip, 1.0, 0), "a new swing was refused after the clip");
            h.succeed();
        });
    }

    /** The walker selected at click time steps out of reach before contact: a miss, nothing applied. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void targetLeavingReachIsAMiss(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.MACHETE.get()));
        WalkerEntity w = walker(h, 2);
        String clip = SwingRules.clipFor(WeaponClass.MACHETE, true);
        h.assertTrue(MeleeSwings.start(p, WeaponClass.MACHETE, clip, 1.0, 0), "swing refused");
        h.runAfterDelay(2, () -> {
            Vec3 away = h.absoluteVec(new Vec3(3.5, 2, 5.6));
            w.moveTo(away.x, away.y, away.z, w.getYRot(), 0);
        });
        h.runAfterDelay(contact(clip) + 1, () -> {
            h.assertFalse(w.isCorpse(), "a walker out of reach was killed");
            h.assertTrue(MeleeSwings.lastResult(p) == MeleeSwings.Result.MISS, "result " + MeleeSwings.lastResult(p));
            h.succeed();
        });
    }

    /** A block between the eyes and the head at contact: no hit through it. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void blockBetweenIsObstructed(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.KNIFE.get()));
        WalkerEntity w = walker(h, 3);
        h.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        String clip = SwingRules.clipFor(WeaponClass.KNIFE, false);
        h.assertTrue(MeleeSwings.start(p, WeaponClass.KNIFE, clip, 1.0, 0), "swing refused");
        h.runAfterDelay(contact(clip) + 1, () -> {
            h.assertFalse(w.isCorpse(), "hit through a block");
            h.assertTrue(MeleeSwings.lastResult(p) == MeleeSwings.Result.OBSTRUCTED, "result " + MeleeSwings.lastResult(p));
            h.succeed();
        });
    }

    /** Switching weapon before contact cancels the swing; nothing lands later. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void weaponSwitchCancelsTheSwing(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.KNIFE.get()));
        WalkerEntity w = walker(h, 2);
        String clip = SwingRules.clipFor(WeaponClass.KNIFE, true);
        h.assertTrue(MeleeSwings.start(p, WeaponClass.KNIFE, clip, 1.0, 0), "swing refused");
        h.runAfterDelay(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        h.runAfterDelay(contact(clip) + 2, () -> {
            h.assertFalse(w.isCorpse(), "a cancelled swing killed");
            h.assertTrue(MeleeSwings.lastResult(p) == MeleeSwings.Result.CANCELLED, "result " + MeleeSwings.lastResult(p));
            h.succeed();
        });
    }

    /** A body hit staggers once; the walker never dies from it. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void bodySwingStaggersOnce(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.FIRE_AXE.get()));
        p.setXRot(25.0F);                                   // down into the torso
        WalkerEntity w = walker(h, 2);
        String clip = SwingRules.clipFor(WeaponClass.AXE, false);
        h.assertTrue(MeleeSwings.start(p, WeaponClass.AXE, clip, 1.0, 0), "swing refused");
        h.assertFalse(w.busy(), "staggered on the click");
        h.runAfterDelay(contact(clip) + 1, () -> {
            h.assertTrue(MeleeSwings.lastResult(p) == MeleeSwings.Result.HIT, "result " + MeleeSwings.lastResult(p));
            h.assertTrue(w.busy() && !w.isCorpse(), "a body hit didn't stagger, or killed");
            assertSurfaceFeedback(h,w,BodyRegion.TORSO);
            h.succeed();
        });
    }

    private static void assertSurfaceFeedback(GameTestHelper h,WalkerEntity w,BodyRegion region) {
        var packet=Strikes.lastFeedback(w).orElseThrow();
        var p=Strikes.localPoint(w,new Vec3(packet.x(),packet.y(),packet.z()));
        h.assertTrue(packet.region()==region,"feedback region does not match the confirmed contact");
        h.assertTrue(ContactGeometry.volumes(w.posture()).stream().anyMatch(v -> v.region()==region && v.box().surface(p,1e-6)),
                "feedback point is not on the matching regional surface: "+p);
    }

    @GameTest(template="arena",timeoutTicks=40)
    public static void fastBulletFeedbackStartsOnSkullSurface(GameTestHelper h) {
        WalkerEntity w=walker(h,3);
        Vec3 start=w.position().add(0,29/16.0,-4),end=start.add(0,0,8);
        var hit=Strikes.trace(w,start,end,true);
        Strikes.apply(null,w,WeaponClass.RIFLE,hit,1,0);
        assertSurfaceFeedback(h,w,BodyRegion.HEAD);
        var packet=Strikes.lastFeedback(w).orElseThrow();
        h.assertTrue(packet.hasExit(),"full skull crossing has no exit point");
        var exit=Strikes.localPoint(w,new Vec3(packet.exitX(),packet.exitY(),packet.exitZ()));
        h.assertTrue(ContactGeometry.volumes(w.posture()).get(0).box().surface(exit,1e-6),"exit is not on skull surface");
        h.assertTrue(new Vec3(packet.x(),packet.y(),packet.z()).distanceTo(start)>3,"feedback used bullet tick origin");
        h.succeed();
    }

    /** A full blunt charge released at the head kills at the heavy swing's contact, not at release. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void chargedBatKillsAtContact(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.BASEBALL_BAT.get()));
        WalkerEntity w = walker(h, 2);
        h.assertTrue(MeleeSwings.release(p, WeaponClass.BAT, 1.0), "charged release refused");
        h.assertTrue(MeleeSwings.clip(p).equals("swing_bat_heavy"), "charged clip " + MeleeSwings.clip(p));
        h.assertFalse(w.isCorpse(), "killed on release");
        int at = contact("swing_bat_heavy");
        h.runAfterDelay(at - 1, () -> h.assertFalse(w.isCorpse(), "killed before contact"));
        h.runAfterDelay(at + 1, () -> {
            h.assertTrue(w.isCorpse(), "a full charge to the head did not kill at contact");
            h.succeed();
        });
    }

    /** A partial charge only staggers, and a full charge with nothing in reach plays the overswing. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void partialChargeStaggersAndEmptyFullChargeOverswings(GameTestHelper h) {
        ServerPlayer p = player(h, new ItemStack(CoreItems.BASEBALL_BAT.get()));
        WalkerEntity w = walker(h, 2);
        h.assertTrue(MeleeSwings.release(p, WeaponClass.BAT, 0.5), "partial release refused");
        h.assertTrue(MeleeSwings.clip(p).equals("swing_bat"), "partial clip " + MeleeSwings.clip(p));
        ServerPlayer q = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_swing_air"));
        Vec3 corner = h.absoluteVec(new Vec3(0.5, 2, 6.5));
        q.moveTo(corner.x, corner.y, corner.z, 0.0F, 0.0F);     // facing out of the arena (+z)
        q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreItems.BASEBALL_BAT.get()));
        h.assertTrue(MeleeSwings.release(q, WeaponClass.BAT, 1.0), "empty release refused");
        h.assertTrue(MeleeSwings.clip(q).equals("bat_charged_miss"), "overswing clip " + MeleeSwings.clip(q));
        h.runAfterDelay(contact("swing_bat") + 1, () -> {
            h.assertTrue(w.busy() && !w.isCorpse(), "a partial charge to the head didn't just stagger");
            h.succeed();
        });
    }
}
