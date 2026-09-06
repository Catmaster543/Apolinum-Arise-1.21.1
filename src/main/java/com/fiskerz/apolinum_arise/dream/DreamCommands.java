package com.fiskerz.apolinum_arise.dream;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Op-only tooling for exercising the dream engine without shipping any dream content.
 *
 * <pre>
 *   /dream list                          - loaded script ids + your queue
 *   /dream queue &lt;dreamId&gt;         - queue one dream for yourself
 *   /dream broadcast &lt;category&gt; &lt;dreamId&gt; - permanent category broadcast
 *   /dream now                           - skip the lie-down delay and start the next queued dream
 *   /dream stop                          - end the current dream immediately
 * </pre>
 */
public final class DreamCommands {
    private DreamCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dream")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(DreamCommands::list))
                .then(Commands.literal("queue")
                        .then(Commands.argument("dreamId", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DreamScripts.INSTANCE.ids(), builder))
                                .executes(DreamCommands::queue)))
                .then(Commands.literal("broadcast")
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        new String[]{"healthy", "infected"}, builder))
                                .then(Commands.argument("dreamId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DreamScripts.INSTANCE.ids(), builder))
                                        .executes(DreamCommands::broadcast))))
                .then(Commands.literal("peek")
                        .then(Commands.argument("chunkX", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(DreamCommands::peek))))
                .then(Commands.literal("unpeek").executes(DreamCommands::unpeek))
                .then(Commands.literal("now").executes(DreamCommands::now))
                .then(Commands.literal("stop").executes(DreamCommands::stop)));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        context.getSource().sendSuccess(() -> Component.literal(
                "Loaded dreams: " + DreamScripts.INSTANCE.ids()
                        + "\nYour queue: " + player.getData(DreamAttachments.DREAMS).queue()
                        + "\nCategory: " + DreamCategory.of(player)
                        + "\nDreaming: " + DreamManager.isDreaming(player)), false);
        return 1;
    }

    private static int queue(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String dreamId = StringArgumentType.getString(context, "dreamId");
        if (!DreamScripts.INSTANCE.exists(dreamId)) {
            context.getSource().sendFailure(Component.literal("No dream script '" + dreamId + "' is loaded."));
            return 0;
        }
        DreamManager.queueDream(player, dreamId);
        context.getSource().sendSuccess(() -> Component.literal(
                "Queued '" + dreamId + "'. Lie in a bed to play it (or /dream now)."), true);
        return 1;
    }

    private static int broadcast(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "category");
        String dreamId = StringArgumentType.getString(context, "dreamId");
        DreamCategory category;
        try {
            category = DreamCategory.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Category must be healthy or infected."));
            return 0;
        }
        if (!DreamScripts.INSTANCE.exists(dreamId)) {
            context.getSource().sendFailure(Component.literal("No dream script '" + dreamId + "' is loaded."));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        DreamManager.queueDreamForCategory(level, category, dreamId);
        context.getSource().sendSuccess(() -> Component.literal(
                "Broadcast '" + dreamId + "' to " + category + " permanently. Anyone who is ever in that "
                        + "category - including future joiners - will receive it once."), true);
        return 1;
    }

    // Phase 10c two-player test: stream a distant chunk area to the SENDER only, while their body
    // stays exactly where it is. A second player standing next to them must notice nothing.
    private static int peek(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int chunkX = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunkZ");
        net.minecraft.world.level.ChunkPos target = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
        long start = System.currentTimeMillis();
        DreamChunkLoader.begin(player, context.getSource().getLevel(), target);
        long elapsed = System.currentTimeMillis() - start;
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "Streamed %d chunk(s) around %s to you only, in %d ms (generation included). "
                        + "Your body has not moved: you are still at %s. Use /dream unpeek to restore.",
                DreamChunkLoader.heldChunkCount(player), target, elapsed, player.chunkPosition())), true);
        return 1;
    }

    private static int unpeek(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int held = DreamChunkLoader.heldChunkCount(player);
        DreamChunkLoader.end(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "Released " + held + " ticket(s) and restored your own view. Held now: "
                        + DreamChunkLoader.heldChunkCount(player)), true);
        return 1;
    }

    private static int now(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!player.isSleeping()) {
            context.getSource().sendFailure(Component.literal("You must be lying in a bed to dream."));
            return 0;
        }
        boolean started = DreamManager.startNow(player);
        if (!started) {
            context.getSource().sendFailure(Component.literal("Nothing queued (or already dreaming)."));
            return 0;
        }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DreamManager.stop(player, false);
        context.getSource().sendSuccess(() -> Component.literal("Dream stopped."), false);
        return 1;
    }
}
