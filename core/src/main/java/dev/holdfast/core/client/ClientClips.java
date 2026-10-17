package dev.holdfast.core.client;

import dev.holdfast.core.HoldfastCore;
import dev.holdfast.core.item.MeleeWeaponItem;
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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Chooses and plays Player Animator clips. Presentation only: the server decided every outcome these show.
 *
 * <p>Layers, low to high priority (TaCZ uses 93-96):
 * <ul>
 * <li>locomotion 95: walk / sprint / crouch walk / jump, or the injury walk; a legs-only {@code _gun} variant while a
 * TaCZ gun is held, so TaCZ keeps arms, items and ADS (testing.md, Codex arm-ownership run).</li>
 * <li>weapon hold 97: the held melee family's hold (arms, items, head only), over locomotion.</li>
 * <li>action 1100: server-started clips (grabs, executions, charge) and swings; owns the arms while it plays.</li>
 * </ul>
 * Locomotion and swings are chosen from what this client sees of each player (movement, the vanilla swing it already
 * receives); the owner's injury walk follows the server's synced condition.
 */
public final class ClientClips {
    private static final ResourceLocation ACTION = new ResourceLocation(HoldfastCore.MODID, "action");
    private static final ResourceLocation HOLD = new ResourceLocation(HoldfastCore.MODID, "weapon_hold");
    private static final ResourceLocation LOCOMOTION = new ResourceLocation(HoldfastCore.MODID, "locomotion");
    private static final ResourceLocation TACZ_GUN = new ResourceLocation("tacz", "modern_kinetic_gun");

    /** The owner's injury walk, from the server's condition sync (null = uninjured). */
    private static String injuryClip;

    private static final class Applied {
        String locomotion, hold;
        int lastSwingTime = 99;      // "not swinging", so the first swing is caught
        boolean wasOnGround = true;
    }

    private static final Map<UUID, Applied> APPLIED = new HashMap<>();

    private ClientClips() {}

