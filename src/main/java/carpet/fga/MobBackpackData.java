package carpet.fga;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
//#if MC >= 1.21.6
//$$ import net.minecraft.world.level.storage.ValueInput;
//$$ import net.minecraft.world.level.storage.ValueOutput;
//#else
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//#endif

public final class MobBackpackData {
    public static final int SIZE = 8;
    private static final String NBT_KEY = "carpetFgaBackpack";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public NonNullList<ItemStack> items() {
        return items;
    }

//#if MC >= 1.21.6
//$$     public void save(ValueOutput output) {
//$$         if (isEmpty()) return;
//$$         ContainerHelper.saveAllItems(output.child(NBT_KEY), items);
//$$     }
//$$
//$$     public void load(ValueInput input) {
//$$         for (int index = 0; index < items.size(); index++) items.set(index, ItemStack.EMPTY);
//$$         input.child(NBT_KEY).ifPresent(child -> ContainerHelper.loadAllItems(child, items));
//$$     }
//#else
    public void save(CompoundTag output, HolderLookup.Provider registry) {
        if (isEmpty()) {
            output.remove(NBT_KEY);
            return;
        }
        ListTag serialized = new ListTag();
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            if (stack.isEmpty()) continue;
            CompoundTag encoded = (CompoundTag) stack.save(registry);
            encoded.putByte("Slot", (byte) index);
            serialized.add(encoded);
        }
        output.put(NBT_KEY, serialized);
    }

    public void load(CompoundTag input, HolderLookup.Provider registry) {
        for (int index = 0; index < items.size(); index++) items.set(index, ItemStack.EMPTY);
//#if MC >= 1.21.5
//$$         ListTag serialized = input.getListOrEmpty(NBT_KEY);
//#else
        ListTag serialized = input.getList(NBT_KEY, Tag.TAG_COMPOUND);
//#endif
        for (int index = 0; index < serialized.size(); index++) {
//#if MC >= 1.21.5
//$$             CompoundTag encoded = serialized.getCompoundOrEmpty(index);
//$$             int slot = encoded.getByteOr("Slot", (byte) -1);
//#else
            CompoundTag encoded = serialized.getCompound(index);
            int slot = encoded.getByte("Slot");
//#endif
            if (slot < 0 || slot >= items.size()) continue;
//#if MC >= 1.21.5
//$$             ItemStack stack = ItemStack.parse(registry, encoded).orElse(ItemStack.EMPTY);
//#else
            ItemStack stack = ItemStack.parseOptional(registry, encoded);
//#endif
            if (!stack.isEmpty()) items.set(slot, stack);
        }
    }
//#endif

    public void dropContents(Mob mob, ServerLevel level) {
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            if (stack.isEmpty()) continue;
//#if MC >= 1.21.2
//$$             boolean spawned = mob.spawnAtLocation(level, stack.copy()) != null;
//#else
            boolean spawned = mob.spawnAtLocation(stack.copy()) != null;
//#endif
            if (spawned) items.set(index, ItemStack.EMPTY);
        }
    }

    private boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }
}
