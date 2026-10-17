package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.net.PlayClipPacket;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Plays the server's chosen clips on players through Player Animator. Two layers: actions (grabs, executions,
 * charge) at priority 1100, above TaCZ's 93-96 so a paired clip owns the arms; locomotion at 95 (the injury walks), which
 * swaps to the legs-only {@code _gun} variant while a TaCZ gun is held so TaCZ keeps arms, items and ADS
 * (testing.md, Codex arm-ownership run).
 */
public final class ClientClips {
    private static final ResourceLocation ACTION = new ResourceLocation(HoldfastCore.MODID, "action");
    private static final ResourceLocation LOCOMOTION = new ResourceLocation(HoldfastCore.MODID, "locomotion");
    private static final ResourceLocation TACZ_GUN = new ResourceLocation("tacz", "modern_kinetic_gun");
    private static String movementClip;
    /** The locomotion clip currently applied, so the layer is only touched when the wanted clip changes. */
    private static String applied;

    private ClientClips() {}

    static void registerLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ACTION, 1100, p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LOCOMOTION, 95, p -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer p, ResourceLocation id) {
        return (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(p).get(id);
    }

    private static boolean holdingGun(AbstractClientPlayer p) {
        return TACZ_GUN.equals(ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()));
    }

    public static void play(PlayClipPacket m) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(m.playerId()) instanceof AbstractClientPlayer p)) return;
        ModifierLayer<IAnimation> layer = layer(p, ACTION);
        if (layer == null) return;
        if (m.clip().isEmpty()) {
            layer.setAnimation(null);
            return;
        }
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastCore.ASSETS, m.clip()));
        if (data == null) {
            HoldfastCore.LOG.warn("[holdfast] no Player Animator clip holdfast:{}", m.clip());
            return;
        }
        layer.setAnimation(new KeyframeAnimationPlayer(data)
                .setFirstPersonMode(m.ownsArms() ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true)));
        p.yBodyRot = p.getYRot();
        p.yBodyRotO = p.yBodyRot;
    }

    /** The server changed our movement state; the injury walk follows it (and the held item, every tick). */
    static void movementChanged(String movement) {
        movementClip = switch (movement.toUpperCase(Locale.ROOT)) {
            case "ARM_INJURED" -> "move_arm_injured";
            case "LIMP" -> "move_limp";
            case "LIMP_SEVERE" -> "move_limp_severe";
            case "PROSTHESIS" -> "move_prosthesis";
            case "CRUTCH_MEDICAL" -> "move_crutch_medical";
            case "CRUTCH_IMPROVISED" -> "move_crutch_improvised";
            case "LEGLESS_CRAWL" -> "move_crawl_legless";
            default -> null;
        };
        refreshLocomotion();
    }

    /** Called every client tick: follows the movement state and swaps the _gun variant as the held item changes. */
    static void refreshLocomotion() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ModifierLayer<IAnimation> layer = layer(mc.player, LOCOMOTION);
        if (layer == null) return;
        String clip = movementClip;
        boolean gunVariant = false;
        if (clip != null && holdingGun(mc.player) && !clip.startsWith("move_crutch") && !clip.equals("move_crawl_legless")) {
            clip += "_gun";
            gunVariant = true;
        }
        if (java.util.Objects.equals(clip, applied)) return;
        applied = clip;
        if (clip == null) {
            layer.setAnimation(null);
            return;
        }
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastCore.ASSETS, clip));
        if (data == null) return;
        layer.setAnimation(new KeyframeAnimationPlayer(data)
                .setFirstPersonMode(gunVariant ? FirstPersonMode.NONE : FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true)));
    }
}
