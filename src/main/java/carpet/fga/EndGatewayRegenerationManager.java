//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import net.minecraft.core.BlockPos;
//#if MC == 1.21.1
import net.minecraft.core.HolderLookup;
//#endif
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
//#if MC == 1.21.1
import net.minecraft.util.datafix.DataFixTypes;
//#endif
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
//#if MC == 1.21.1
import net.minecraft.world.level.saveddata.SavedData;
//#endif

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.IdentityHashMap;

/** Tracks the original twenty End gateways without regenerating their surroundings. */
public final class EndGatewayRegenerationManager {
    private static final String DATA_NAME = "carpet_fga_end_gateways";
    private static final int MAX_GATEWAYS = 20;
    //#if MC == 1.21.1
    private static final SavedData.Factory<GatewayData> FACTORY = new SavedData.Factory<>(
            GatewayData::new, GatewayData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    //#else
    //$$ private static final Map<ServerLevel, GatewayData> NEW_API_DATA = new IdentityHashMap<>();
    //#endif

    private EndGatewayRegenerationManager() {
    }

    public static void record(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(Level.END)) return;
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof TheEndGatewayBlockEntity gateway)) return;
        GatewayData data = data(level);
        BlockPos immutable = position.immutable();
        if (data.gateways.containsKey(immutable) || data.gateways.size() >= MAX_GATEWAYS) return;
        //#if MC == 1.21.1
        data.gateways.put(immutable, gateway.saveCustomOnly(level.registryAccess()));
        data.setDirty();
        //#else
        //$$ data.gateways.put(immutable, new CompoundTag());
        //#endif
    }

    public static void tick(MinecraftServer server) {
        if (!FGASettings.endGatewayRegeneration) return;
        ServerLevel level = server.getLevel(Level.END);
        if (level == null) return;
        GatewayData data = data(level);
        for (Map.Entry<BlockPos, CompoundTag> entry : data.gateways.entrySet()) {
            BlockPos position = entry.getKey();
            if (!level.getBlockState(position).isAir()) continue;
            level.setBlock(position, Blocks.END_GATEWAY.defaultBlockState(), 3);
            //#if MC >= 1.21
            TheEndGatewayBlockEntity gateway = new TheEndGatewayBlockEntity(
                    position, Blocks.END_GATEWAY.defaultBlockState());
            //#if MC == 1.21.1
            gateway.loadCustomOnly(entry.getValue().copy(), level.registryAccess());
            //#endif
            level.setBlockEntity(gateway);
            //#endif
            level.sendBlockUpdated(position, Blocks.END_GATEWAY.defaultBlockState(),
                    Blocks.END_GATEWAY.defaultBlockState(), 3);
        }
    }

    public static void clear() {
        //#if MC != 1.21.1
        //$$ NEW_API_DATA.clear();
        //#endif
    }

    private static GatewayData data(ServerLevel level) {
        //#if MC == 1.21.1
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        //#else
        //$$ return NEW_API_DATA.computeIfAbsent(level, ignored -> new GatewayData());
        //#endif
    }

    private static final class GatewayData
            //#if MC == 1.21.1
            extends SavedData
            //#endif
    {
        private final Map<BlockPos, CompoundTag> gateways = new LinkedHashMap<>();

        //#if MC == 1.21.1
        private static GatewayData load(CompoundTag tag, HolderLookup.Provider provider) {
            GatewayData data = new GatewayData();
            ListTag list = tag.getList("gateways", 10);
            for (int index = 0; index < list.size() && data.gateways.size() < MAX_GATEWAYS; index++) {
                CompoundTag entry = list.getCompound(index);
                NbtUtils.readBlockPos(entry, "pos").ifPresent(position ->
                        data.gateways.put(position.immutable(), entry.getCompound("data").copy()));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Map.Entry<BlockPos, CompoundTag> entry : gateways.entrySet()) {
                CompoundTag value = new CompoundTag();
                value.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
                value.put("data", entry.getValue().copy());
                list.add(value);
            }
            tag.put("gateways", list);
            return tag;
        }
        //#endif
    }
}
//#endif
