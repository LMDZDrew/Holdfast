package dev.holdfast.core.gametest;

import com.mojang.authlib.GameProfile;
import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.combat.MeleeSwings;
import dev.holdfast.core.combat.Weapons;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.item.MeleeWeaponItem;
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
}
