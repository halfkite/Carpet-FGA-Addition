package carpet.fga;

import carpet.helpers.EntityPlayerActionPack;
//#if MC >= 1.18
import carpet.fakes.ServerPlayerInterface;
//#else
//$$ import carpet.fakes.ServerPlayerEntityInterface;
//#endif
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//#if MC < 1.19
//$$ import net.minecraft.network.chat.TextComponent;
//#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class FGACompat {
    private FGACompat() {
    }

    public static MutableComponent literal(String text) {
        //#if MC >= 1.19
        return Component.literal(text);
        //#else
        //$$ return new TextComponent(text);
        //#endif
    }

    public static Level level(Entity entity) {
        //#if MC >= 1.19.4
        return entity.level();
        //#else
        //$$ return entity.level;
        //#endif
    }

    public static ServerLevel serverLevel(ServerPlayer player) {
        //#if MC >= 1.20
        return player.serverLevel();
        //#else
        //$$ return player.getLevel();
        //#endif
    }

    public static ItemStack copyWithCount(ItemStack stack, int count) {
        //#if MC >= 1.20.5
        return stack.copyWithCount(count);
        //#else
        //$$ ItemStack copy = stack.copy();
        //$$ copy.setCount(count);
        //$$ return copy;
        //#endif
    }

    public static BlockPos containing(Vec3 position) {
        //#if MC >= 1.19.4
        return BlockPos.containing(position);
        //#else
        //$$ return new BlockPos(position.x, position.y, position.z);
        //#endif
    }

    public static EntityPlayerActionPack actionPack(ServerPlayer player) {
        //#if MC >= 1.18
        return ((ServerPlayerInterface) player).getActionPack();
        //#else
        //$$ return ((ServerPlayerEntityInterface) player).getActionPack();
        //#endif
    }

    public static void sendSuccess(CommandSourceStack source, Component message, boolean broadcastToOps) {
        //#if MC >= 1.19
        source.sendSuccess(() -> message, broadcastToOps);
        //#else
        //$$ source.sendSuccess(message, broadcastToOps);
        //#endif
    }

    public static ResourceLocation vanillaId(String path) {
        //#if MC >= 1.21
        return ResourceLocation.withDefaultNamespace(path);
        //#else
        //$$ return new ResourceLocation(path);
        //#endif
    }
}
