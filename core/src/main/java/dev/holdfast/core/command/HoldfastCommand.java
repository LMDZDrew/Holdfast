package dev.holdfast.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.holdfast.core.entity.WalkerEntity;
import dev.holdfast.core.registry.CoreEntities;
import dev.holdfast.core.rules.InjuryRules;
import dev.holdfast.core.rules.Posture;
import dev.holdfast.core.state.ConditionCapability;
import dev.holdfast.core.state.ConditionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Operator commands for Stage 1 checks until the wound system and world spawning exist. They set state the server
 * owns; they never fake an outcome (a spawned walker still dies only by a brain kill).
 * <pre>
 * /holdfast walker [upright|prone|crawler]   spawn a walker 4 blocks ahead (prone ones are dormant)
 * /holdfast injury &lt;movement&gt;               set your injury movement state (NORMAL, LIMP, LIMP_SEVERE, ...)
 * /holdfast condition                       print your saved condition
 * /holdfast cure                            clear bite and injury (test only)
 * </pre>
 */
public final class HoldfastCommand {
    private HoldfastCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("holdfast").requires(s -> s.hasPermission(2))
                .then(Commands.literal("walker")
                        .executes(c -> spawn(c, Posture.UPRIGHT))
                        .then(Commands.argument("posture", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Posture.values()).map(p -> p.name().toLowerCase(Locale.ROOT)), b))
                                .executes(c -> spawn(c, Posture.valueOf(StringArgumentType.getString(c, "posture").toUpperCase(Locale.ROOT))))))
                .then(Commands.literal("injury")
                        .then(Commands.argument("movement", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(InjuryRules.Movement.values()).map(Enum::name), b))
                                .executes(HoldfastCommand::injury)))
                .then(Commands.literal("condition").executes(HoldfastCommand::condition))
                .then(Commands.literal("presentation")
                        .then(Commands.argument("contact", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"blunt", "slash", "stab", "gunshot"}, b))
                                .executes(HoldfastCommand::presentation)))
                .then(Commands.literal("cure").executes(HoldfastCommand::cure)));
    }

    /** Operator-only visual/audio probe, no hit, victim, damage, durability or saved state. */
    private static int presentation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        dev.holdfast.core.rules.WeaponClass.Contact family;
        try {
            family = dev.holdfast.core.rules.WeaponClass.Contact.valueOf(StringArgumentType.getString(c, "contact").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("expected blunt, slash, stab or gunshot"));
            return 0;
        }
        var direction = p.getLookAngle();
        var point = p.getEyePosition().add(direction.scale(1.4));
        dev.holdfast.core.net.CoreNet.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                dev.holdfast.core.net.HitFeedbackPacket.of(-1, point, direction, family,
                        dev.holdfast.core.rules.BodyRegion.TORSO, dev.holdfast.core.rules.WalkerStrikeRules.Outcome.STAGGER));
        c.getSource().sendSuccess(() -> Component.literal("presentation probe " + family + " (cosmetic only)"), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Posture posture) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WalkerEntity w = CoreEntities.WALKER.get().create(p.serverLevel());
        if (w == null) return 0;
        double rad = Math.toRadians(p.getYRot());
        w.moveTo(p.getX() - Math.sin(rad) * 4, p.getY(), p.getZ() + Math.cos(rad) * 4, p.getYRot() + 180.0F, 0);
        w.setPosture(posture);
        if (posture == Posture.PRONE) w.setDormant(true);
        p.serverLevel().addFreshEntity(w);
        c.getSource().sendSuccess(() -> Component.literal("spawned " + posture.name().toLowerCase(Locale.ROOT) + " walker " + w.getId()), true);
        return 1;
    }

    private static int injury(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        InjuryRules.Movement m;
        try {
            m = InjuryRules.Movement.valueOf(StringArgumentType.getString(c, "movement").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("unknown movement state"));
            return 0;
        }
        var cond = ConditionCapability.of(p);
        cond.setMovement(m);
        ConditionEvents.applySpeed(p, cond);
        c.getSource().sendSuccess(() -> Component.literal("movement " + m + " (" + Math.round(m.speed * 100) + "% speed)"), false);
        return 1;
    }

    private static int condition(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CompoundTag t = ConditionCapability.of(p).save();
        c.getSource().sendSuccess(() -> Component.literal(t.toString()), false);
        return 1;
    }

    private static int cure(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var cond = ConditionCapability.of(p);
        cond.load(new CompoundTag());
        ConditionEvents.applySpeed(p, cond);
        c.getSource().sendSuccess(() -> Component.literal("condition cleared (test only)"), false);
        return 1;
    }
}
