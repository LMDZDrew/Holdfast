package dev.holdfast.core.gametest;

import com.mojang.authlib.GameProfile;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.MeleeSwings;
import dev.holdfast.core.combat.Executions;
import dev.holdfast.core.combat.Strikes;
import dev.holdfast.core.combat.Weapons;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.item.MeleeWeaponItem;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.registry.CoreItems;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.ExecutionRules;
import dev.holdfast.core.rules.Posture;
import dev.holdfast.core.rules.SwingRules;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Iconic weapons reuse an existing family on the server: same class, handedness and contact resolution. */
@GameTestHolder(HoldfastCore.MODID)
@PrefixGameTestTemplate(false)
public final class IconicWeaponGameTests {
    private IconicWeaponGameTests() {}

    /** Michonne's katana is a two-handed katana and its swing resolves exactly once, at the katana contact tick. */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void michonneKatanaSwingsAsAKatana(GameTestHelper h) {
        ItemStack stack = new ItemStack(CoreItems.MICHONNE_KATANA.get());
        h.assertTrue(Weapons.of(stack) == WeaponClass.KATANA, "class " + Weapons.of(stack));
        h.assertTrue(stack.getItem() instanceof MeleeWeaponItem m && m.twoHanded, "not a two-handed melee weapon");
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "hf_michonne"));
        Vec3 at = h.absoluteVec(new Vec3(3.5, 2, 1.5));
        p.moveTo(at.x, at.y, at.z, 0.0F, -8.0F);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        WalkerEntity w = h.spawn(CoreEntities.WALKER.get(), new BlockPos(3, 2, 2));
        w.setPosture(Posture.UPRIGHT);
        w.setNoAi(true);
        String clip = SwingRules.clipFor(WeaponClass.KATANA, true);
        int contact = CombatTimings.swing(clip).contactTick();
        int damageBefore = p.getMainHandItem().getDamageValue();
        h.assertTrue(MeleeSwings.contact(p).walker() == w, "the view ray does not meet the walker");
        h.assertTrue(MeleeSwings.start(p, Weapons.held(p), clip, 1.0, 0), "swing refused");
        h.runAfterDelay(contact - 1, () -> h.assertTrue(MeleeSwings.pending(p), "resolved before the katana contact tick"));
        h.runAfterDelay(contact + 1, () -> {
            h.assertTrue(MeleeSwings.lastResult(p) == MeleeSwings.Result.HIT, "result " + MeleeSwings.lastResult(p));
            h.assertTrue(p.getMainHandItem().getDamageValue() == damageBefore + 1, "durability not spent once at contact");
            h.succeed();
        });
    }

    @GameTest(template="arena", timeoutTicks=80)
    public static void michonneKatanaStandingExecution(GameTestHelper h) { execution(h,Posture.UPRIGHT,false); }

    @GameTest(template="arena", timeoutTicks=80)
    public static void michonneKatanaRearExecution(GameTestHelper h) { execution(h,Posture.UPRIGHT,true); }

    @GameTest(template="arena", timeoutTicks=80)
    public static void michonneKatanaCrawlerExecution(GameTestHelper h) { execution(h,Posture.CRAWLER,false); }

    @GameTest(template="arena", timeoutTicks=80)
    public static void michonneKatanaProneExecution(GameTestHelper h) { execution(h,Posture.PRONE,false); }

    private static void execution(GameTestHelper h, Posture posture, boolean rear) {
        Vec3 at=h.absoluteVec(new Vec3(3.5,2,3.5));
        WalkerEntity w=h.spawn(CoreEntities.WALKER.get(),new Vec3(3.5,2,3.5));
        w.setPosture(posture); w.setNoAi(true); w.moveTo(at.x,at.y,at.z,0,0);
        ServerPlayer p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"hf_mk_exec"));
        double headOffset=posture==Posture.PRONE?13/16.0:posture==Posture.CRAWLER?9/16.0:0;
        p.moveTo(at.x,at.y,at.z+headOffset+(rear?-2.5:2.5),rear?0:180,23);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(CoreItems.MICHONNE_KATANA.get()));
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var timing=ExecutionRules.clipFor(WeaponClass.KATANA,posture).orElseThrow();
        Executions.request(p,w);
        h.assertTrue(Executions.isExecuting(p),"refused "+posture+": "+Executions.lastRefusal(p));
        h.assertTrue(Math.abs(p.getXRot()-(posture==Posture.UPRIGHT?23:0))<.001,"unexpected execution look pitch");
        h.assertTrue(w.fullClip().startsWith("react_katana"),"wrong reaction "+w.fullClip());
        if(rear) h.assertTrue(Executions.lastApproach(p).orElseThrow()==ExecutionRules.Approach.REAR,"rear approach lost");
        h.runAfterDelay(timing.killTick()-1,()->h.assertFalse(w.isCorpse(),"killed before server contact tick"));
        h.runAfterDelay(timing.killTick()+1,()-> {
            h.assertTrue(w.isCorpse(),"no brain kill for "+posture);
            h.assertTrue(p.getMainHandItem().getDamageValue()==1,"durability not spent exactly once");
            h.assertTrue(Strikes.lastFeedback(w).isPresent(),"no confirmed feedback");
        });
        h.runAfterDelay(timing.lengthTicks()+3,()-> {
            h.assertFalse(Executions.isExecuting(p),"execution retained ownership after release");
            h.assertTrue(p.getMainHandItem().getDamageValue()==1,"duplicate durability spend");
            h.succeed();
        });
    }
}
