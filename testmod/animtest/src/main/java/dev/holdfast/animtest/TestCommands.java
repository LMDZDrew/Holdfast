package dev.holdfast.animtest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /hfa test commands (operator level 2). They only start presentation; nothing here is a gameplay rule.
 */
public final class TestCommands {
    private static final Map<UUID, Integer> CAMERA = new HashMap<>();
    private static final Map<UUID, String> FIRST_PERSON = new HashMap<>();
    private static final List<String> FP_MODES = List.of("THIRD_PERSON_MODEL", "VANILLA", "NONE", "DISABLED");

    private TestCommands() {}

    private static SuggestionProvider<CommandSourceStack> from(Collection<String> values) {
        Collection<String> sorted = new TreeSet<>(values);
        return (ctx, builder) -> SharedSuggestionProvider.suggest(sorted, builder);
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        Collection<String> walkerAndCrawler = Stream.concat(Clips.WALKER.keySet().stream(), Clips.CRAWLER.keySet().stream()).toList();
        d.register(Commands.literal("hfa").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(c -> spawn(c, false))
                        .then(Commands.literal("crawler").executes(c -> spawn(c, true))))
                .then(Commands.literal("walker")
                        .then(Commands.argument("clip", StringArgumentType.word()).suggests(from(walkerAndCrawler))
                                .executes(TestCommands::walker)))
                .then(Commands.literal("mix")
                        .then(Commands.argument("body", StringArgumentType.word()).suggests(from(prefixed("body.")))
                                .then(Commands.argument("arms", StringArgumentType.word()).suggests(from(prefixed("arms.")))
                                        .then(Commands.argument("head", StringArgumentType.word()).suggests(from(prefixed("head.")))
                                                .executes(TestCommands::mix)))))
                .then(Commands.literal("skin")
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 3)).executes(TestCommands::skin)))
                .then(Commands.literal("player")
                        .then(Commands.argument("clip", StringArgumentType.word()).suggests(from(Clips.PLAYER.keySet()))
                                .executes(TestCommands::player)))
                .then(Commands.literal("pair")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(from(Clips.PAIRS.stream().map(Clips.Pair::name).toList()))
                                .executes(TestCommands::pair)))
                .then(Commands.literal("stop").executes(TestCommands::stop))
                .then(Commands.literal("fp")
                        .then(Commands.argument("mode", StringArgumentType.word()).suggests(from(FP_MODES))
                                .executes(TestCommands::fp)))
                .then(Commands.literal("camera")
                        .then(Commands.argument("flags", IntegerArgumentType.integer(0, 15)).executes(TestCommands::camera))));
    }

    private static List<String> prefixed(String p) {
        return Clips.WALKER.keySet().stream().filter(k -> k.startsWith(p)).toList();
    }

    private static int camera(ServerPlayer p) {
        return CAMERA.getOrDefault(p.getUUID(), 1);
    }

    private static String fp(ServerPlayer p) {
        return FIRST_PERSON.getOrDefault(p.getUUID(), "THIRD_PERSON_MODEL");
    }

    private static void say(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        HoldfastAnimTest.LOG.info("[holdfast-test] server tick {}: {}", c.getSource().getServer().getTickCount(), msg);
    }

    private static TestWalker nearest(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(TestWalker.class, p.getBoundingBox().inflate(32.0D)).stream()
                .min(Comparator.comparingDouble(w -> w.distanceToSqr(p))).orElse(null);
    }

    private static TestWalker spawnAt(ServerPlayer p, double distBlocks) {
        ServerLevel level = p.serverLevel();
        TestWalker w = HoldfastAnimTest.TEST_WALKER.get().create(level);
        if (w == null) return null;
        place(w, p, distBlocks);
        level.addFreshEntity(w);
        return w;
    }

    /** Put the walker distBlocks in front of the player's feet, facing the player. */
    private static void place(TestWalker w, ServerPlayer p, double distBlocks) {
        float yaw = p.getYRot();
        double rad = Math.toRadians(yaw);
        double x = p.getX() - Math.sin(rad) * distBlocks;
        double z = p.getZ() + Math.cos(rad) * distBlocks;
        float facing = yaw + 180.0F;
        w.moveTo(x, p.getY(), z, facing, 0.0F);
        w.setYHeadRot(facing);
        w.setYBodyRot(facing);
    }

    private static int spawn(CommandContext<CommandSourceStack> c, boolean crawler) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        TestWalker w = spawnAt(p, 3.0D);
        if (w == null) {
            c.getSource().sendFailure(Component.literal("could not create test walker"));
            return 0;
        }
        if (crawler) w.setCrawler(true);
        say(c, "spawned test " + (crawler ? "crawler" : "walker") + " id " + w.getId() + " 3 blocks ahead");
        return 1;
    }

    private static TestWalker require(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TestWalker w = nearest(c.getSource().getPlayerOrException());
        if (w == null) c.getSource().sendFailure(Component.literal("no test walker within 32 blocks; use /hfa spawn"));
        return w;
    }

    private static int walker(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String clip = StringArgumentType.getString(c, "clip");
        TestWalker w = require(c);
        if (w == null) return 0;
        boolean crawlerClip = Clips.CRAWLER.containsKey(clip);
        if (!crawlerClip && !Clips.WALKER.containsKey(clip)) {
            c.getSource().sendFailure(Component.literal("unknown walker/crawler clip " + clip));
            return 0;
        }
        if (crawlerClip != w.isCrawler()) w.setCrawler(crawlerClip);
        w.playClip(clip);
        say(c, "walker " + w.getId() + " (" + (crawlerClip ? "crawler" : "upright") + ") plays " + clip);
        return 1;
    }

    private static int mix(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String body = StringArgumentType.getString(c, "body");
        String arms = StringArgumentType.getString(c, "arms");
        String head = StringArgumentType.getString(c, "head");
        for (String s : new String[]{body, arms, head}) {
            if (!Clips.WALKER.containsKey(s)) {
                c.getSource().sendFailure(Component.literal("unknown walker clip " + s));
                return 0;
            }
        }
        TestWalker w = require(c);
        if (w == null) return 0;
        if (w.isCrawler()) w.setCrawler(false);
        w.setLayers(body, arms, head);
        say(c, "walker " + w.getId() + " layers " + body + " + " + arms + " + " + head);
        return 1;
    }

    private static int skin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        TestWalker w = require(c);
        if (w == null) return 0;
        w.setSkin(IntegerArgumentType.getInteger(c, "n"));
        say(c, "walker " + w.getId() + " skin " + w.skin());
        return 1;
    }

    private static int player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String clip = StringArgumentType.getString(c, "clip");
        if (!Clips.PLAYER.containsKey(clip)) {
            c.getSource().sendFailure(Component.literal("unknown player clip " + clip));
            return 0;
        }
        Net.send(p, new Net.Play(p.getId(), clip, fp(p), camera(p), -1, "", "", "", 0, false));
        say(c, "player " + p.getGameProfile().getName() + " plays " + clip + " (first person " + fp(p) + ", camera flags " + camera(p) + ")");
        return 1;
    }

    private static int pair(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "preset");
        Clips.Pair pr = Clips.PAIRS.stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
        if (pr == null) {
            c.getSource().sendFailure(Component.literal("unknown pair preset " + name));
            return 0;
        }
        // Level the player's view so the pairing line is horizontal; yaw is kept.
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
        TestWalker w = nearest(p);
        if (w == null) w = spawnAt(p, pr.distPx() / 16.0D);
        if (w == null) return 0;
        if (w.isCrawler() != pr.crawler()) w.setCrawler(pr.crawler());
        place(w, p, pr.distPx() / 16.0D);
        int nonce = w.playClip(pr.attackerClip());
        Net.send(p, new Net.Play(p.getId(), pr.playerClip(), fp(p), camera(p),
                w.getId(), w.clip(0), w.clip(1), w.clip(2), nonce, pr.crawler()));
        say(c, "pair " + pr.name() + ": player " + pr.playerClip() + " + " + (pr.crawler() ? "crawler " : "walker ")
                + pr.attackerClip() + " at " + pr.distPx() + " px");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Net.send(p, new Net.Play(p.getId(), "", fp(p), camera(p), -1, "", "", "", 0, false));
        TestWalker w = nearest(p);
        if (w != null) w.setCrawler(w.isCrawler());
        say(c, "stopped player clip" + (w != null ? " and reset walker " + w.getId() + " to idle" : ""));
        return 1;
    }

    private static int fp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String mode = StringArgumentType.getString(c, "mode");
        if (!FP_MODES.contains(mode)) {
            c.getSource().sendFailure(Component.literal("mode must be one of " + FP_MODES));
            return 0;
        }
        FIRST_PERSON.put(p.getUUID(), mode);
        say(c, "first-person mode for next clips: " + mode);
        return 1;
    }

    private static int camera(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int flags = IntegerArgumentType.getInteger(c, "flags");
        CAMERA.put(p.getUUID(), flags);
        say(c, "camera flags for next clips: " + flags + " (1 track on, 2 flip yaw, 4 flip roll, 8 flip pitch)");
        return 1;
    }
}
