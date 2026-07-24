package carpet.fga;

//#if MC >= 1.18
import carpet.fakes.ServerPlayerInterface;
//#else
//$$ import carpet.fakes.ServerPlayerEntityInterface;
//#endif
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RangeActionManager {
    public static final int MAX_VOLUME = 1_000_000;
    public static final double MAX_REACH = 64.0;

    private static final Map<TaskKey, RangeTask> TASKS = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/range-control");

    private RangeActionManager() {
    }

    public static boolean start(CommandSourceStack source, ServerPlayer player, Mode mode, BlockPos first,
                                BlockPos second, boolean continuous, boolean pathfinding, double reach,
                                boolean airPlace, boolean ignoreObstruction, boolean placeBlock,
                                boolean interactBlock, int interactSpeed) {
        if (!(player instanceof EntityPlayerMPFake)) {
            source.sendFailure(FGACompat.literal("区域操作只能由假人执行"));
            return false;
        }
        long sizeX = (long) Math.abs(first.getX() - second.getX()) + 1;
        long sizeY = (long) Math.abs(first.getY() - second.getY()) + 1;
        long sizeZ = (long) Math.abs(first.getZ() - second.getZ()) + 1;
        long volume = sizeX * sizeY * sizeZ;
        if (volume > MAX_VOLUME) {
            source.sendFailure(FGACompat.literal("区域体积不能超过 " + MAX_VOLUME + " 个方块"));
            return false;
        }
        InteractionHand blockHand = selectBlockHand(player);
        if (mode == Mode.USE && placeBlock && blockHand == null) {
            source.sendFailure(FGACompat.literal("假人主手或副手必须持有方块物品"));
            return false;
        }

        TaskKey key = new TaskKey(player.getUUID(), mode);
        RangeTask oldTask = TASKS.remove(key);
        if (oldTask != null) {
            oldTask.stop(player);
        }
        TASKS.put(key, new RangeTask(player, mode, first, second, continuous, pathfinding,
                reach, airPlace, ignoreObstruction, blockHand, placeBlock, interactBlock, interactSpeed));
        FGACompat.sendSuccess(source, FGACompat.literal(
                "已开始区域" + mode.label + "，共 " + volume + " 个坐标，持续=" + continuous
                        + "，寻路=" + pathfinding + "，手长=" + reach + "，凭空放置=" + airPlace
                        + "，忽略阻挡=" + ignoreObstruction), false);
        return true;
    }

    public static boolean stop(ServerPlayer player) {
        boolean stopped = false;
        for (Mode mode : Mode.values()) {
            RangeTask task = TASKS.remove(new TaskKey(player.getUUID(), mode));
            if (task != null) {
                task.stop(player);
                stopped = true;
            }
        }
        if (stopped) {
            FGACompat.actionPack(player).stopMovement();
        }
        return stopped;
    }

    public static void tick(MinecraftServer server) {
        if (!FGASettings.fakePlayerRangeControl) {
            for (RangeTask task : TASKS.values()) {
                ServerPlayer player = server.getPlayerList().getPlayer(task.playerId);
                if (player != null) {
                    task.stop(player);
                    FGACompat.actionPack(player).stopMovement();
                }
            }
            TASKS.clear();
            return;
        }
        Iterator<RangeTask> iterator = TASKS.values().iterator();
        while (iterator.hasNext()) {
            RangeTask task = iterator.next();
            try {
                if (!task.tick(server)) {
                    iterator.remove();
                }
            } catch (Throwable throwable) {
                ServerPlayer player = server.getPlayerList().getPlayer(task.playerId);
                if (player != null) {
                    task.stop(player);
                    FGACompat.actionPack(player).stopMovement();
                }
                LOGGER.error("假人 {} 的区域{}任务发生异常，任务已停止",
                        player == null ? task.playerId : player.getScoreboardName(), task.mode.label, throwable);
                iterator.remove();
            }
        }
    }

    public static void clear() {
        for (RangeTask task : TASKS.values()) {
            task.miningProgress.clear();
        }
        TASKS.clear();
    }

    public enum Mode {
        USE("放置"),
        ATTACK("破坏");

        private final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private static final class RangeTask {
        private static final Direction[] SUPPORT_DIRECTIONS = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
        };

        private final UUID playerId;
        private final Mode mode;
        private final BlockPos first;
        private final BlockPos second;
        private final List<BlockPos> targets;
        private final boolean continuous;
        private final boolean pathfinding;
        private final double reachSquared;
        private final boolean airPlace;
        private final boolean ignoreObstruction;
        private final InteractionHand blockHand;
        private final boolean placeBlock;
        private final boolean interactBlock;
        private final int interactSpeed;
        private final Map<BlockPos, MiningProgress> miningProgress = new HashMap<>();
        private int stuckTicks;
        private Vec3 lastPosition;

        private RangeTask(ServerPlayer player, Mode mode, BlockPos first, BlockPos second,
                          boolean continuous, boolean pathfinding, double reach,
                          boolean airPlace, boolean ignoreObstruction, InteractionHand blockHand,
                          boolean placeBlock, boolean interactBlock, int interactSpeed) {
            this.playerId = player.getUUID();
            this.mode = mode;
            this.first = first;
            this.second = second;
            this.continuous = continuous;
            this.pathfinding = pathfinding;
            this.reachSquared = reach * reach;
            this.airPlace = airPlace;
            this.ignoreObstruction = ignoreObstruction;
            this.blockHand = blockHand;
            this.placeBlock = placeBlock;
            this.interactBlock = interactBlock;
            this.interactSpeed = interactSpeed;
            this.targets = createTargets(first, second);
            this.lastPosition = player.position();
        }

        private boolean tick(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (!(player instanceof EntityPlayerMPFake) || !player.isAlive()) {
                return false;
            }

            ServerLevel level = FGACompat.serverLevel(player);
            if (!continuous) {
                targets.removeIf(pos -> isComplete(level, pos));
            }
            List<BlockPos> pending = continuous
                    ? targets.stream().filter(pos -> !isComplete(level, pos)).toList()
                    : targets;
            if (pending.isEmpty()) {
                stopMovement(player);
                if (continuous) {
                    miningProgress.clear();
                    return true;
                }
                //#if MC >= 1.19
                player.sendSystemMessage(FGACompat.literal("区域" + mode.label + "已完成"));
                //#else
                //$$ player.sendMessage(FGACompat.literal("区域" + mode.label + "已完成"), player.getUUID());
                //#endif
                return false;
            }

            int completed = 0;
            Iterator<BlockPos> iterator = pending.iterator();
            while (iterator.hasNext()) {
                BlockPos target = iterator.next();
                if (inReach(player, target) && perform(player, target)) {
                    if (!continuous) {
                        iterator.remove();
                    }
                    completed++;
                }
            }
            if (completed > 0 || !pathfinding) {
                stopMovement(player);
            } else {
                moveToward(player, nearestTarget(player, pending));
            }
            return true;
        }

        private boolean perform(ServerPlayer player, BlockPos target) {
            return mode == Mode.ATTACK ? attack(player, target) : use(player, target);
        }

        private boolean attack(ServerPlayer player, BlockPos target) {
            ServerLevel level = FGACompat.serverLevel(player);
            BlockState state = level.getBlockState(target);
            if (state.isAir() || player.blockActionRestricted(level, target, player.gameMode.getGameModeForPlayer())) {
                miningProgress.remove(target);
                return state.isAir();
            }
            Vec3 hitPosition = Vec3.atCenterOf(target);
            if (!hasLineOfSight(player, target, hitPosition)) {
                return false;
            }
            player.swing(InteractionHand.MAIN_HAND);
            player.resetLastActionTime();
            if (player.gameMode.getGameModeForPlayer().isCreative()) {
                player.gameMode.destroyBlock(target);
                miningProgress.remove(target);
                return level.getBlockState(target).isAir();
            }

            MiningProgress previous = miningProgress.get(target);
            float progress = previous != null && previous.state.equals(state) ? previous.progress : 0.0F;
            progress += state.getDestroyProgress(player, level, target);
            if (progress >= 1.0F) {
                player.gameMode.destroyBlock(target);
                miningProgress.remove(target);
                level.destroyBlockProgress(player.getId(), target, -1);
                return level.getBlockState(target).isAir();
            }
            miningProgress.put(target, new MiningProgress(state, progress));
            level.destroyBlockProgress(player.getId(), target, Math.min(9, (int) (progress * 10.0F)));
            return false;
        }

        private boolean use(ServerPlayer player, BlockPos target) {
            boolean targetWasAir = FGACompat.serverLevel(player).getBlockState(target).isAir();
            boolean interacted = false;
            if (interactBlock) {
                interacted = targetWasAir
                        ? interactForAirTarget(player, target)
                        : interactWithBlock(player, target);
            }
            if (!FGACompat.serverLevel(player).getBlockState(target).isAir()) {
                return interacted || !targetWasAir;
            }
            if (!placeBlock) {
                return interacted;
            }
            ItemStack stack = player.getItemInHand(blockHand);
            if (!(stack.getItem() instanceof BlockItem)) {
                return false;
            }
            for (Direction direction : SUPPORT_DIRECTIONS) {
                BlockPos support = target.relative(direction);
                BlockState supportState = FGACompat.serverLevel(player).getBlockState(support);
                if (supportState.isAir() || !supportState.getFluidState().isEmpty()) {
                    continue;
                }
                Direction face = direction.getOpposite();
                Vec3 hitPosition = Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(
                        //#if MC >= 1.21.2
                        //$$ face.getUnitVec3i()
                        //#else
                        face.getNormal()
                        //#endif
                ).scale(0.5));
                if (!hasLineOfSight(player, support, hitPosition)) {
                    continue;
                }
                BlockHitResult hit = new BlockHitResult(hitPosition, face, support, false);
                InteractionResult result = player.gameMode.useItemOn(player, FGACompat.serverLevel(player), stack,
                        blockHand, hit);
                if (result.consumesAction()) {
                    player.swing(blockHand);
                    player.resetLastActionTime();
                    return !FGACompat.serverLevel(player).getBlockState(target).isAir();
                }
            }
            if (airPlace) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, true);
                InteractionResult result = ((BlockItem) stack.getItem()).place(
                        new BlockPlaceContext(player, blockHand, stack, hit));
                if (result.consumesAction()) {
                    player.swing(blockHand);
                    player.resetLastActionTime();
                    return !FGACompat.serverLevel(player).getBlockState(target).isAir();
                }
            }
            return false;
        }

        private boolean interactForAirTarget(ServerPlayer player, BlockPos target) {
            for (Direction direction : SUPPORT_DIRECTIONS) {
                BlockPos support = target.relative(direction);
                BlockState supportState = FGACompat.serverLevel(player).getBlockState(support);
                if (supportState.isAir()) {
                    continue;
                }
                Direction face = direction.getOpposite();
                Vec3 hitPosition = Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(
                        //#if MC >= 1.21.2
                        //$$ face.getUnitVec3i()
                        //#else
                        face.getNormal()
                        //#endif
                ).scale(0.5));
                if (!hasLineOfSight(player, support, hitPosition)) {
                    continue;
                }
                return interactWithHit(player, support, face, hitPosition);
            }
            return false;
        }

        private boolean interactWithBlock(ServerPlayer player, BlockPos target) {
            Vec3 hitPosition = Vec3.atCenterOf(target);
            if (!inReach(player, target) || !hasLineOfSight(player, target, hitPosition)) {
                return false;
            }
            Vec3 direction = player.getEyePosition().subtract(hitPosition);
            Direction face =
                    //#if MC >= 1.19
                    Direction.getNearest(direction);
                    //#else
                    //$$ Direction.getNearest(direction.x, direction.y, direction.z);
                    //#endif
            return interactWithHit(player, target, face, hitPosition);
        }

        private boolean interactWithHit(ServerPlayer player, BlockPos target, Direction face, Vec3 hitPosition) {
            if (!inReach(player, target)) {
                return false;
            }
            BlockHitResult hit = new BlockHitResult(hitPosition, face, target, false);
            boolean attempted = false;
            for (int attempt = 0; attempt < interactSpeed; attempt++) {
                if (player.getMainHandItem().isEmpty()) {
                    break;
                }
                InteractionResult result = player.gameMode.useItemOn(player, FGACompat.serverLevel(player),
                        player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                attempted = true;
                if (result.consumesAction()) {
                    player.swing(InteractionHand.MAIN_HAND);
                    player.resetLastActionTime();
                }
            }
            return attempted;
        }

        private boolean hasLineOfSight(ServerPlayer player, BlockPos expectedHit, Vec3 end) {
            if (ignoreObstruction) {
                return true;
            }
            BlockHitResult hit = FGACompat.level(player).clip(new ClipContext(player.getEyePosition(), end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(expectedHit);
        }

        private boolean inReach(ServerPlayer player, BlockPos target) {
            return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= reachSquared;
        }

        private boolean isComplete(ServerLevel level, BlockPos pos) {
            if (mode == Mode.USE) {
                if (interactBlock) {
                    return false;
                }
                return !placeBlock || !level.getBlockState(pos).isAir();
            }
            return level.getBlockState(pos).isAir();
        }

        private BlockPos nearestTarget(ServerPlayer player, List<BlockPos> pending) {
            return pending.stream().min(Comparator.comparingDouble(pos ->
                    Vec3.atCenterOf(pos).distanceToSqr(player.position()))).orElseThrow();
        }

        private void moveToward(ServerPlayer player, BlockPos target) {
            EntityPlayerActionPack actionPack = FGACompat.actionPack(player);
            Vec3 destination = Vec3.atBottomCenterOf(target);
            actionPack.lookAt(new Vec3(destination.x, player.getEyeY(), destination.z));
            actionPack.setSprinting(true).setForward(1.0F);

            double moved = player.position().distanceToSqr(lastPosition);
            stuckTicks = moved < 0.0025 ? stuckTicks + 1 : 0;
            lastPosition = player.position();
            if (player.horizontalCollision || stuckTicks > 10) {
                actionPack.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
                stuckTicks = 0;
            }
        }

        private void stop(ServerPlayer player) {
            for (BlockPos pos : miningProgress.keySet()) {
                FGACompat.serverLevel(player).destroyBlockProgress(player.getId(), pos, -1);
            }
            miningProgress.clear();
        }

        private static void stopMovement(ServerPlayer player) {
            FGACompat.actionPack(player).stopMovement();
        }

        private static List<BlockPos> createTargets(BlockPos first, BlockPos second) {
            int minX = Math.min(first.getX(), second.getX());
            int maxX = Math.max(first.getX(), second.getX());
            int minY = Math.min(first.getY(), second.getY());
            int maxY = Math.max(first.getY(), second.getY());
            int minZ = Math.min(first.getZ(), second.getZ());
            int maxZ = Math.max(first.getZ(), second.getZ());
            List<BlockPos> result = new ArrayList<>((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        result.add(new BlockPos(x, y, z));
                    }
                }
            }
            return result;
        }

        private record MiningProgress(BlockState state, float progress) {
        }
    }

    private static InteractionHand selectBlockHand(ServerPlayer player) {
        if (player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        return player.getMainHandItem().getItem() instanceof BlockItem ? InteractionHand.MAIN_HAND : null;
    }

    private record TaskKey(UUID playerId, Mode mode) {
    }
}
