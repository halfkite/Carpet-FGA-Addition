//#if MC == 1.21.1
package carpet.fga.smoke;

import carpet.fga.FGASettings;
import carpet.fga.ResilientPlants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.lang.reflect.Method;

/**
 * Test-only regression for the injected canSurvive method in an isolated world
 * Run fgaPlantsProbe after startup: false, true, an explicit list and [] must
 * retain vanilla terrain eligibility, including both halves of tall flowers
 * Normal ServerLevel placement and neighbor updates retain the enabled rule
 * The WorldGenRegion reads loaded fixture chunks through getChunk instead of
 * the async generation cache; canSurvive and Feature.SIMPLE_BLOCK are vanilla
 * Then summon a Carpet fake with a name no longer than 16 characters and run
 * fgaPlantsTerrainProbe <name> 12; it visits
 * twelve new terrain chunks while the rule is enabled and checks BushBlocks
 * using the original BlockBehaviour.canSurvive implementation for support
 */
public final class ResilientPlantsProbe implements DedicatedServerModInitializer {
    private static int checks;
    private static final Method VANILLA_SURVIVAL = vanillaSurvivalMethod();
    private static ServerPlayer terrainPlayer;
    private static String previousRule;
    private static int terrainTargets;
    private static int terrainIndex;
    private static int terrainWait;
    private static int terrainChunks;
    private static int terrainBushes;
    private static int unsupportedBushes;

