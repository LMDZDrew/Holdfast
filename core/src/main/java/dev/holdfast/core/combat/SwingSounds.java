package dev.holdfast.core.combat;

import dev.holdfast.core.registry.CoreSounds;
import dev.holdfast.core.rules.WeaponClass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** One server-scheduled presentation cue per accepted swing, including misses. No predicted duplicate. */
final class SwingSounds {
    private SwingSounds() {}

    static void play(ServerPlayer player, WeaponClass weapon) {
        if (weapon.firearm()) return;
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), CoreSounds.swing(weapon),
                SoundSource.PLAYERS, weapon == WeaponClass.NONE ? .35F : .75F,
                .96F + player.getRandom().nextFloat() * .08F);
    }
}
