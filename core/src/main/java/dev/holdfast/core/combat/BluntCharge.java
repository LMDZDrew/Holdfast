package dev.holdfast.core.combat;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-measured charge for blunt weapons (S10): charging starts when the server sees the use begin and ends when
 * it sees the release; the client's animation is only presentation. A released charge is spent by the next swing
 * within {@link #RELEASE_GRACE} ticks.
 */
public final class BluntCharge {
    /** Ticks of holding for a full charge (1 s). */
    public static final int FULL_TICKS = 20;
    public static final int RELEASE_GRACE = 8;

    private record Charge(long startTick, long releaseTick, double level) {}

    private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();

    private BluntCharge() {}

    public static void start(ServerPlayer p) {
        CHARGES.put(p.getUUID(), new Charge(p.serverLevel().getGameTime(), -1, 0));
    }

    public static void release(ServerPlayer p) {
        Charge c = CHARGES.get(p.getUUID());
        if (c == null || c.releaseTick >= 0) return;
        long now = p.serverLevel().getGameTime();
        CHARGES.put(p.getUUID(), new Charge(c.startTick, now, Math.min(1.0, (now - c.startTick) / (double) FULL_TICKS)));
    }

    /** Consumes and returns the released charge (0 if none or stale). */
    public static double spend(ServerPlayer p) {
        Charge c = CHARGES.remove(p.getUUID());
        if (c == null || c.releaseTick < 0) return 0;
        return p.serverLevel().getGameTime() - c.releaseTick <= RELEASE_GRACE ? c.level : 0;
    }

    /** Current charge while still holding (for executions that need a full charge). */
    public static double holding(ServerPlayer p) {
        Charge c = CHARGES.get(p.getUUID());
        if (c == null) return 0;
        long end = c.releaseTick >= 0 ? c.releaseTick : p.serverLevel().getGameTime();
        return Math.min(1.0, (end - c.startTick) / (double) FULL_TICKS);
    }

    public static void clear(ServerPlayer p) {
        CHARGES.remove(p.getUUID());
    }
}
