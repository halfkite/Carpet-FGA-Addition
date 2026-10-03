//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.*;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.minecraft.core.BlockPos;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.util.ProblemReporter;
//$$ import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
//$$ import net.minecraft.world.InteractionHand;
//$$ import net.minecraft.world.entity.EntityTypes;
//$$ import net.minecraft.world.entity.vehicle.boat.Boat;
//$$ import net.minecraft.world.entity.vehicle.minecart.Minecart;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.entity.item.ItemEntity;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.world.level.LevelHeightAccessor;
//$$ import net.minecraft.world.level.ChunkPos;
//$$ import net.minecraft.world.level.block.Blocks;
//$$ import net.minecraft.world.level.block.entity.BarrelBlockEntity;
//$$ import net.minecraft.world.level.chunk.ProtoChunk;
//$$ import net.minecraft.world.level.chunk.UpgradeData;
//$$ import net.minecraft.world.level.chunk.PalettedContainerFactory;
//$$ import net.minecraft.world.level.levelgen.NoiseSettings;
//$$ import net.minecraft.world.level.storage.TagValueInput;
//$$ import net.minecraft.world.phys.Vec3;
//$$ import net.minecraft.world.phys.AABB;
//$$
//$$ /** Test mod only: disposable world, no real player data, no client simulation claims. */
//$$ public final class PlayerVehicleWorldProbe implements DedicatedServerModInitializer {
//$$     private int ticks, phase, checks;
//$$     private boolean done;
//$$     private ServerPlayer actor;
//$$
//$$     @Override public void onInitializeServer() {
//$$         ServerLifecycleEvents.SERVER_STARTED.register(server -> {
//$$             for (String name : new String[]{"FgaFeatureA", "FgaFeatureB", "FgaFeatureC"}) {
//$$                 server.services().nameToIdCache().add(NameAndId.createOffline(name));
//$$                 EntityPlayerMPFake.createFake(name, server, new Vec3(0, 120, 0), 0, 0,
//$$                         Level.OVERWORLD, GameType.SURVIVAL, false);
//$$             }
//$$         });
//$$         ServerTickEvents.END_SERVER_TICK.register(server -> {
//$$             if (done) return;
//$$             try { tick(server); }
//$$             catch (Throwable failure) {
//$$                 done = true;
//$$                 System.err.println("FGA_NEW_FEATURES_FAIL: " + failure);
//$$                 failure.printStackTrace();
//$$                 server.halt(false);
//$$             }
//$$         });
//$$     }
//$$
//$$     private void tick(MinecraftServer server) {
//$$         if (++ticks > 600) throw new AssertionError("Probe timed out");
//$$         if (phase == 0) {
//$$             actor = server.getPlayerList().getPlayerByName("FgaFeatureA");
//$$             var b = server.getPlayerList().getPlayerByName("FgaFeatureB");
//$$             var c = server.getPlayerList().getPlayerByName("FgaFeatureC");
//$$             if (actor == null || b == null || c == null) return;
//$$             actor.setNoGravity(true); b.setNoGravity(true); c.setNoGravity(true);
//$$             if (ticks < 80) return; // Let vanilla join invulnerability expire before damage checks.
//$$             ServerLevel level = server.overworld();
//$$             actor.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
//$$             b.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
//$$             c.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
//$$             barrel(level);
//$$             bedrock(level);
//$$             vehicles(level, actor, b, c);
//$$             FGASettings.fastEating = true;
//$$             actor.stopRiding();
//$$             actor.getFoodData().setFoodLevel(0);
//$$             actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 2));
//$$             actor.startUsingItem(InteractionHand.MAIN_HAND);
//$$             require(actor.getUseItemRemainingTicks() == 8, "Bread must start with 8 ticks");
//$$             phase = 1;
//$$             ticks = 0;
//$$         } else if (phase == 1 && ticks == 8) {
//$$             require(actor.getMainHandItem().getCount() == 1, "Exactly one bread must be consumed after 8 ticks");
//$$             require(!actor.isUsingItem(), "Eating must have ended");
//$$             actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MILK_BUCKET));
//$$             actor.startUsingItem(InteractionHand.MAIN_HAND);
//$$             require(actor.getUseItemRemainingTicks() == 8, "Milk must start with 8 ticks");
//$$             phase = 2;
//$$             ticks = 0;
//$$         } else if (phase == 2 && ticks == 8) {
//$$             require(actor.getMainHandItem().is(Items.BUCKET), "Milk must produce exactly one bucket after 8 ticks");
//$$             FGASettings.fastEating = false;
//$$             actor.getFoodData().setFoodLevel(0);
//$$             actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
//$$             actor.startUsingItem(InteractionHand.MAIN_HAND);
//$$             require(actor.getUseItemRemainingTicks() == 32, "Disabled rule must preserve vanilla 32 ticks");
//$$             actor.stopUsingItem();
//$$             done = true;
//$$             System.out.println("FGA_NEW_FEATURES_PASS: " + checks + " checks; barrel slot migration, bedrock, capacities, cramming, food and drink");
//$$             server.halt(false);
//$$         }
//$$     }
//$$
//$$     private void barrel(ServerLevel level) {
//$$         BlockPos pos = new BlockPos(0, 150, 0);
//$$         FGASettings.doubleBarrelCapacity = false;
//$$         BarrelCapacityManager.load();
//$$         BarrelBlockEntity small = new BarrelBlockEntity(pos, Blocks.BARREL.defaultBlockState());
//$$         require(small.getContainerSize() == 27, "Disabled barrel must have 27 slots");
//$$         small.setItem(0, new ItemStack(Items.DIAMOND, 3));
//$$         var oldTag = small.saveWithoutMetadata(level.registryAccess());
//$$         FGASettings.doubleBarrelCapacity = true;
//$$         BarrelCapacityManager.load();
//$$         BarrelBlockEntity big = new BarrelBlockEntity(pos, Blocks.BARREL.defaultBlockState());
//$$         big.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), oldTag));
//$$         require(big.getContainerSize() == 54 && big.getItem(0).getCount() == 3, "Old barrel must expand without losing items");
//$$         big.setItem(53, new ItemStack(Items.GOLD_INGOT, 7));
//$$         var expandedTag = big.saveWithoutMetadata(level.registryAccess());
//$$         FGASettings.doubleBarrelCapacity = false;
//$$         require(big.getContainerSize() == 54, "Changing the rule live must not change the startup snapshot");
//$$         BarrelCapacityManager.load(); // Model the next startup snapshot, without reopening a real world.
//$$         BarrelBlockEntity recovered = new BarrelBlockEntity(pos, Blocks.BARREL.defaultBlockState());
//$$         recovered.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), expandedTag));
//$$         require(recovered.getContainerSize() == 54, "Disabled barrel must preserve occupied overflow");
//$$         require(recovered.getItem(53).getCount() == 7 && recovered.getItem(0).getCount() == 3, "Saved slots must survive disable and reload");
//$$         recovered.setItem(53, ItemStack.EMPTY);
//$$         require(recovered.getContainerSize() == 54, "Recovered capacity must stay stable for cached slot references");
//$$         BarrelBlockEntity reloaded = new BarrelBlockEntity(pos, Blocks.BARREL.defaultBlockState());
//$$         reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
//$$                 recovered.saveWithoutMetadata(level.registryAccess())));
//$$         require(reloaded.getContainerSize() == 27 && reloaded.getItem(0).getCount() == 3,
//$$                 "Empty overflow must restore 27 slots on the next load without losing original items");
//$$         level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
//$$         BarrelBlockEntity migrating = (BarrelBlockEntity) level.getBlockEntity(pos);
//$$         migrating.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), expandedTag));
//$$         require(actor.openMenu(migrating).isPresent() && actor.containerMenu.slots.size() == 90,
//$$                 "Stored overflow must open a six-row menu with 54 plus 36 player slots");
//$$         migrating.setItem(53, ItemStack.EMPTY);
//$$         require(migrating.getContainerSize() == 54 && migrating.getItem(53).isEmpty(),
//$$                 "Empty overflow must stay addressable while a six-row menu remains open");
//$$         actor.closeContainer();
//$$         require(migrating.getContainerSize() == 54, "Closing a menu must not invalidate transfer API slot caches");
//$$         FGASettings.doubleBarrelCapacity = true;
//$$         BarrelCapacityManager.load();
//$$         level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
//$$         BarrelBlockEntity placed = (BarrelBlockEntity) level.getBlockEntity(pos);
//$$         require(placed.getContainerSize() == 54, "Placed barrel must expose all slots");
//$$         placed.setItem(0, new ItemStack(Items.DIAMOND, 3));
//$$         placed.setItem(53, new ItemStack(Items.GOLD_INGOT, 7));
//$$         level.destroyBlock(pos, true);
//$$         var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
//$$         require(drops.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum() == 3,
//$$                 "Breaking a barrel must drop its original slots");
//$$         require(drops.stream().filter(e -> e.getItem().is(Items.GOLD_INGOT)).mapToInt(e -> e.getItem().getCount()).sum() == 7,
//$$                 "Breaking a barrel must drop occupied slot 53");
//$$         if (FabricLoader.getInstance().isModLoaded("carpet-tis-addition")) {
//$$             try {
//$$                 var rule = Class.forName("carpettisaddition.CarpetTISAdditionSettings").getField("largeBarrel");
//$$                 boolean previous = rule.getBoolean(null);
//$$                 try {
//$$                     rule.setBoolean(null, true);
//$$                     BarrelCapacityManager.load();
//$$                     require(!BarrelCapacityManager.active(), "Enabled TIS largeBarrel must disable FGA expansion at startup");
//$$                 } finally { rule.setBoolean(null, previous); }
//$$             } catch (ReflectiveOperationException e) { throw new AssertionError("TIS rule check failed", e); }
//$$         }
//$$         FGASettings.doubleBarrelCapacity = false;
//$$         BarrelCapacityManager.load();
//$$     }
//$$
//$$     private void bedrock(ServerLevel level) {
//$$         var factory = PalettedContainerFactory.create(level.registryAccess());
//$$         var chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
//$$                 LevelHeightAccessor.create(-64, 384), factory, null);
//$$         for (int y = -64; y < -59; y++) chunk.setBlockState(new BlockPos(0, y, 0), Blocks.BEDROCK.defaultBlockState(), 0);
//$$         FGASettings.flatBedrock = "true";
//$$         FlatBedrockManager.apply(chunk, Level.OVERWORLD, NoiseSettings.create(-64, 384));
//$$         require(chunk.getBlockState(new BlockPos(0, -64, 0)).is(Blocks.BEDROCK), "One floor bedrock layer");
//$$         require(chunk.getBlockState(new BlockPos(0, -63, 0)).is(Blocks.DEEPSLATE), "Excess floor bedrock becomes deepslate");
//$$         require(chunk.getBlockState(new BlockPos(1, -64, 1)).isAir(), "Generator with no bedrock stays empty");
//$$         var nether = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY,
//$$                 LevelHeightAccessor.create(0, 256), factory, null);
//$$         for (int d = 0; d < 5; d++) {
//$$             nether.setBlockState(new BlockPos(0, d, 0), Blocks.BEDROCK.defaultBlockState(), 0);
//$$             nether.setBlockState(new BlockPos(0, 127 - d, 0), Blocks.BEDROCK.defaultBlockState(), 0);
//$$         }
//$$         FGASettings.flatBedrock = "3";
//$$         FlatBedrockManager.apply(nether, Level.NETHER, NoiseSettings.create(0, 128));
//$$         require(nether.getBlockState(new BlockPos(0, 2, 0)).is(Blocks.BEDROCK), "Three Nether floor layers");
//$$         require(nether.getBlockState(new BlockPos(0, 3, 0)).is(Blocks.NETHERRACK), "Excess Nether floor becomes netherrack");
//$$         require(nether.getBlockState(new BlockPos(0, 125, 0)).is(Blocks.BEDROCK), "Three Nether roof layers");
//$$         require(nether.getBlockState(new BlockPos(0, 124, 0)).is(Blocks.NETHERRACK), "Excess Nether roof becomes netherrack");
//$$         require(nether.getBlockState(new BlockPos(0, 255, 0)).isAir(), "Roof must use noise height 128, not dimension height 256");
//$$         FGASettings.flatBedrock = "false";
//$$     }
//$$
//$$     private void vehicles(ServerLevel level, ServerPlayer a, ServerPlayer b, ServerPlayer c) {
//$$         Boat boat = new Boat(EntityTypes.OAK_BOAT, level, () -> Items.OAK_BOAT);
//$$         boat.setPos(0, 120, 0);
//$$         level.addFreshEntity(boat);
//$$         FGASettings.playerVehicleCapacity = "2";
//$$         require(a.startRiding(boat) && b.startRiding(boat), "Boat must accept two players");
//$$         require(!c.startRiding(boat), "Boat must enforce the custom limit");
//$$         FGASettings.playerVehicleCapacity = "4";
//$$         require(c.startRiding(boat), "Boat must accept a third player under limit 4");
//$$         require(boat.getFirstPassenger() == a, "Driver must remain the first passenger");
//$$         a.setInvulnerableTime(0);
//$$         a.damageCooldownTime = 0;
//$$         FGASettings.vehicleNoCramming = true;
//$$         float health = a.getHealth();
//$$         a.hurtServer(level, level.damageSources().cramming(), 6);
//$$         require(a.getHealth() == health, "Mounted passengers must not take cramming damage");
//$$         FGASettings.vehicleNoCramming = false;
//$$         a.setInvulnerableTime(0);
//$$         a.damageCooldownTime = 0;
//$$         a.hurtServer(level, level.damageSources().cramming(), 6);
//$$         require(a.getHealth() < health, "Disabled cramming protection must restore damage");
//$$         a.setHealth(20);
//$$         a.stopRiding(); b.stopRiding(); c.stopRiding();
//$$         Minecart cart = new Minecart(EntityTypes.MINECART, level);
//$$         cart.setPos(0, 120, 0);
//$$         level.addFreshEntity(cart);
//$$         require(a.startRiding(cart) && b.startRiding(cart) && c.startRiding(cart), "Rideable minecart must accept extra players");
//$$         FGASettings.vehicleJump = true;
//$$         cart.setOnGround(true);
//$$         require(((VehicleJumpAccess) cart).carpetFga$tryJump(), "Grounded cart must start a jump");
//$$         require(!((VehicleJumpAccess) cart).carpetFga$tryJump(), "Cart must reject repeated airborne jump");
//$$         a.stopRiding(); b.stopRiding(); c.stopRiding();
//$$         FGASettings.vehicleJump = false;
//$$         FGASettings.playerVehicleCapacity = "false";
//$$     }
//$$
//$$     private void require(boolean value, String message) {
//$$         checks++;
//$$         if (!value) throw new AssertionError(message);
//$$     }
//$$ }
//#endif
//$$
