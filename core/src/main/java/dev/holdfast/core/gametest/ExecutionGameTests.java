package dev.holdfast.core.gametest;

import com.mojang.authlib.GameProfile;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.Executions;
import dev.holdfast.core.combat.Strikes;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.ContactGeometry;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.registry.CoreItems;
import dev.holdfast.core.rules.ExecutionRules;
import dev.holdfast.core.rules.Posture;
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
 * Executions from any approach (Andrew 2026-10-17: front, rear, either side, diagonals) with the server's world
 * checks. The walker never moves: only its yaw may change (upright, at most ~45 degrees); the player steps to the
 * clip's slot along their own approach line, and is refused when the step is blocked or there is no room.
 */
@GameTestHolder(HoldfastCore.MODID)
@PrefixGameTestTemplate(false)
public final class ExecutionGameTests {
    private ExecutionGameTests() {}

    private static final Vec3 CENTRE = new Vec3(3.5, 2, 3.5);

    private static WalkerEntity walker(GameTestHelper h, Posture posture, float yaw) {
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), CENTRE);
        w.setPosture(posture);
        w.setNoAi(true);
        Vec3 at = h.absoluteVec(CENTRE);
        w.moveTo(at.x, at.y, at.z, yaw, 0);
        w.setYBodyRot(yaw);
        w.setYHeadRot(yaw);
        return w;
    }

    /** A fake player {@code dist} blocks from {@code target}, {@code bearing} degrees clockwise (toward its right) from the walker facing. */
    private static ServerPlayer player(GameTestHelper h, Vec3 target, float walkerYaw, double bearing, double dist, ItemStack held) {
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_exec"));
        double r = Math.toRadians(walkerYaw + bearing);
        double x = target.x - Math.sin(r) * dist, z = target.z + Math.cos(r) * dist;
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - x), target.z - z));
        p.moveTo(x, target.y, z, yaw, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p;
    }

    private static double wrap(double d) {
        d %= 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    private static void approachKills(GameTestHelper h, double bearing, ExecutionRules.Approach expected) {
        float yaw = 20;
        WalkerEntity w = walker(h, Posture.UPRIGHT, yaw);
        Vec3 before = w.position();
        ServerPlayer p = player(h, before, yaw, bearing, 1.5, new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(p, w);
        h.assertTrue(Executions.isExecuting(p), "refused from " + bearing + " deg: " + Executions.lastRefusal(p));
        h.assertTrue(Executions.lastApproach(p).orElseThrow() == expected, "approach " + Executions.lastApproach(p));
        h.assertTrue(w.position().distanceTo(before) < 1e-9, "the walker was moved");
        h.assertTrue(Math.abs(wrap(w.getYRot() - yaw)) <= 55, "walker turned " + wrap(w.getYRot() - yaw));
        h.assertTrue(p.position().distanceTo(before) < 1.6, "player slot too far from the walker");
        h.runAfterDelay(40, () -> {
            h.assertTrue(w.isCorpse(), "no kill from " + bearing + " deg");
            assertContact(h,w,"react_knife"+expected.suffix);
            h.assertFalse(Executions.isExecuting(p), "still executing after the clip");
            Executions.request(p, w);
            h.assertFalse(Executions.isExecuting(p), "a corpse was executed again");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void frontApproach(GameTestHelper h) { approachKills(h, 0, ExecutionRules.Approach.FRONT); }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void rearApproach(GameTestHelper h) { approachKills(h, 180, ExecutionRules.Approach.REAR); }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void leftApproach(GameTestHelper h) { approachKills(h, -90, ExecutionRules.Approach.LEFT); }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void rightApproach(GameTestHelper h) { approachKills(h, 90, ExecutionRules.Approach.RIGHT); }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void rearDiagonalApproach(GameTestHelper h) { approachKills(h, 150, ExecutionRules.Approach.REAR); }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void frontDiagonalApproach(GameTestHelper h) { approachKills(h, -40, ExecutionRules.Approach.FRONT); }

    /** A wall between the player and the walker: refused, nothing moves, the walker lives. */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void wallBetweenIsObstructed(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT, 0);
        ServerPlayer p = player(h, w.position(), 0, 0, 2.2, new ItemStack(CoreItems.KNIFE.get()));
        h.setBlock(new BlockPos(3, 2, 5), Blocks.STONE);
        h.setBlock(new BlockPos(3, 3, 5), Blocks.STONE);
        Vec3 was = p.position();
        Executions.request(p, w);
        h.assertFalse(Executions.isExecuting(p), "executed through a wall");
        h.assertTrue(Executions.lastRefusal(p).orElse(null) == ExecutionRules.Refusal.OBSTRUCTED
                || Executions.lastRefusal(p).orElse(null) == ExecutionRules.Refusal.NO_ROOM, "refusal " + Executions.lastRefusal(p));
        h.assertTrue(p.position().distanceTo(was) < 1e-9, "the refused player was moved");
        h.assertFalse(w.isCorpse(), "walker died");
        h.succeed();
    }

    /** The slot is inside a block (the player cannot stand there): refused. */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void noRoomAtTheSlot(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT, 0);
        ServerPlayer p = player(h, w.position(), 0, 0, 1.0, new ItemStack(CoreItems.KNIFE.get()));
        h.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);       // where the 1.1-block knife slot puts the player's feet
        h.setBlock(new BlockPos(3, 3, 4), Blocks.STONE);
        Executions.request(p, w);
        h.assertFalse(Executions.isExecuting(p), "executed with no room for the player");
        h.assertTrue(Executions.lastRefusal(p).isPresent(), "no refusal recorded");
        h.succeed();
    }

    /** A lying walker: fine from beside its head, refused from over its body; it never turns or moves. */
    @GameTest(template = "arena", timeoutTicks = 80)
    public static void lyingWalkerFromItsSideNotOverItsBody(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.PRONE, 0);
        Vec3 before = w.position();
        ServerPlayer over = player(h, before, 0, 180, 1.2, new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(over, w);
        h.assertFalse(Executions.isExecuting(over), "executed from over the body");
        h.assertTrue(Executions.lastRefusal(over).orElse(null) == ExecutionRules.Refusal.NO_ROOM, "refusal " + Executions.lastRefusal(over));
        over.discard();
        ServerPlayer side = player(h, before.add(0, 0, 13 / 16.0), 0, 70, 1.4, new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(side, w);
        h.assertTrue(Executions.isExecuting(side), "refused from beside the head: " + Executions.lastRefusal(side));
        h.assertTrue(w.position().distanceTo(before) < 1e-9 && Math.abs(wrap(w.getYRot())) < 1e-6, "lying walker moved or turned");
        h.runAfterDelay(45, () -> {
            h.assertTrue(w.isCorpse(), "no kill from the side");
            assertContact(h,w,"react_knife_ground_prone");
            h.succeed();
        });
    }

    private static void assertContact(GameTestHelper h,WalkerEntity w,String reaction) {
        var source=CombatTimings.EXEC_CONTACT.get(reaction);
        Vec3 expected=Strikes.world(w,new ContactGeometry.Point(source.x(),source.y(),source.z()));
        var packet=Strikes.lastFeedback(w).orElseThrow();
        Vec3 actual=new Vec3(packet.x(),packet.y(),packet.z());
        h.assertTrue(actual.distanceTo(expected)<1/16.0,"execution feedback missed generated surface by over 1 px: "+reaction);
        h.assertTrue(actual.distanceTo(w.position().add(0,w.posture().onGround()?.3:1.8,0))>.01,
                "execution used the old fixed entity-centre blood point");
    }

    @GameTest(template="arena",timeoutTicks=80)
    public static void crawlerExecutionBloodUsesSkullEntryNotHips(GameTestHelper h) {
        WalkerEntity w=walker(h,Posture.CRAWLER,0);
        ServerPlayer p=player(h,w.position().add(0,0,9/16.0),0,0,1.4,new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(p,w);
        h.assertTrue(Executions.isExecuting(p),"crawler execution refused: "+Executions.lastRefusal(p));
        h.runAfterDelay(45,() -> {
            h.assertTrue(w.isCorpse(),"crawler was not executed");
            assertContact(h,w,"react_knife_ground");
            h.succeed();
        });
    }

    /** Weapon gate and exclusive ownership still hold from any side. */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void gatesStillApply(GameTestHelper h) {
        WalkerEntity w = walker(h, Posture.UPRIGHT, 0);
        ServerPlayer bare = player(h, w.position(), 0, 180, 1.5, ItemStack.EMPTY);
        Executions.request(bare, w);
        h.assertTrue(Executions.lastRefusal(bare).orElse(null) == ExecutionRules.Refusal.WEAPON_CANNOT_EXECUTE, "empty hands: " + Executions.lastRefusal(bare));
        ServerPlayer first = player(h, w.position(), 0, 90, 1.5, new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(first, w);
        h.assertTrue(Executions.isExecuting(first), "first request refused: " + Executions.lastRefusal(first));
        ServerPlayer second = player(h, w.position(), 0, -90, 1.5, new ItemStack(CoreItems.KNIFE.get()));
        Executions.request(second, w);
        h.assertFalse(Executions.isExecuting(second), "two players executed one walker");
        h.assertTrue(Executions.lastRefusal(second).orElse(null) == ExecutionRules.Refusal.ALREADY_EXECUTED, "refusal " + Executions.lastRefusal(second));
        h.succeed();
    }
}
