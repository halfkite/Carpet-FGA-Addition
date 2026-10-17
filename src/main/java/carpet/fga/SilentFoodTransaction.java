package carpet.fga;

//#if MC >= 1.21.1
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Runs only on the authoritative server, using vanilla item completion and its side effects. */
public final class SilentFoodTransaction
{
    private static final ThreadLocal<ServerPlayer> ACTIVE = new ThreadLocal<>();
    private SilentFoodTransaction() {}
    public static boolean isSilent(LivingEntity entity) { return ACTIVE.get() == entity; }
    public static String consume(ServerPlayer player, int slot, ItemStack expected, int target, boolean pauseCombat)
    {
        if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Silent food requires server thread");
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || slot < 0 || slot >= 36
                || target < 1 || target > 20 || player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.getCarried().isEmpty() || player.isUsingItem()
                || pauseCombat && player.hurtTime > 0) return "BUSY";
        ItemStack food = player.getInventory().getItem(slot);
        if (!ItemStack.matches(food, expected)) return "STALE";
        var properties = food.get(DataComponents.FOOD);
//#if MC >= 1.21.3
//$$         var consumable = food.get(DataComponents.CONSUMABLE);
//$$         if (food.isEmpty() || properties == null || properties.nutrition() <= 0 || consumable == null
//$$                 || !consumable.canConsume(player, food) || player.getFoodData().getFoodLevel() >= target) return "NOT_NEEDED";
//#else
        if (food.isEmpty() || properties == null || properties.nutrition() <= 0
                || !player.canEat(properties.canAlwaysEat()) || player.getFoodData().getFoodLevel() >= target) return "NOT_NEEDED";
//#endif
//#if MC >= 1.21.3
//$$         if (player.getCooldowns().isOnCooldown(food)) return "COOLDOWN";
//#else
        if (player.getCooldowns().isOnCooldown(food.getItem())) return "COOLDOWN";
//#endif
//#if MC >= 1.21.3
//$$         var useRemainder = food.get(DataComponents.USE_REMAINDER);
//#if MC >= 26.1.2
//$$         ItemStack remainder = useRemainder == null ? ItemStack.EMPTY : useRemainder.convertInto().create();
//#else
//$$         ItemStack remainder = useRemainder == null ? ItemStack.EMPTY : useRemainder.convertInto().copy();
//#endif
//#else
        ItemStack remainder = properties.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY);
//#endif
        if (food.getCount() > 1 && !remainder.isEmpty()) {
            int capacity = 0;
            for (int i = 0; i < 36; i++)
            {
                ItemStack current = player.getInventory().getItem(i);
                if (current.isEmpty()) capacity += remainder.getMaxStackSize();
                else if (ItemStack.isSameItemSameComponents(current, remainder))
                    capacity += Math.max(0, current.getMaxStackSize() - current.getCount());
            }
            if (capacity < remainder.getCount()) return "NO_SPACE";
        }
        ACTIVE.set(player);
        try { player.getInventory().setItem(slot, food.finishUsingItem(player.level(), player)); }
        finally { ACTIVE.remove(); }
        player.inventoryMenu.broadcastChanges();
        return "OK";
    }
}
//#endif
