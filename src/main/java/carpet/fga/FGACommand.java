package carpet.fga;

import carpet.CarpetSettings;
//#if MC >= 1.19
import carpet.utils.CommandHelper;
//#endif
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
                        //#if MC >= 1.19
                        || CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                        //#else
                        //$$ || FGACompat.hasPermission(source, 0))
                        //#endif
                .executes(FGACommand::help)
                .then(Commands.literal("help").executes(FGACommand::help))
                .then(Commands.literal("status").executes(FGACommand::status));
        redirect(root, "droppedItemStackLimit", dispatcher);
        redirect(root, "dropPreStack", dispatcher);
        redirect(root, "villagerPerformance", dispatcher);
        //#if MC >= 1.21 && MC <= 26.2
        redirect(root, "fakePlayerItemSort", dispatcher);
        redirect(root, "trialStop", dispatcher);
        //#endif
        //#if MC == 1.21.1
        redirect(root, "minecart", dispatcher);
        redirect(root, "playerLoadDistance", dispatcher);
        redirect(root, "playertpend", dispatcher);
        //#endif
        redirect(root, "vehicleStop", dispatcher);
        //#if MC >= 1.21 && MC <= 26.2
        redirect(root, "regenerateTerrain", dispatcher);
        //#endif
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
        MutableComponent out = FGACompat.literal("FGA Help / FGA 帮助\n").withStyle(ChatFormatting.GOLD);
        line(out, "/fga droppedItemStackLimit help", "地面物品堆叠上限 / ground item stack limit");
        line(out, "/fga dropPreStack help", "掉落物预堆叠 / drop pre-stacking");
        line(out, "/fga villagerPerformance help", "村民性能配置 / villager performance");
        //#if MC >= 1.21 && MC <= 26.2
        line(out, "/fga fakePlayerItemSort help", "假人物品分类 / fake-player item sorting");
        //#if MC == 1.21.1
        if (CommandHelper.canUseCommand(context.getSource(), FGASettings.minecartFeatureCommandPermission)) {
            line(out, "/fga minecart help", "矿车烟花加速与锁链列车 / firework boost and chain trains");
        }
        if ("control".equals(FGASettings.PlayerTpEndControl)) {
            line(out, "/fga playertpend help", "玩家末地门传送控制 / player End portal control");
        }
        //#endif
        line(out, "/fga regenerateTerrain help", "地形重生成与虚空清除 / regenerate or clear terrain");
        if (CommandHelper.canUseCommand(context.getSource(), FGASettings.trialStopCommandPermission)) {
            line(out, "/fga trialStop help", "试炼刷怪笼截停并刷新 / stop and refresh trial spawners");
        }
        //#endif
        line(out, "/fga vehicleStop help", "玩家离开载具急停 / stop vehicles when drivers dismount");
        line(out, "/fga playerLoadDistance help", "玩家加载距离 / per-player chunk loading distance");
        line(out, "/fga inventoryAdvancementOptimization help", "背包进度优化 / inventory advancement optimization");
        line(out, "/log playerHealth", "订阅多人列表血量 / subscribe to player-list health");
        //#if MC >= 1.21 && MC <= 26.2
        line(out, "/fga player <fake> bot_sort help", "假人分类操作 / fake-player sorting actions");
        line(out, "/fga status", "FGA 分类任务状态 / sorter task status");
        //#endif
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static void line(MutableComponent out, String command, String note) {
        out.append(FGACompat.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(FGACompat.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        //#if MC >= 1.21 && MC <= 26.2
        FGACompat.sendSuccess(context.getSource(), FGACompat.literal(FakePlayerItemSortManager.status()).withStyle(ChatFormatting.GRAY), false);
        //#else
        //$$ FGACompat.sendSuccess(context.getSource(), FGACompat.literal("Carpet FGA Addition").withStyle(ChatFormatting.GRAY), false);
        //#endif
        return 1;
    }
}
