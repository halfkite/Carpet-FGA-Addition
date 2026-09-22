package carpet.fga;

/** Pure rule and quantity calculations shared by full-shulker crafting paths. */
final class FullShulkerCraftingPolicy {
    private FullShulkerCraftingPolicy() {
    }

    /**
     * Clients keep the permissive preview used for servers whose Carpet rules are unknown, while
     * every logical server (including an integrated server) follows the authoritative rule value.
     */
    static boolean ruleEnabled(boolean logicalClient, String ruleValue) {
        return logicalClient || !"false".equals(ruleValue);
    }

    static boolean only64Mode(boolean logicalClient, String ruleValue) {
        return !logicalClient && "only64".equals(ruleValue);
    }

    /**
     * Every occupied crafting-grid slot supplies the same number of items, so that number is the
     * craft count. It must not be multiplied by the number of occupied or repeated ingredient slots.
     */
    static long totalOutputItems(long itemsPerInputSlot, int outputPerCraft) {
        if (itemsPerInputSlot <= 0L || outputPerCraft <= 0) return 0L;
        return Math.multiplyExact(itemsPerInputSlot, (long) outputPerCraft);
    }
}