    private static Method vanillaSurvivalMethod() {
        try {
            Method method = BlockBehaviour.class.getDeclaredMethod(
                    "canSurvive", BlockState.class, LevelReader.class, BlockPos.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("fgaPlantsProbe").executes(context -> {
                    String previous = FGASettings.resilientPlants;
                    checks = 0;
                    try {
                        run(context.getSource().getLevel());
                        System.out.println("FGA_PLANTS_PROBE_PASS: checks=" + checks
                                + " worldgen=true runtime=true updates=true");
                        return 1;
                    } catch (Exception failure) {
                        System.out.println("FGA_PLANTS_PROBE_FAIL: " + failure);
                        failure.printStackTrace();
                        return 0;
                    } finally {
                        rule(previous);
                    }
                })));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("fgaPlantsTerrainProbe")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 12))
                                        .executes(context -> startTerrainScan(
                                                context.getSource().getServer(),
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "chunks")))))));
        ServerTickEvents.END_SERVER_TICK.register(ResilientPlantsProbe::tickTerrainScan);
    }

    private static void rule(String value) {
        FGASettings.resilientPlants = ResilientPlants.validate(value);
        ResilientPlants.setConfiguredBlocks(FGASettings.resilientPlants);
    }

    private static void require(boolean result, String message) {
        ++checks;
        if (!result) throw new IllegalStateException(message);
    }

    private static boolean feature(WorldGenRegion region, ServerLevel level, BlockPos pos, BlockState state) {
        return Feature.SIMPLE_BLOCK.place(new SimpleBlockConfiguration(BlockStateProvider.simple(state)),
                region, level.getChunkSource().getGenerator(), level.random, pos);
    }

    private static void run(ServerLevel level) {
        BlockPos pos = new BlockPos(8, 200, 8);
        level.getChunk(pos);
        WorldGenRegion region = new WorldGenRegion(level, null,
                ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), level.getChunk(pos)) {
            @Override
            public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
                // Earlier-status requests on a live server may return a
                // read-only ImposterProtoChunk; fixtures need the loaded,
                // writable full chunk so successful features really place
                return level.getChunk(x, z);
            }
        };
        BlockState flower = Blocks.DANDELION.defaultBlockState();
        BlockState tall = Blocks.SUNFLOWER.defaultBlockState();
        for (String mode : new String[]{"false", "true", "[dandelion,sunflower]", "[]"}) {
            rule(mode);
            boolean enabled = mode.equals("true") || mode.startsWith("[dandelion");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
            require(!flower.canSurvive(region, pos), mode + ": generation allowed a floating flower");
            require(!tall.canSurvive(region, pos), mode + ": generation allowed a floating tall flower");
            require(!feature(region, level, pos, flower), mode + ": simple feature generated a floating flower");
            require(!feature(region, level, pos, tall), mode + ": simple feature generated a floating sunflower");
            require(region.getBlockState(pos).isAir() && region.getBlockState(pos.above()).isAir(),
                    mode + ": rejected feature left a block behind");
            require(flower.canSurvive(level, pos) == enabled, mode + ": normal survival behavior changed");
            require(Blocks.WHEAT.defaultBlockState().canSurvive(level, pos) == mode.equals("true"),
                    mode + ": list matching changed");

            level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 2);
            require(flower.canSurvive(region, pos), mode + ": supported worldgen flower rejected");
            require(feature(region, level, pos, flower), mode + ": supported feature rejected");
            require(region.getBlockState(pos).is(Blocks.DANDELION), mode + ": feature did not place flower");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            require(feature(region, level, pos, tall), mode + ": supported tall flower rejected");
            require(region.getBlockState(pos).is(Blocks.SUNFLOWER)
                            && region.getBlockState(pos.above()).is(Blocks.SUNFLOWER),
                    mode + ": supported tall flower incomplete");

            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos, flower, 2);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
            require(level.getBlockState(pos).is(Blocks.DANDELION) == enabled,
                    mode + ": neighbor-update survival changed");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static int startTerrainScan(MinecraftServer server, ServerPlayer player, int chunks) {
        if (terrainPlayer != null) throw new IllegalStateException("terrain scan already running");
        previousRule = FGASettings.resilientPlants;
        rule("true");
        terrainPlayer = player;
        terrainTargets = chunks;
        terrainIndex = 0;
        terrainWait = 0;
        terrainChunks = 0;
        terrainBushes = 0;
        unsupportedBushes = 0;
        System.out.println("FGA_PLANTS_TERRAIN_BEGIN: chunks=" + chunks + " rule=true fake=" + player.getGameProfile().getName());
        teleportToNextTarget();
        return 1;
    }

    private static void teleportToNextTarget() {
        // Keep adjacent tickets close so the dedicated-server fixture does
        // not generate hundreds of unrelated chunks merely for this scan
        int x = 4096 + (terrainIndex % 4) * 32 + 8;
        int z = -2048 + (terrainIndex / 4) * 32 + 8;
        terrainPlayer.teleportTo(terrainPlayer.serverLevel(), x + 0.5, 150, z + 0.5, 0, 0);
        terrainWait = 0;
    }

    private static void tickTerrainScan(MinecraftServer server) {
        if (terrainPlayer == null) return;
        if (!terrainPlayer.isAlive() || !terrainPlayer.connection.isAcceptingMessages()) {
            finishTerrainScan("fake player disconnected before scan finished");
            return;
        }
        ServerLevel level = terrainPlayer.serverLevel();
        int chunkX = terrainPlayer.chunkPosition().x;
        int chunkZ = terrainPlayer.chunkPosition().z;
        ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            if (++terrainWait > 600) finishTerrainScan("timed out waiting for generated chunk " + chunkX + "," + chunkZ);
            return;
        }

        inspectGeneratedChunk(level, chunk);
        ++terrainChunks;
        ++terrainIndex;
        if (terrainIndex >= terrainTargets) {
            if (terrainBushes == 0) {
                finishTerrainScan("generated chunks contained no BushBlock candidates to verify");
                return;
            }
            if (unsupportedBushes != 0) {
                finishTerrainScan("vanilla survival rejected " + unsupportedBushes + " generated BushBlock(s)");
                return;
            }
            System.out.println("FGA_PLANTS_TERRAIN_PASS: chunks=" + terrainChunks + " bushes=" + terrainBushes
                    + " unsupported=" + unsupportedBushes + " rule=true fake=" + terrainPlayer.getGameProfile().getName());
            finishTerrainScan(null);
        } else {
            teleportToNextTarget();
        }
    }

    private static void inspectGeneratedChunk(ServerLevel level, ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + 16; ++x) {
            for (int z = minZ; z < minZ + 16; ++z) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); ++y) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (!(state.getBlock() instanceof BushBlock)) continue;
                    ++terrainBushes;
                    try {
                        if (!(boolean) VANILLA_SURVIVAL.invoke(state.getBlock(), state, level, pos.immutable())) {
                            ++unsupportedBushes;
                            if (unsupportedBushes <= 20) {
                                System.out.println("FGA_PLANTS_TERRAIN_FLOATING: " + state + " at " + pos.immutable());
                            }
                        }
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException("cannot evaluate vanilla plant support", failure);
                    }
                }
            }
        }
    }

    private static void finishTerrainScan(String failure) {
        String fakeName = terrainPlayer == null ? "unknown" : terrainPlayer.getGameProfile().getName();
        if (failure != null) System.out.println("FGA_PLANTS_TERRAIN_FAIL: " + failure);
        if (terrainPlayer != null && terrainPlayer.isAlive()) {
            terrainPlayer.connection.disconnect(Component.literal("FGA terrain smoke test complete"));
        }
        if (previousRule != null) rule(previousRule);
        System.out.println("FGA_PLANTS_TERRAIN_END: fake=" + fakeName + " chunks=" + terrainChunks
                + " bushes=" + terrainBushes + " unsupported=" + unsupportedBushes);
        terrainPlayer = null;
        previousRule = null;
    }
}
//#endif
