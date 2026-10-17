package dev.holdfast.core.client;

import dev.holdfast.core.net.ConditionSyncPacket;
import dev.holdfast.core.net.GrabPromptPacket;
import net.minecraft.client.Minecraft;

/** Display-only copies of server state for the HUD. Nothing here decides anything. */
public final class ClientState {
    static ConditionSyncPacket condition = new ConditionSyncPacket(false, "", 0, 0, "NORMAL", 0);
    static int promptWindow;
    static long promptStartClientTick = -1;
    static boolean promptGround;
    static String lastResult = "";
    static long resultUntil;

    private ClientState() {}

    public static void condition(ConditionSyncPacket m) {
        condition = m;
        // Rehydrate the animation state even when a reconnect sends the same saved injury as the last session.
        ClientClips.movementChanged(m.movement());
    }

    public static void prompt(GrabPromptPacket m) {
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        if (m.windowTicks() > 0) {
            promptWindow = m.windowTicks();
            promptStartClientTick = now;
            promptGround = m.ground();
        } else {
            promptWindow = 0;
            promptStartClientTick = -1;
            lastResult = m.result();
            resultUntil = now + 40;
        }
    }
}
