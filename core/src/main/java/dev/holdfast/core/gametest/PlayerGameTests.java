package dev.holdfast.core.gametest;

import dev.holdfast.core.CoreConfig;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.Executions;
import dev.holdfast.core.combat.Grabs;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.item.BiteGuardItem;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.registry.CoreItems;
import dev.holdfast.core.rules.GrabRules;
import dev.holdfast.core.rules.InjuryRules;
import dev.holdfast.core.rules.Posture;
import dev.holdfast.core.state.ConditionCapability;
import dev.holdfast.core.state.ConditionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Grab, bite, execution and injury checks with a Forge {@link FakePlayer} (survival, no client; its connection drops
 * packets). Vanilla's mock server player can't be used: it fails Forge's network handshake in 1.20.1. Timing is the
 * server's tick count, exactly as in play.
 */
@GameTestHolder(HoldfastCore.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerGameTests {
    private PlayerGameTests() {}

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_test"));
        Vec3 at = h.absoluteVec(new Vec3(3.5, 2, 1.5));
        p.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);       // facing +z, toward the walker
        return p;
    }

    private static WalkerEntity walker(GameTestHelper h) {
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new BlockPos(3, 2, 2));
        w.setPosture(Posture.UPRIGHT);
        w.setNoAi(true);
        return w;
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void escapeInsideTheWindowShovesFree(GameTestHelper h) {
        ServerPlayer p = player(h);
        WalkerEntity w = walker(h);
        h.assertTrue(Grabs.start(w, p, GrabRules.Kind.UPRIGHT), "grab did not start");
        h.assertTrue(Grabs.isGrabbed(p), "not grabbed");
        h.runAfterDelay(CoreConfig.grabWindowTicks() / 2, () -> {
            Grabs.escape(p);
            h.assertFalse(Grabs.isGrabbed(p), "escape inside the window was refused");
            h.assertFalse(ConditionCapability.of(p).infected(), "escaped but infected");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void collarBlocksThenBiteInfects(GameTestHelper h) {
        ServerPlayer p = player(h);
        WalkerEntity w = walker(h);
        ItemStack collar = new ItemStack(CoreItems.PADDED_COLLAR.get());   // padded leather: stops 2 bites
        p.setItemSlot(EquipmentSlot.HEAD, collar);
        h.assertTrue(Grabs.start(w, p, GrabRules.Kind.UPRIGHT), "grab did not start");
        int window = CoreConfig.grabWindowTicks();
        // two blocked bites (the second breaks it), each restarting the window, then a penetrating bite
        h.runAfterDelay(window + 2, () -> {
            h.assertTrue(Grabs.isGrabbed(p), "a blocked bite released the grab");
            h.assertFalse(ConditionCapability.of(p).infected(), "the collar didn't block the first bite");
        });
        h.runAfterDelay(2 * window + 4, () -> {
            h.assertFalse(ConditionCapability.of(p).infected(), "the breaking bite got through");
            h.assertTrue(BiteGuardItem.condition(p.getItemBySlot(EquipmentSlot.HEAD)) == 0, "collar not used up");
        });
        h.runAfterDelay(3 * window + 8, () -> {
            h.assertTrue(ConditionCapability.of(p).infected(), "a bite through a ruined collar didn't infect");
            h.assertFalse(Grabs.isGrabbed(p), "still grabbed after a penetrating bite");
            h.assertTrue(ConditionCapability.of(p).blockedBites() == 2, "expected 2 blocked bites");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void knifeExecutionKillsOnItsTick(GameTestHelper h) {
        ServerPlayer p = player(h);
        WalkerEntity w = walker(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(p, w);
        h.assertTrue(Executions.isExecuting(p), "a valid knife execution was refused");
        h.runAfterDelay(30, () -> {
            h.assertTrue(w.isCorpse(), "the execution didn't kill at its tick");
            h.assertFalse(Executions.isExecuting(p), "execution still running after the clip");
            WalkerGameTests.assertGrounded(h, w, "executed");
            h.succeed();
        });
    }

    /** Ground executions (prone and crawler targets) kill and leave the corpse on the floor. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void groundExecutionCorpsesLieOnTheFloor(GameTestHelper h) {
        Posture[] postures = {Posture.PRONE, Posture.CRAWLER};
        WalkerEntity[] ws = new WalkerEntity[2];
        for (int i = 0; i < 2; i++) {
            ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_test" + i));
            Vec3 at = h.absoluteVec(new Vec3(1.5 + 4 * i, 2, 0.5));
            p.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreItems.KNIFE.get()));
            double d = dev.holdfast.core.rules.ExecutionRules.clipFor(dev.holdfast.core.rules.WeaponClass.KNIFE, postures[i])
                    .orElseThrow().distPx() / 16.0;
            WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new Vec3(1.5 + 4 * i, 2, 0.5 + d));
            w.setPosture(postures[i]);
            w.setNoAi(true);
            w.moveTo(w.getX(), w.getY(), w.getZ(), 180.0F, 0.0F);   // head toward the player (not over its body)
            w.setYBodyRot(180.0F);
            ws[i] = w;
            final int n = i;
            h.runAfterDelay(5, () -> {
                Executions.request(p, ws[n]);
                h.assertTrue(Executions.isExecuting(p), postures[n] + " knife execution was refused");
            });
        }
        h.runAfterDelay(60, () -> {
            for (WalkerEntity w : ws) {
                h.assertTrue(w.isCorpse(), w.posture() + " execution did not kill");
                WalkerGameTests.assertGrounded(h, w, w.posture() + " executed");
            }
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void emptyHandsCannotExecute(GameTestHelper h) {
        ServerPlayer p = player(h);
        WalkerEntity w = walker(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Executions.request(p, w);
        h.assertFalse(Executions.isExecuting(p), "executed with empty hands");
        h.assertFalse(w.isCorpse(), "walker died");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void injurySetsTheServerSpeed(GameTestHelper h) {
        ServerPlayer p = player(h);
        double base = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        var c = ConditionCapability.of(p);
        c.setMovement(InjuryRules.Movement.LIMP_SEVERE);
        ConditionEvents.applySpeed(p, c);
        double limping = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        h.assertTrue(Math.abs(limping / base - 0.55) < 1e-6, "severe limp speed was " + limping / base);
        c.setMovement(InjuryRules.Movement.NORMAL);
        ConditionEvents.applySpeed(p, c);
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1e-9, "speed not restored");
        h.succeed();
    }
}
