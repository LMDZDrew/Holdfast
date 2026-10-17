package dev.holdfast.core.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

final class Players {
    private Players() {}

    /**
     * The live player for a session. Real players are always looked up by UUID (relogs and respawns replace the
     * object). Forge fake players (automation, GameTests) aren't in the player list, so the session's own reference
     * is used for them.
     */
    static ServerPlayer resolve(MinecraftServer server, UUID id, ServerPlayer ref) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null && ref instanceof FakePlayer && !ref.isRemoved()) return ref;
        return p;
    }
}
