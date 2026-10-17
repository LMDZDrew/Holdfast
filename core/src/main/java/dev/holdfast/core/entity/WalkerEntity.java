package dev.holdfast.core.entity;

import dev.holdfast.core.combat.Grabs;
import dev.holdfast.core.registry.CoreSounds;
import dev.holdfast.core.rules.CombatTimings;
import dev.holdfast.core.rules.GrabRules;
import dev.holdfast.core.rules.Posture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Random;

/**
 * An ordinary walker (decisions.md: no special variants). Upright, prone (dormant until it notices a player) or a
 * lower-half crawler. It takes no accumulated damage: {@link #hurt} ignores ordinary damage, and it dies only when
 * the core resolves a brain kill ({@link #brainKill}). Its corpse stays as a lying entity until removed.
 *
 * <p>Animation: three GeckoLib layers (body gait, arms, head) chosen from the UUID seed, or one full-body clip the
 * server sets with a nonce (lunge, grab, reaction, death). Clip choice is presentation; grab and hit timing are the
 * server's (Grabs, CombatTimings).
 */
public class WalkerEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> POSTURE = SynchedEntityData.defineId(WalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(WalkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CORPSE = SynchedEntityData.defineId(WalkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> FULL_CLIP = SynchedEntityData.defineId(WalkerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> CLIP_NONCE = SynchedEntityData.defineId(WalkerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(WalkerEntity.class, EntityDataSerializers.INT);

    private static final String[] GAITS = {"body.shamble", "body.shuffle", "body.drag_left", "body.limp_right", "body.stiff",
            "body.hunched", "body.lurch", "body.heavy"};
    private static final String[] ARMS = {"arms.dangle", "arms.forward_low", "arms.one_up", "arms.broken", "arms.clutched", "arms.wide"};
    private static final String[] HEADS = {"head.loll_left", "head.loll_right", "head.chin_down", "head.sniff", "head.slack", "head.twitch"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Server: ticks left on the current full-body clip; AI holds still while it plays. */
    private int fullClipTicks;
    /** Server: ticks of stagger left; no lunges or grabs. */
    private int staggerTicks;
    // client bookkeeping
    private final int[] seenNonce = {-1, -1, -1};
    private final String[] seenClip = {"", "", ""};
    private String localClip;
    private int localNonce = -1;

    /** Paired presentation begins on the same client packet as the player's clip. Later data sync must not restart it. */
    public void startLocally(String clip, int nonce) { localClip = clip; localNonce = nonce; }

    public WalkerEntity(EntityType<? extends WalkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.17D)          // ordinary shamble, slower than a walking player
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D);           // walkers hurt by bites, through Grabs, not by melee damage
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSTURE, Posture.UPRIGHT.ordinal());
        entityData.define(DORMANT, false);
        entityData.define(CORPSE, false);
        entityData.define(FULL_CLIP, "");
        entityData.define(CLIP_NONCE, 0);
        entityData.define(SKIN, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WakeGoal());
        goalSelector.addGoal(2, new LungeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true, p -> !((Player) p).isCreative() && !p.isSpectator()));
    }

    // ------------------------------------------------------------------ state

    public Posture posture() {
        int i = entityData.get(POSTURE);
        return Posture.values()[Math.max(0, Math.min(Posture.values().length - 1, i))];
    }

    public void setPosture(Posture p) {
        entityData.set(POSTURE, p.ordinal());
        refreshDimensions();
    }

    public boolean dormant() {
        return entityData.get(DORMANT);
    }

    public void setDormant(boolean d) {
        entityData.set(DORMANT, d);
    }

    public boolean isCorpse() {
        return entityData.get(CORPSE);
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public String fullClip() {
        if (level().isClientSide && localClip != null && localNonce >= entityData.get(CLIP_NONCE)) return localClip;
        return entityData.get(FULL_CLIP);
    }

    public int clipNonce() {
        return level().isClientSide ? Math.max(localNonce, entityData.get(CLIP_NONCE)) : entityData.get(CLIP_NONCE);
    }

    /** Server: play a full-body clip for {@code ticks}, holding the AI still. Empty clip returns to the layers. */
    public void playFull(String clip, int ticks) {
        entityData.set(FULL_CLIP, clip);
        entityData.set(CLIP_NONCE, entityData.get(CLIP_NONCE) + 1);
        fullClipTicks = ticks;
        getNavigation().stop();
    }

    public boolean busy() {
        return fullClipTicks > 0 || staggerTicks > 0 || isCorpse() || dormant() || Grabs.isGrabbing(this);
    }

    /** Server: a body or weak head hit. Body impacts never kill (decisions.md). */
    public void stagger(LivingEntity from) {
        if (isCorpse()) return;
        Grabs.releaseByAttacker(this);
        staggerTicks = 20;
        if (posture() == Posture.UPRIGHT) playFull("react_bat_quick", 20);
        if (from != null) knockback(0.35D, from.getX() - getX(), from.getZ() - getZ());
    }

    /** Server: the core resolved a brain kill. {@code reactionClip} is a paired reaction, or null for the plain death. */
    public void brainKill(String reactionClip, int reactionTicks) {
        if (isCorpse()) return;
        Grabs.releaseByAttacker(this);
        entityData.set(CORPSE, true);
        setNoAi(true);
        getNavigation().stop();
        String clip = reactionClip != null ? reactionClip : switch (posture()) {
            case UPRIGHT -> "death_headshot";
            case CRAWLER -> "crawler_death";
            case PRONE -> "react_knife_ground_prone";
        };
        if (!clip.equals(fullClip())) {          // a paired reaction already playing keeps running into its fall
            entityData.set(FULL_CLIP, clip);
            entityData.set(CLIP_NONCE, entityData.get(CLIP_NONCE) + 1);
        }
        fullClipTicks = Integer.MAX_VALUE;     // corpses hold the last frame
        playSound(CoreSounds.WALKER_DEATH.get(), 0.8F, 0.9F + random.nextFloat() * 0.2F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (staggerTicks > 0) staggerTicks--;
        if (fullClipTicks > 0 && fullClipTicks != Integer.MAX_VALUE && --fullClipTicks == 0 && !isCorpse()) {
            entityData.set(FULL_CLIP, "");
            entityData.set(CLIP_NONCE, entityData.get(CLIP_NONCE) + 1);
        }
        if (!isCorpse() && !dormant() && tickCount % 80 == random.nextInt(80) && random.nextFloat() < 0.6F) {
            playSound(getTarget() != null ? CoreSounds.WALKER_SNARL.get() : CoreSounds.WALKER_GROAN.get(), 0.9F, 0.85F + random.nextFloat() * 0.3F);
        }
    }

    /** Lying walkers (prone, crawler, corpses) get a low square box covering head to hips (~1.6 blocks). */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (posture().onGround() || isCorpse()) return net.minecraft.world.entity.EntityDimensions.scalable(1.6F, 0.5F);
        return super.getDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (POSTURE.equals(key) || CORPSE.equals(key)) refreshDimensions();
    }

    @Override
    public boolean isPushable() {
        return !isCorpse() && super.isPushable();
    }

    @Override
    public boolean isPickable() {
        return true;   // corpses can still be looked at and removed
    }

    /**
     * Walkers have no damage pool. Only /kill and the void remove one; every combat outcome comes from the core's
     * rules (CombatEvents, TaczBridge, Executions), which call {@link #stagger} or {@link #brainKill}.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return super.hurt(source, amount);
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;     // vocals are played explicitly in tick() so they can vary with pursuit
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("HfPosture", posture().name());
        tag.putBoolean("HfDormant", dormant());
        tag.putBoolean("HfCorpse", isCorpse());
        tag.putInt("HfSkin", skin());
        if (isCorpse()) tag.putString("HfDeathClip", fullClip());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            setPosture(Posture.valueOf(tag.getString("HfPosture")));
        } catch (IllegalArgumentException ignored) {
            setPosture(Posture.UPRIGHT);
        }
        setDormant(tag.getBoolean("HfDormant"));
        entityData.set(SKIN, tag.contains("HfSkin") ? tag.getInt("HfSkin") : new Random(getUUID().getLeastSignificantBits()).nextInt(4));
        if (tag.getBoolean("HfCorpse")) {
            entityData.set(CORPSE, true);
            entityData.set(FULL_CLIP, tag.getString("HfDeathClip"));
            fullClipTicks = Integer.MAX_VALUE;
            setNoAi(true);
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && entityData.get(SKIN) == 0 && !isCorpse()) {
            entityData.set(SKIN, new Random(getUUID().getLeastSignificantBits()).nextInt(4));
        }
    }

    // ------------------------------------------------------------------ AI

    /** Prone or dormant walkers wake when a player is close and visible (S12): a rise clip, then upright. */
    private final class WakeGoal extends Goal {
        WakeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return dormant() && !isCorpse();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null || !hasLineOfSight(t)) return;
            double d = distanceTo(t);
            if (posture() == Posture.PRONE && d < CombatTimings.GRAB_REACH + 0.4 && t instanceof ServerPlayer sp) {
                setDormant(false);
                playFull("prone_ankle_grab", 10);
                Grabs.start(WalkerEntity.this, sp, GrabRules.Kind.PRONE_ANKLE);
            } else if (d < 7.0) {
                setDormant(false);
                if (posture() == Posture.PRONE) {
                    playFull("prone_rise", 52);
                    setPosture(Posture.UPRIGHT);
                }
            }
        }
    }

    /** Approach, then lunge; the server checks reach and sight at its own hit tick (S1) and starts the grab. */
    private final class LungeGoal extends Goal {
        private int lungeTick = -1;
        private int cooldown;

        LungeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() instanceof Player && !dormant() && !isCorpse();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            lungeTick = -1;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (cooldown > 0) cooldown--;
            if (lungeTick >= 0) {
                lungeTick++;
                int hit = posture() == Posture.CRAWLER ? CombatTimings.CRAWLER_LUNGE_HIT_TICK : CombatTimings.LUNGE_HIT_TICK;
                if (lungeTick == hit) {
                    if (t instanceof ServerPlayer sp && distanceTo(sp) <= CombatTimings.GRAB_REACH + 0.3 && hasLineOfSight(sp)
                            && staggerTicks == 0) {
                        Grabs.start(WalkerEntity.this, sp, posture() == Posture.CRAWLER ? GrabRules.Kind.CRAWLER_ANKLE : GrabRules.Kind.UPRIGHT);
                    }
                    lungeTick = -1;
                    cooldown = 30;
                }
                return;
            }
            if (busy()) return;
            double d = distanceTo(t);
            if (d <= CombatTimings.GRAB_REACH && cooldown == 0 && hasLineOfSight(t)) {
                lungeTick = 0;
                playFull(posture() == Posture.CRAWLER ? "crawler_lunge" : "lunge", 20);
                playSound(CoreSounds.WALKER_LUNGE.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
            } else {
                getNavigation().moveTo(t, posture() == Posture.CRAWLER ? 0.7D : 1.0D);
            }
        }
    }

    // ------------------------------------------------------------------ animation (client)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String[] names = {"body", "arms", "head"};
        for (int i = 0; i < 3; i++) {
            final int layer = i;
            controllers.add(new AnimationController<>(this, names[i], 4, state -> handle(layer, state)));
        }
    }

    private String layerClip(int layer) {
        String full = fullClip();
        if (!full.isEmpty()) return layer == 0 ? full : "";
        Posture p = posture();
        if (p == Posture.CRAWLER) return layer == 0 ? (walkAnimation.isMoving() ? "crawl" : "crawler_still") : "";
        if (p == Posture.PRONE) return layer == 0 ? "prone_still" : "";
        Random r = new Random(getUUID().getMostSignificantBits());
        boolean pursuing = getTarget() != null || isAggressive();
        return switch (layer) {
            case 0 -> walkAnimation.isMoving() ? (pursuing ? "body.hungry" : GAITS[r.nextInt(GAITS.length)]) : "body.stand";
            case 1 -> pursuing ? "arms.reach" : ARMS[new Random(r.nextLong()).nextInt(ARMS.length)];
            default -> pursuing ? "head.snap" : HEADS[new Random(r.nextLong() * 31).nextInt(HEADS.length)];
        };
    }

    private PlayState handle(int layer, AnimationState<WalkerEntity> state) {
        String clip = layerClip(layer);
        if (clip.isEmpty()) return PlayState.STOP;
        String prefix = posture() == Posture.CRAWLER ? "animation.holdfast.crawler." : "animation.holdfast.walker.";
        state.getController().setTransitionLength(fullClip().isEmpty() ? 4 : 0);
        int nonce = clipNonce();
        if (nonce != seenNonce[layer] || !clip.equals(seenClip[layer])) {
            seenNonce[layer] = nonce;
            seenClip[layer] = clip;
            state.getController().forceAnimationReset();
        }
        boolean once = !fullClip().isEmpty();
        return state.setAndContinue(RawAnimation.begin().then(prefix + clip,
                once ? Animation.LoopType.HOLD_ON_LAST_FRAME : Animation.LoopType.LOOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
