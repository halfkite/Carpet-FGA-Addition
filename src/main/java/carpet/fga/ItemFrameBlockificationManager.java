//#if MC == 1.21.1
package carpet.fga;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.entity.EntityTickList;
import carpet.fga.mixin.ServerLevelEntityTickListAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Replaces periodic item-frame survival scans with support-block change checks. */
public final class ItemFrameBlockificationManager {
    private static final Map<ServerLevel, Map<Long, Set<ItemFrame>>> BY_BLOCK = new IdentityHashMap<>();

    private ItemFrameBlockificationManager() {
    }

    public static synchronized void register(Entity entity) {
        if (!(entity instanceof ItemFrame frame) || !(frame.level() instanceof ServerLevel level)) return;
        index(level, supportPos(frame), frame);
        index(level, frame.getPos(), frame);
    }

    public static synchronized void unregister(Entity entity) {
        if (!(entity instanceof ItemFrame frame) || !(frame.level() instanceof ServerLevel level)) return;
        Map<Long, Set<ItemFrame>> byPosition = BY_BLOCK.get(level);
        if (byPosition == null) return;
        unindex(byPosition, supportPos(frame), frame);
        unindex(byPosition, frame.getPos(), frame);
        if (byPosition.isEmpty()) BY_BLOCK.remove(level);
    }

    public static void blockChanged(ServerLevel level, BlockPos position) {
        if (!FGASettings.itemFrameBlockification) return;
        ArrayList<ItemFrame> candidates = framesAt(level, position.asLong());
        for (ItemFrame frame : candidates) {
            if (frame.isRemoved() || frame.level() != level) {
                unregister(frame);
                continue;
            }
            if (!frame.survives()) {
                frame.discard();
                frame.dropItem(null);
            }
        }
    }

    public static synchronized void rebuild(MinecraftServer server) {
        BY_BLOCK.clear();
        for (ServerLevel level : server.getAllLevels()) {
            EntityTickList tickList = ((ServerLevelEntityTickListAccessor) level)
                    .carpetFga$getEntityTickList();
            for (Entity entity : level.getAllEntities()) {
                register(entity);
                if (!(entity instanceof ItemFrame frame)) continue;
                if (FGASettings.itemFrameBlockification) {
                    tickList.remove(frame);
                } else if (level.isPositionEntityTicking(frame.blockPosition())) {
                    tickList.add(frame);
                }
            }
        }
        if (!FGASettings.itemFrameBlockification) return;
        for (Map<Long, Set<ItemFrame>> byPosition : new ArrayList<>(BY_BLOCK.values())) {
            for (Set<ItemFrame> frames : new ArrayList<>(byPosition.values())) {
                for (ItemFrame frame : new ArrayList<>(frames)) {
                    if (!frame.isRemoved() && !frame.survives()) {
                        frame.discard();
                        frame.dropItem(null);
                    }
                }
            }
        }
    }

    private static synchronized ArrayList<ItemFrame> framesAt(ServerLevel level, long support) {
        Map<Long, Set<ItemFrame>> byPosition = BY_BLOCK.get(level);
        if (byPosition == null) return new ArrayList<>();
        Set<ItemFrame> frames = byPosition.get(support);
        return frames == null ? new ArrayList<>() : new ArrayList<>(frames);
    }

    private static void index(ServerLevel level, BlockPos position, ItemFrame frame) {
        BY_BLOCK.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(position.asLong(), ignored -> new HashSet<>())
                .add(frame);
    }

    private static void unindex(Map<Long, Set<ItemFrame>> byPosition, BlockPos position, ItemFrame frame) {
        long key = position.asLong();
        Set<ItemFrame> frames = byPosition.get(key);
        if (frames == null) return;
        frames.remove(frame);
        if (frames.isEmpty()) byPosition.remove(key);
    }

    private static BlockPos supportPos(ItemFrame frame) {
        return frame.getPos().relative(frame.getDirection().getOpposite());
    }

    public static synchronized void clear() {
        BY_BLOCK.clear();
    }
}
//#endif