    static void registerLayers() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ACTION, 1100, p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(HOLD, 97, p -> new ModifierLayer<>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LOCOMOTION, 95, p -> new ModifierLayer<>());
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer p, ResourceLocation id) {
        return (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(p).get(id);
    }

    private static boolean holdingGun(Player p) {
        return TACZ_GUN.equals(ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()));
    }

    private static KeyframeAnimationPlayer player(String clip, FirstPersonMode fp) {
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(HoldfastCore.ASSETS, clip));
        if (data == null) {
            HoldfastCore.LOG.warn("[holdfast] no Player Animator clip holdfast:{}", clip);
            return null;
        }
        return new KeyframeAnimationPlayer(data).setFirstPersonMode(fp)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
    }

    /** A server-started clip (grab, execution, bite confirmation, charge). */
    public static void play(PlayClipPacket m) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(m.playerId()) instanceof AbstractClientPlayer p)) return;
        ModifierLayer<IAnimation> layer = layer(p, ACTION);
        if (layer == null) return;
        if (m.clip().isEmpty()) {
            layer.setAnimation(null);
            return;
        }
        KeyframeAnimationPlayer kp = player(m.clip(), m.ownsArms() ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
        if (kp == null) return;
        layer.setAnimation(kp);
        p.yBodyRot = p.getYRot();
        p.yBodyRotO = p.yBodyRot;
    }

    /** Other players' injury walks, by entity id (MovementPacket). */
    private static final Map<Integer, String> REMOTE_INJURY = new HashMap<>();

    public static void remoteMovement(dev.holdfast.core.net.MovementPacket m) {
        String clip = injuryClipFor(m.movement());
        if (clip == null) REMOTE_INJURY.remove(m.playerId());
        else REMOTE_INJURY.put(m.playerId(), clip);
    }

    static void movementChanged(String movement) {
        injuryClip = injuryClipFor(movement);
    }

    private static String injuryClipFor(String movement) {
        return switch (movement.toUpperCase(Locale.ROOT)) {
            case "ARM_INJURED" -> "move_arm_injured";
            case "LIMP" -> "move_limp";
            case "LIMP_SEVERE" -> "move_limp_severe";
            case "PROSTHESIS" -> "move_prosthesis";
            case "CRUTCH_MEDICAL" -> "move_crutch_medical";
            case "CRUTCH_IMPROVISED" -> "move_crutch_improvised";
            case "LEGLESS_CRAWL" -> "move_crawl_legless";
            default -> null;
        };
    }

    /** Called every client tick. */
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Player pl : mc.level.players()) {
            if (pl instanceof AbstractClientPlayer p) update(p, p == mc.player);
        }
        APPLIED.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    private static String family(Player p) {
        if (!(p.getMainHandItem().getItem() instanceof MeleeWeaponItem m)) return null;
        return switch (m.weaponClass) {
            case KNIFE -> "knife";
            case MACHETE -> "machete";
            case HATCHET -> "hatchet";
            case KATANA -> "katana";
            case AXE, HAMMER -> "axe";
            case BAT -> "bat";
            default -> null;
        };
    }

    private static void update(AbstractClientPlayer p, boolean self) {
        Applied a = APPLIED.computeIfAbsent(p.getUUID(), k -> new Applied());
        boolean gun = holdingGun(p);
        // ---- locomotion
        double speed = p.getDeltaMovement().horizontalDistance();
        boolean moving = speed > 0.02;
        String loco;
        String injury = self ? injuryClip : REMOTE_INJURY.get(p.getId());
        if (injury != null) {
            loco = injury;
        } else if (!p.onGround() && a.wasOnGround && p.getDeltaMovement().y > 0.2) {
            loco = "move_jump";
        } else if (a.locomotion != null && a.locomotion.startsWith("move_jump") && !p.onGround()) {
            loco = a.locomotion.replace("_gun", "");
        } else if (!moving) {
            loco = null;
        } else if (p.isCrouching()) {
            loco = "move_crouch_walk";
        } else {
            loco = p.isSprinting() ? "move_sprint" : "move_walk";
        }
        a.wasOnGround = p.onGround();
        boolean noGunVariant = loco != null && (loco.startsWith("move_crutch") || loco.equals("move_crawl_legless"));
        if (loco != null && gun && !noGunVariant) loco += "_gun";
        if (!Objects.equals(loco, a.locomotion)) {
            a.locomotion = loco;
            ModifierLayer<IAnimation> layer = layer(p, LOCOMOTION);
            if (layer != null) {
                layer.setAnimation(loco == null ? null
                        : player(loco, FirstPersonMode.NONE));      // locomotion never takes the first-person view
            }
        }
        // ---- weapon hold (melee families; TaCZ guns keep their own hold)
        String fam = gun ? null : family(p);
        String hold = fam == null || fam.equals("bat") ? null : "hold_" + fam;
        if (!Objects.equals(hold, a.hold)) {
            a.hold = hold;
            ModifierLayer<IAnimation> layer = layer(p, HOLD);
            if (layer != null) layer.setAnimation(hold == null ? null : player(hold, FirstPersonMode.NONE));
        }
        // ---- swing: vanilla already tells every client when a player starts a swing
        if (fam != null && p.swinging && p.swingTime <= 1 && a.lastSwingTime > 1) {
            boolean heavy = self && p.getAttackStrengthScale(0.0F) >= 0.95F;
            String clip = switch (fam) {
                case "bat" -> "bat_swing_quick";
                case "axe" -> heavy ? "swing_axe_overhead" : "swing_axe";
                default -> "swing_" + fam + (heavy ? "_heavy" : "");
            };
            ModifierLayer<IAnimation> layer = layer(p, ACTION);
            if (layer != null && (layer.getAnimation() == null || !layer.getAnimation().isActive())) {
                KeyframeAnimationPlayer kp = player(clip, FirstPersonMode.THIRD_PERSON_MODEL);
                if (kp != null) layer.setAnimation(kp);
            }
        }
        a.lastSwingTime = p.swinging ? p.swingTime : 99;
    }
}
