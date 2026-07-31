//#if MC == 1.21.1
package carpet.fga;

import carpet.CarpetSettings;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Readable index plus redirects to the FGA-owned root commands. */
public final class FGACommand {
    private FGACommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("fga")
                .requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer
                        || CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                .executes(FGACommand::help)
                .then(Commands.literal("help").executes(FGACommand::help))
                .then(Commands.literal("status").executes(FGACommand::status));
        redirect(root, "droppedItemStackLimit", dispatcher);
        redirect(root, "dropPreStack", dispatcher);
        redirect(root, "villagerPerformance", dispatcher);
        redirect(root, "fakePlayerItemSort", dispatcher);
        redirect(root, "inventoryAdvancementOptimization", dispatcher);
        redirect(root, "player", dispatcher);
        dispatcher.register(root);
    }

    private static void redirect(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root,
            String name, CommandDispatcher<CommandSourceStack> dispatcher) {
        var target = dispatcher.getRoot().getChild(name);
        if (target != null) root.then(Commands.literal(name).redirect(target));
    }

    private static int help(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        MutableComponent out = Component.literal("FGA Help / FGA 帮助\n").withStyle(ChatFormatting.GOLD);
        line(out, "/fga droppedItemStackLimit help", "地面物品堆叠上限 / ground item stack limit");
        line(out, "/fga dropPreStack help", "掉落物预堆叠 / drop pre-stacking");
        line(out, "/fga villagerPerformance help", "村民性能配置 / villager performance");
        line(out, "/fga fakePlayerItemSort help", "假人物品分类 / fake-player item sorting");
        line(out, "/fga inventoryAdvancementOptimization help", "背包进度优化 / inventory advancement optimization");
        line(out, "/log playerHealth", "订阅多人列表血量 / subscribe to player-list health");
        line(out, "/fga player <fake> bot_sort help", "假人分类操作 / fake-player sorting actions");
        line(out, "/fga status", "FGA 分类任务状态 / sorter task status");
        context.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    private static void line(MutableComponent out, String command, String note) {
        out.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal(FakePlayerItemSortManager.status()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
//#endif
