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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

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
                .then(Commands.literal("weapons").executes(TestCommands::weapons))
                .then(Commands.literal("sweep")
                        .then(Commands.literal("stop").executes(TestCommands::sweepStop))
                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(from(SWEEP_KINDS))
                                .executes(c -> sweep(c, 1))
                                .then(Commands.argument("from", IntegerArgumentType.integer(1))
                                        .executes(c -> sweep(c, IntegerArgumentType.getInteger(c, "from"))))))
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
        String held = equip(p, clip) + applyTestSpeed(p, clip);
        Net.send(p, new Net.Play(p.getId(), clip, fp(p), camera(p), -1, "", "", "", 0, false));
        say(c, "player " + p.getGameProfile().getName() + " plays " + clip + held + " (first person " + fp(p) + ", camera flags " + camera(p) + ")");
        return 1;
    }

    /**
     * Puts the clip's test weapon (from its extras file) in the main hand, and for crutch clips in both hands, so
     * held-item orientation is always judged with the geometry the clip was solved for. Whatever was held moves
     * into the inventory (or drops if it is full); other test weapons are simply replaced.
     */
    private static String equip(ServerPlayer p, String playerClip) {
        String weapon = Clips.WEAPON.get(playerClip);
        if (weapon == null || !HoldfastAnimTest.TEST_WEAPONS.containsKey(weapon)) return "";
        Item item = HoldfastAnimTest.TEST_WEAPONS.get(weapon).get();
        hold(p, InteractionHand.MAIN_HAND, item);
        if (weapon.equals("crutch")) hold(p, InteractionHand.OFF_HAND, item);
        return " holding test " + weapon;
    }

    private static void hold(ServerPlayer p, InteractionHand hand, Item item) {
        ItemStack old = p.getItemInHand(hand);
        if (old.is(item)) return;
        boolean testItem = HoldfastAnimTest.TEST_WEAPONS.values().stream().anyMatch(r -> old.is(r.get()));
        p.setItemInHand(hand, new ItemStack(item));
        if (!old.isEmpty() && !testItem && !p.getInventory().add(old)) p.drop(old, false);
    }

    private static final UUID TEST_SPEED_ID = UUID.fromString("6c1f0d52-8a7e-4f53-9b0e-0f00a11c0de1");

    /**
     * TEST ONLY stand-in for the core mod's injury slowdown (S13): while an injury movement clip plays, walking
     * speed is set to the speed its footsteps were authored for, so foot sliding can be judged. Any other clip, or
     * /hfa stop, removes it. Not a gameplay rule; the real multiplier is server state in the core mod.
     */
    private static String applyTestSpeed(ServerPlayer p, String playerClip) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return "";
        speed.removeModifier(TEST_SPEED_ID);
        Double f = Clips.MOVE_SPEED.get(playerClip);
        if (f == null || f >= 0.999 && f <= 1.001) return "";
        // Vanilla already moves a crawling (swimming-pose) player at 0.3 of walking speed.
        double attr = playerClip.equals("move_crawl_legless") ? f / 0.3 : f;
        speed.addTransientModifier(new AttributeModifier(TEST_SPEED_ID, "holdfast test injury speed", attr - 1.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        return String.format(java.util.Locale.ROOT, ", walking at %.0f%% (test only; don't sprint)", f * 100);
    }

    private static int weapons(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        for (String w : List.of("knife", "bat", "axe", "machete", "crutch")) {
            ItemStack s = new ItemStack(HoldfastAnimTest.TEST_WEAPONS.get(w).get());
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
        say(c, "gave test knife, bat, axe, machete and crutch (clips also equip them automatically)");
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
        if (!startPair(p, pr)) return 0;
        say(c, "pair " + pr.name() + ": player " + pr.playerClip() + " + " + (pr.crawler() ? "crawler " : "walker ")
                + pr.attackerClip() + " at " + pr.distPx() + " px");
        return 1;
    }

    private static boolean startPair(ServerPlayer p, Clips.Pair pr) {
        // Level the player's view so the pairing line is horizontal; yaw is kept.
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
        TestWalker w = nearest(p);
        if (w == null) w = spawnAt(p, pr.distPx() / 16.0D);
        if (w == null) return false;
        if (w.isCrawler() != pr.crawler()) w.setCrawler(pr.crawler());
        place(w, p, pr.distPx() / 16.0D);
        int nonce = w.playClip(pr.attackerClip());
        equip(p, pr.playerClip());
        applyTestSpeed(p, pr.playerClip());
        Net.send(p, new Net.Play(p.getId(), pr.playerClip(), fp(p), camera(p),
                w.getId(), w.clip(0), w.clip(1), w.clip(2), nonce, pr.crawler()));
        return true;
    }

    // ---- /hfa sweep: plays every clip of one kind in turn, numbered in chat, so a whole set can be checked by eye ----

    private static final List<String> SWEEP_KINDS = List.of("walker", "crawler", "player", "pairs");
    private static final Map<UUID, Sweep> SWEEPS = new HashMap<>();
    /** Pause after each clip so the end pose and the return to idle are visible. */
    private static final int SWEEP_GAP_TICKS = 20;
    /** Loops and very short clips play at least this long. */
    private static final int SWEEP_MIN_TICKS = 40;

    private static final class Sweep {
        final String kind;
        final List<String> items;
        int index;
        int nextTick;

        Sweep(String kind, List<String> items, int index) {
            this.kind = kind;
            this.items = items;
            this.index = index;
        }
    }

    private static List<String> sweepItems(String kind) {
        return switch (kind) {
            case "walker" -> new java.util.ArrayList<>(new TreeSet<>(Clips.WALKER.keySet()));
            case "crawler" -> new java.util.ArrayList<>(new TreeSet<>(Clips.CRAWLER.keySet()));
            case "player" -> new java.util.ArrayList<>(new TreeSet<>(Clips.PLAYER.keySet()));
            default -> Clips.PAIRS.stream().map(Clips.Pair::name).toList();
        };
    }

    private static int sweep(CommandContext<CommandSourceStack> c, int from) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        if (!SWEEP_KINDS.contains(kind)) {
            c.getSource().sendFailure(Component.literal("kind must be one of " + SWEEP_KINDS));
            return 0;
        }
        List<String> items = sweepItems(kind);
        if (from < 1 || from > items.size()) {
            c.getSource().sendFailure(Component.literal(kind + " has clips 1-" + items.size()));
            return 0;
        }
        Sweep s = new Sweep(kind, items, from - 1);
        s.nextTick = c.getSource().getServer().getTickCount();
        SWEEPS.put(p.getUUID(), s);
        say(c, "sweep " + kind + ": " + items.size() + " clips from #" + from + "; /hfa sweep stop to end");
        return 1;
    }

    private static int sweepStop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Sweep s = SWEEPS.remove(p.getUUID());
        say(c, s == null ? "no sweep running" : "sweep " + s.kind + " stopped at #" + s.index);
        return 1;
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SWEEPS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        SWEEPS.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            Sweep s = entry.getValue();
            if (p == null) return true;
            if (now < s.nextTick) return false;
            if (s.index >= s.items.size()) {
                announce(p, now, "sweep " + s.kind + " done (" + s.items.size() + " clips)");
                return true;
            }
            String item = s.items.get(s.index);
            int ticks = sweepStep(p, s.kind, item);
            s.index++;
            if (ticks < 0) {
                announce(p, now, "sweep " + s.kind + " #" + s.index + " " + item + ": could not start, skipped");
                s.nextTick = now + 1;
                return false;
            }
            announce(p, now, "sweep " + s.kind + " #" + s.index + "/" + s.items.size() + " " + item);
            s.nextTick = now + Math.max(ticks, SWEEP_MIN_TICKS) + SWEEP_GAP_TICKS;
            return false;
        });
    }

    /** Starts one sweep item and returns its length in ticks, or -1 if it could not start. */
    private static int sweepStep(ServerPlayer p, String kind, String item) {
        switch (kind) {
            case "walker", "crawler" -> {
                boolean crawler = kind.equals("crawler");
                TestWalker w = nearest(p);
                if (w == null) w = spawnAt(p, 3.0D);
                if (w == null) return -1;
                if (w.isCrawler() != crawler) w.setCrawler(crawler);
                w.playClip(item);
                return ticks((crawler ? Clips.CRAWLER : Clips.WALKER).get(item));
            }
            case "player" -> {
                equip(p, item);
                applyTestSpeed(p, item);
                Net.send(p, new Net.Play(p.getId(), item, fp(p), camera(p), -1, "", "", "", 0, false));
                return ticks(Clips.PLAYER.get(item));
            }
            default -> {
                Clips.Pair pr = Clips.PAIRS.stream().filter(x -> x.name().equals(item)).findFirst().orElse(null);
                if (pr == null || !startPair(p, pr)) return -1;
                Double attacker = (pr.crawler() ? Clips.CRAWLER : Clips.WALKER).get(pr.attackerClip());
                return Math.max(ticks(Clips.PLAYER.get(pr.playerClip())), ticks(attacker));
            }
        }
    }

    private static int ticks(Double seconds) {
        return seconds == null ? SWEEP_MIN_TICKS : (int) Math.ceil(seconds * 20.0);
    }

    private static void announce(ServerPlayer p, int tick, String msg) {
        p.sendSystemMessage(Component.literal(msg));
        HoldfastAnimTest.LOG.info("[holdfast-test] server tick {}: {}", tick, msg);
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Net.send(p, new Net.Play(p.getId(), "", fp(p), camera(p), -1, "", "", "", 0, false));
        applyTestSpeed(p, "");
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
