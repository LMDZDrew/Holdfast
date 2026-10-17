package dev.holdfast.core.state;

import dev.holdfast.core.rules.BodyRegion;
import dev.holdfast.core.rules.Difficulty;
import dev.holdfast.core.rules.InfectionRules;
import dev.holdfast.core.rules.InjuryRules;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server-owned survivor condition, saved in player NBT (docs/core_combat.md "Persistence"). Clients receive a
 * read-only copy for the HUD and clip choice. Time is the player's own online tick count, so "real minutes" of bite
 * decline pass only while the player is on the server, across restarts.
 */
public final class PlayerCondition {
    private long onlineTicks;
    private InfectionRules.Bite bite;               // null = not bitten
    private int blockedBites;
    private InjuryRules.Movement movement = InjuryRules.Movement.NORMAL;
    private final Map<BodyRegion, Integer> wounds = new EnumMap<>(BodyRegion.class);
    private boolean dirty = true;

    public long onlineTicks() {
        return onlineTicks;
    }

    public void tick() {
        onlineTicks++;
    }

    public InfectionRules.Bite bite() {
        return bite;
    }

    public boolean infected() {
        return bite != null;
    }

    /** One penetrating bite infects; later bites don't restart or speed up the decline. */
    public void bitten(BodyRegion region, Difficulty difficulty) {
        wounds.merge(region, 1, Integer::sum);
        if (bite == null) bite = new InfectionRules.Bite(region, onlineTicks, difficulty);
        dirty = true;
    }

    public void blockedBite() {
        blockedBites++;
        dirty = true;
    }

    public int blockedBites() {
        return blockedBites;
    }

    public InjuryRules.Movement movement() {
        return movement;
    }

    private boolean movementDirty = true;

    public void setMovement(InjuryRules.Movement m) {
        if (m != movement) {
            movement = m;
            dirty = true;
            movementDirty = true;
        }
    }

    /** True once after the movement state changed (or after load), so trackers can be told. */
    public boolean takeMovementDirty() {
        boolean d = movementDirty;
        movementDirty = false;
        return d;
    }

    public Map<BodyRegion, Integer> wounds() {
        return wounds;
    }

    public double infectionProgress() {
        return bite == null ? 0 : InfectionRules.progress(bite, onlineTicks);
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void markDirty() {
        dirty = true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", 1);
        t.putLong("onlineTicks", onlineTicks);
        t.putInt("blockedBites", blockedBites);
        t.putString("movement", movement.name());
        if (bite != null) {
            CompoundTag b = new CompoundTag();
            b.putString("region", bite.region().name());
            b.putLong("start", bite.startTick());
            b.putString("difficulty", bite.difficulty().name());
            t.put("bite", b);
        }
        CompoundTag w = new CompoundTag();
        wounds.forEach((r, n) -> w.putInt(r.name(), n));
        t.put("wounds", w);
        return t;
    }

    public void load(CompoundTag t) {
        onlineTicks = t.getLong("onlineTicks");
        blockedBites = t.getInt("blockedBites");
        movement = parse(InjuryRules.Movement.class, t.getString("movement"), InjuryRules.Movement.NORMAL);
        bite = null;
        if (t.contains("bite")) {
            CompoundTag b = t.getCompound("bite");
            bite = new InfectionRules.Bite(parse(BodyRegion.class, b.getString("region"), BodyRegion.NECK), b.getLong("start"),
                    parse(Difficulty.class, b.getString("difficulty"), Difficulty.DEFAULT));
        }
        wounds.clear();
        CompoundTag w = t.getCompound("wounds");
        for (String k : w.getAllKeys()) {
            BodyRegion r = parse(BodyRegion.class, k, null);
            if (r != null) wounds.put(r, w.getInt(k));
        }
        dirty = true;
        movementDirty = true;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
