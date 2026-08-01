package com.fiskerz.apolinum_arise.downed.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Live pose-tuning tool (client command, no external 3D editor needed). Preview a variant on your own
 * model, nudge one body part's rotation at a time in real time, then export ready-to-paste JSON for that
 * pose's file. Client-side only (it edits this client's live pose data), so it needs no server op check.
 *
 * <pre>
 *   /downedpose preview &lt;0-2&gt;                 - show that pose on your own model (forces 3rd person)
 *   /downedpose preview off                        - stop previewing
 *   /downedpose set &lt;part&gt; &lt;x|y|z&gt; &lt;deg&gt;   - set a part rotation on the previewed pose
 *   /downedpose adjust &lt;part&gt; &lt;x|y|z&gt; &lt;deg&gt;- add to a part rotation
 *   /downedpose root &lt;rotation|offset&gt; &lt;x|y|z&gt; &lt;v&gt;    - set the whole-body transform
 *   /downedpose rootadjust &lt;rotation|offset&gt; &lt;x|y|z&gt; &lt;v&gt; - add to the whole-body transform
 *   /downedpose export                             - print + copy the previewed pose's JSON to clipboard
 *   /downedpose reset                              - discard live edits on the previewed pose
 *   /downedpose reload                             - reload all poses from files
 * </pre>
 */
public final class DownedPoseCommand {
    private DownedPoseCommand() {}

    private static final String[] PARTS = DownedPose.PART_NAMES.toArray(new String[0]);
    private static final String[] AXES = {"x", "y", "z"};

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("downedpose");

        root.then(Commands.literal("preview")
                .then(Commands.literal("off").executes(ctx -> {
                    DownedPoses.setPreviewVariant(-1);
                    return feedback(ctx, "Preview off.");
                }))
                .then(Commands.argument("variant", IntegerArgumentType.integer(0, DownedPoses.COUNT - 1))
                        .executes(ctx -> {
                            int variant = IntegerArgumentType.getInteger(ctx, "variant");
                            DownedPoses.setPreviewVariant(variant);
                            return feedback(ctx, "Previewing pose " + variant + " (3rd person forced).");
                        })));

        root.then(Commands.literal("set")
                .then(part().then(axis().then(Commands.argument("deg", FloatArgumentType.floatArg())
                        .executes(ctx -> setPart(ctx, false))))));
        root.then(Commands.literal("adjust")
                .then(part().then(axis().then(Commands.argument("deg", FloatArgumentType.floatArg())
                        .executes(ctx -> setPart(ctx, true))))));

        root.then(Commands.literal("root")
                .then(rootField().then(axis().then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> setRoot(ctx, false))))));
        root.then(Commands.literal("rootadjust")
                .then(rootField().then(axis().then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> setRoot(ctx, true))))));

        root.then(Commands.literal("export").executes(DownedPoseCommand::export));
        root.then(Commands.literal("reset").executes(ctx -> {
            int variant = requirePreview(ctx);
            if (variant < 0) {
                return 0;
            }
            DownedPoses.clearOverride(variant);
            return feedback(ctx, "Reverted pose " + variant + " to its file value.");
        }));
        root.then(Commands.literal("reload").executes(ctx -> {
            Minecraft.getInstance().reloadResourcePacks();
            return feedback(ctx, "Reloading resource packs (poses reload with them).");
        }));

        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> part() {
        return Commands.argument("part", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(PARTS, builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> axis() {
        return Commands.argument("axis", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AXES, builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rootField() {
        return Commands.argument("field", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"rotation", "offset"}, builder));
    }

    private static int setPart(CommandContext<CommandSourceStack> ctx, boolean adjust) {
        int variant = requirePreview(ctx);
        if (variant < 0) {
            return 0;
        }
        String partName = StringArgumentType.getString(ctx, "part");
        int axisIndex = axisIndex(ctx);
        if (!DownedPose.PART_NAMES.contains(partName) || axisIndex < 0) {
            ctx.getSource().sendFailure(Component.literal("Unknown part or axis."));
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, "deg");
        float[] rotation = DownedPoses.editable(variant).part(partName);
        rotation[axisIndex] = adjust ? rotation[axisIndex] + value : value;
        return feedback(ctx, partName + "." + AXES[axisIndex] + " = " + rotation[axisIndex] + " deg");
    }

    private static int setRoot(CommandContext<CommandSourceStack> ctx, boolean adjust) {
        int variant = requirePreview(ctx);
        if (variant < 0) {
            return 0;
        }
        String field = StringArgumentType.getString(ctx, "field");
        int axisIndex = axisIndex(ctx);
        if (axisIndex < 0 || (!field.equals("rotation") && !field.equals("offset"))) {
            ctx.getSource().sendFailure(Component.literal("Unknown field or axis."));
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, "value");
        DownedPose pose = DownedPoses.editable(variant);
        float[] target = field.equals("rotation") ? pose.rootRotation : pose.rootOffset;
        target[axisIndex] = adjust ? target[axisIndex] + value : value;
        return feedback(ctx, "root." + field + "." + AXES[axisIndex] + " = " + target[axisIndex]);
    }

    private static int export(CommandContext<CommandSourceStack> ctx) {
        int variant = requirePreview(ctx);
        if (variant < 0) {
            return 0;
        }
        String json = DownedPoses.get(variant).toJsonString();
        Minecraft.getInstance().keyboardHandler.setClipboard(json);
        ctx.getSource().sendSuccess(() -> Component.literal("pose_" + variant + ".json (copied to clipboard):\n" + json), false);
        return 1;
    }

    private static int axisIndex(CommandContext<CommandSourceStack> ctx) {
        String axis = StringArgumentType.getString(ctx, "axis").toLowerCase();
        return switch (axis) {
            case "x", "pitch" -> 0;
            case "y", "yaw" -> 1;
            case "z", "roll" -> 2;
            default -> -1;
        };
    }

    private static int requirePreview(CommandContext<CommandSourceStack> ctx) {
        int variant = DownedPoses.previewVariant();
        if (variant < 0) {
            ctx.getSource().sendFailure(Component.literal("Select a pose first with /downedpose preview <0-2>."));
        }
        return variant;
    }

    private static int feedback(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal("[downedpose] " + message), false);
        return 1;
    }
}
