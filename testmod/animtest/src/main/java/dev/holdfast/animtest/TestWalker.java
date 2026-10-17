package dev.holdfast.animtest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
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

/**
 * Test-only walker: no AI, invulnerable, never despawns. Plays whatever clips the server stores in its entity data.
 * Three GeckoLib controllers mirror the exported layers: body (gait or full-body clip), arms, head.
 * A full-body clip goes in the body controller with the arm and head layers off.
 */
public class TestWalker extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> BODY = SynchedEntityData.defineId(TestWalker.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ARMS = SynchedEntityData.defineId(TestWalker.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HEAD = SynchedEntityData.defineId(TestWalker.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> NONCE = SynchedEntityData.defineId(TestWalker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(TestWalker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CRAWLER = SynchedEntityData.defineId(TestWalker.class, EntityDataSerializers.BOOLEAN);
    private static final String[] LAYER = {"body", "arms", "head"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Client-side: clips started by a pair packet before the entity-data sync arrives.
    private String[] localClips;
    private int localNonce = -1;
    private boolean localCrawler;
    // Client-side bookkeeping per controller, for restart detection and timing logs.
    private final int[] seenNonce = {-1, -1, -1};
    private final String[] seenClip = {"", "", ""};
    private final RawAnimation[] raw = new RawAnimation[3];
    private final int[] startTick = new int[3];
    private final boolean[] finishLogged = new boolean[3];

    public TestWalker(EntityType<? extends TestWalker> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BODY, "body.stand");
        entityData.define(ARMS, "arms.dangle");
        entityData.define(HEAD, "head.loll_left");
        entityData.define(NONCE, 0);
        entityData.define(SKIN, 0);
        entityData.define(CRAWLER, false);
    }

    public boolean isCrawler() {
        return localNonce >= entityData.get(NONCE) && localClips != null ? localCrawler : entityData.get(CRAWLER);
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public void setSkin(int skin) {
        entityData.set(SKIN, skin);
    }

    public int nonce() {
        return entityData.get(NONCE);
    }

    public String clip(int layer) {
        return entityData.get(layer == 0 ? BODY : layer == 1 ? ARMS : HEAD);
    }

    /** Server: store all three layers and bump the nonce so every client restarts them together. Returns the new nonce. */
    public int setLayers(String body, String arms, String head) {
        entityData.set(BODY, body);
        entityData.set(ARMS, arms);
        entityData.set(HEAD, head);
        int n = entityData.get(NONCE) + 1;
        entityData.set(NONCE, n);
        return n;
    }

    /** Server: "body.x" / "arms.x" / "head.x" replace that layer; anything else is a full-body clip. */
    public int playClip(String clip) {
        if (clip.startsWith("body.")) return setLayers(clip, nonEmpty(clip(1), "arms.dangle"), nonEmpty(clip(2), "head.loll_left"));
        if (clip.startsWith("arms.")) return setLayers(isLayered(clip(0)) ? clip(0) : "body.stand", clip, nonEmpty(clip(2), "head.loll_left"));
        if (clip.startsWith("head.")) return setLayers(isLayered(clip(0)) ? clip(0) : "body.stand", nonEmpty(clip(1), "arms.dangle"), clip);
        return setLayers(clip, "", "");
    }

    /** Server: switch between the upright and lower-half rigs, resetting to that rig's idle. */
    public int setCrawler(boolean crawler) {
        entityData.set(CRAWLER, crawler);
        return crawler ? setLayers("crawler_still", "", "") : setLayers("body.stand", "arms.dangle", "head.loll_left");
    }

    private static boolean isLayered(String clip) {
        return clip.startsWith("body.");
    }

    private static String nonEmpty(String s, String fallback) {
        return s.isEmpty() ? fallback : s;
    }

    /** Client: start clips now (from a pair packet) instead of waiting for the entity-data sync. */
    public void startLocally(String body, String arms, String head, int nonce, boolean crawler) {
        localClips = new String[]{body, arms, head};
        localNonce = nonce;
        localCrawler = crawler;
    }

    private String effectiveClip(int layer) {
        if (localClips != null && localNonce >= entityData.get(NONCE)) return localClips[layer];
        return clip(layer);
    }

    private int effectiveNonce() {
        return Math.max(entityData.get(NONCE), localClips != null ? localNonce : -1);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        for (int i = 0; i < 3; i++) {
            final int layer = i;
            controllers.add(new AnimationController<>(this, LAYER[i], 0, state -> handle(layer, state))
                    .setCustomInstructionKeyframeHandler(e -> HoldfastAnimTest.LOG.info(
                            "[holdfast-test] walker {} {} clip {} timeline '{}' at controller tick {} ({} s)",
                            getId(), LAYER[layer], seenClip[layer], e.getKeyframeData().getInstructions(),
                            fmt(e.getAnimationTick()), fmt(e.getAnimationTick() / 20.0)))
                    .setSoundKeyframeHandler(e -> HoldfastAnimTest.LOG.info(
                            "[holdfast-test] walker {} {} clip {} sound cue '{}' at controller tick {} ({} s) (sounds are not registered yet)",
                            getId(), LAYER[layer], seenClip[layer], e.getKeyframeData().getSound(),
                            fmt(e.getAnimationTick()), fmt(e.getAnimationTick() / 20.0))));
        }
    }

    private PlayState handle(int layer, AnimationState<TestWalker> state) {
        String clip = effectiveClip(layer);
        int nonce = effectiveNonce();
        boolean crawler = isCrawler();
        if (nonce != seenNonce[layer] || !clip.equals(seenClip[layer])) {
            seenNonce[layer] = nonce;
            seenClip[layer] = clip;
            startTick[layer] = tickCount;
            finishLogged[layer] = false;
            raw[layer] = clip.isEmpty() ? null : RawAnimation.begin().then(fullName(clip, crawler), Animation.LoopType.DEFAULT);
            state.getController().forceAnimationReset();
            if (!clip.isEmpty()) {
                Double len = (crawler ? Clips.CRAWLER : Clips.WALKER).get(clip);
                HoldfastAnimTest.LOG.info("[holdfast-test] walker {} {} start clip {} ({}) at entity tick {}; exported length {} s = {} ticks",
                        getId(), LAYER[layer], clip, fullName(clip, crawler), tickCount,
                        len == null ? "UNKNOWN" : fmt(len), len == null ? "?" : fmt(len * 20));
            }
        }
        if (raw[layer] == null) return PlayState.STOP;
        // On the restart frame the controller still reports the previous run as finished, so skip that frame.
        if (!finishLogged[layer] && tickCount > startTick[layer] && state.getController().hasAnimationFinished()) {
            finishLogged[layer] = true;
            HoldfastAnimTest.LOG.info("[holdfast-test] walker {} {} clip {} finished after {} entity ticks",
                    getId(), LAYER[layer], clip, tickCount - startTick[layer]);
        }
        return state.setAndContinue(raw[layer]);
    }

    private static String fullName(String clip, boolean crawler) {
        return (crawler ? "animation.holdfast.crawler." : "animation.holdfast.walker.") + clip;
    }

    static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("HfBody", clip(0));
        tag.putString("HfArms", clip(1));
        tag.putString("HfHead", clip(2));
        tag.putInt("HfSkin", skin());
        tag.putBoolean("HfCrawler", entityData.get(CRAWLER));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HfBody")) {
            entityData.set(CRAWLER, tag.getBoolean("HfCrawler"));
            entityData.set(SKIN, tag.getInt("HfSkin"));
            setLayers(tag.getString("HfBody"), tag.getString("HfArms"), tag.getString("HfHead"));
        }
        setNoAi(true);
    }
}
