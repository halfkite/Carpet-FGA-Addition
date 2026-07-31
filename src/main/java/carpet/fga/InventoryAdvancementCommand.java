//#if MC >= 1.20.5
package carpet.fga;

import carpet.fga.inventoryadvancement.InventoryAdvancementManager;
import carpet.fga.inventoryadvancement.metrics.StatsCollector;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class InventoryAdvancementCommand {
    private InventoryAdvancementCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("inventoryAdvancementOptimization")
                .requires(source -> FGACompat.hasPermission(source, 4))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                .then(Commands.literal("verify").executes(context -> verify(context.getSource())))
                .then(Commands.literal("resetStats").executes(context -> resetStats(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[FGA] " + InventoryAdvancementManager.RUNTIME.status()), false);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        StatsCollector.Snapshot stats = InventoryAdvancementManager.RUNTIME.stats().snapshot();
        source.sendSuccess(() -> Component.literal(String.format(
                "[FGA] triggers=%d listeners=%d candidates=%d reduction=%.2f%% predicates=%d fullScans=%d fallbacks=%d mismatches=%d",
                stats.triggers(), stats.rawListeners(), stats.candidateListeners(), stats.reductionPercent(),
                stats.predicateTests(), stats.fullScans(), stats.fallbacks(), stats.mismatches())), false);
        return 1;
    }

    private static int verify(CommandSourceStack source) {
        InventoryAdvancementManager.RUNTIME.requestVerification();
        source.sendSuccess(() -> Component.literal("[FGA] The next trigger for each indexed player will be fully verified."), false);
        return 1;
    }

    private static int resetStats(CommandSourceStack source) {
        InventoryAdvancementManager.RUNTIME.stats().reset();
        source.sendSuccess(() -> Component.literal("[FGA] Inventory advancement optimization statistics reset."), false);
        return 1;
    }
}
//#endif
