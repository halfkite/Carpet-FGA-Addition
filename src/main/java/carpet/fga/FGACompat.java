package carpet.fga;

import carpet.helpers.EntityPlayerActionPack;
//#if MC >= 1.19.3
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

    public static net.minecraft.server.MinecraftServer server(ServerPlayer player) {
        return serverLevel(player).getServer();
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
        //#if MC >= 1.19.3
        return ((ServerPlayerInterface) player).getActionPack();
        //#else
        //$$ return ((ServerPlayerEntityInterface) player).getActionPack();
        //#endif
    }

    public static void sendSuccess(CommandSourceStack source, Component message, boolean broadcastToOps) {
        //#if MC >= 1.20
        source.sendSuccess(() -> message, broadcastToOps);
        //#else
        //$$ source.sendSuccess(message, broadcastToOps);
        //#endif
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        //#if MC >= 1.21.11
        //$$ return source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.byId(level)));
        //#else
        return source.hasPermission(level);
        //#endif
    }

    public static ResourceLocation vanillaId(String path) {
        //#if MC >= 1.21
        return ResourceLocation.withDefaultNamespace(path);
        //#else
        //$$ return new ResourceLocation(path);
        //#endif
    }

    public static boolean isItem(ItemStack stack, Item item) {
        //#if MC >= 1.18.2
        return stack.is(item);
        //#else
        //$$ return stack.getItem() == item;
        //#endif
    }

    public static boolean isSameItemSameTags(ItemStack a, ItemStack b) {
        //#if MC >= 1.20.5
        return ItemStack.isSameItemSameComponents(a, b);
        //#elseif MC >= 1.17
        //$$ return ItemStack.isSameItemSameTags(a, b);
        //#else
        //$$ return ItemStack.isSame(a, b) && ItemStack.tagMatches(a, b);
        //#endif
    }

    public static boolean isCreative(Player player) {
        //#if MC >= 1.17
        return player.getAbilities().instabuild;
        //#else
        //$$ return player.abilities.instabuild;
        //#endif
    }

    public static Vec3 eyePosition(Entity entity) {
        //#if MC >= 1.17
        return entity.getEyePosition();
        //#else
        //$$ return entity.getEyePosition(1.0F);
        //#endif
    }

    public static void discard(Entity entity) {
        //#if MC >= 1.17
        entity.discard();
        //#else
        //$$ entity.remove();
        //#endif
    }

    public static ItemEntity createItemEntity(Level level, double x, double y, double z, ItemStack stack, double dx, double dy, double dz) {
        //#if MC >= 1.17
        return new ItemEntity(level, x, y, z, stack, dx, dy, dz);
        //#else
        //$$ ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        //$$ entity.setDeltaMovement(dx, dy, dz);
        //$$ return entity;
        //#endif
    }

    public static void copyRotation(Entity source, Entity target) {
        //#if MC >= 1.17
        target.setYRot(source.getYRot());
        target.setXRot(source.getXRot());
        //#else
        //$$ target.yRot = source.yRot;
        //$$ target.xRot = source.xRot;
        //#endif
    }
}
