//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import org.junit.jupiter.api.*;
//$$ import net.minecraft.SharedConstants;
//$$ import net.minecraft.server.Bootstrap;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.data.registries.VanillaRegistries;
//$$ import net.minecraft.core.HolderLookup;
//$$ import net.minecraft.nbt.*;
//$$ import net.minecraft.world.item.*;
//$$ import net.minecraft.world.item.component.ItemContainerContents;
//$$ import net.minecraft.core.NonNullList;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import io.netty.buffer.Unpooled;
//$$ import java.util.*;
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ final class PlayerSortInventoryApiTest {
//$$     private static HolderLookup.Provider registry;
//$$     @BeforeAll static void bootstrap() {
//$$         SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
//$$         // 26.3 binds item defaults only after the dynamic registry/component phase.
//$$         registry=VanillaRegistries.createWorldLookup();
//$$         BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registry)
//$$                 .forEach(pending -> pending.apply());
//$$     }
//$$     private static ItemStack[] empty(int size) { ItemStack[] a=new ItemStack[size]; Arrays.fill(a,ItemStack.EMPTY); return a; }
//$$     @Test void refillIsServerHalfStack() {
//$$         assertEquals(32,PlayerSortInventoryApi.halfStack(new ItemStack(Items.STONE)));
//$$         assertEquals(8,PlayerSortInventoryApi.halfStack(new ItemStack(Items.ENDER_PEARL)));
//$$         assertEquals(1,PlayerSortInventoryApi.halfStack(new ItemStack(Items.DIAMOND_PICKAXE)));
//$$     }
//$$     @Test void mixedShulkerExtractionConservesMaterialsAndBoxMetadata() {
//$$         var inner=NonNullList.withSize(27,ItemStack.EMPTY);
//$$         inner.set(0,new ItemStack(Items.STONE,17)); inner.set(1,new ItemStack(Items.STONE,20));
//$$         inner.set(2,new ItemStack(Items.DIRT,64));
//$$         var box=new ItemStack(Items.SHULKER_BOX); box.set(DataComponents.CUSTOM_NAME,Component.literal("keep me"));
//$$         box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(inner));
//$$         ItemStack[] source=empty(37); source[0]=box.copy();
//$$         ItemStack prototype=PlayerSortInventoryApi.materialPrototype(source,"minecraft:stone");
//$$         assertEquals(37,PlayerSortInventoryApi.materialCount(source,prototype));
//$$         assertTrue(PlayerSortInventoryApi.extractMaterial(source,prototype,32));
//$$         assertEquals(5,PlayerSortInventoryApi.materialCount(source,prototype));
//$$         assertEquals("keep me",source[0].getHoverName().getString());
//$$         assertEquals(64,PlayerSortInventoryApi.materialCount(source,new ItemStack(Items.DIRT)));
//$$         var before=empty(37); before[0]=box;
//$$         assertEquals(37,PlayerSortInventoryApi.materialCount(before,prototype),"never mutate original component contents");
//$$     }
//$$     @Test void depletedBoxesRemainInSource() {
//$$         var inner=NonNullList.withSize(27,ItemStack.EMPTY); inner.set(0,new ItemStack(Items.ENDER_PEARL,8));
//$$         ItemStack[] source=empty(37); source[36]=new ItemStack(Items.SHULKER_BOX);
//$$         source[36].set(DataComponents.CONTAINER,ItemContainerContents.fromItems(inner));
//$$         assertTrue(PlayerSortInventoryApi.extractMaterial(source,new ItemStack(Items.ENDER_PEARL),8));
//$$         assertTrue(source[36].is(Items.SHULKER_BOX)); assertEquals(1,source[36].getCount());
//$$         assertEquals(0,PlayerSortInventoryApi.materialCount(source,new ItemStack(Items.ENDER_PEARL)));
//$$     }
//$$     @Test void capacityPlanRejectsInsufficientSpace() {
//$$         ItemStack[] recipient=empty(36); Arrays.setAll(recipient,i -> new ItemStack(Items.STONE,64));
//$$         recipient[35].setCount(63);
//$$         assertFalse(PlayerSortInventoryApi.planInsertion(recipient,new ItemStack(Items.STONE,32)));
//$$         recipient=empty(36); recipient[0]=new ItemStack(Items.STONE,60);
//$$         assertTrue(PlayerSortInventoryApi.planInsertion(recipient,new ItemStack(Items.STONE,32)));
//$$         assertEquals(64,recipient[0].getCount()); assertEquals(28,recipient[1].getCount());
//$$     }
//$$     @Test void differentComponentsAreNotCombined() {
//$$         ItemStack named=new ItemStack(Items.STONE,32); named.set(DataComponents.CUSTOM_NAME,Component.literal("named"));
//$$         ItemStack[] source=empty(37); source[0]=named; source[1]=new ItemStack(Items.STONE,64);
//$$         assertEquals(32,PlayerSortInventoryApi.materialCount(source,named.copyWithCount(1)));
//$$         assertTrue(PlayerSortInventoryApi.extractMaterial(source,named.copyWithCount(1),32));
//$$         assertEquals(64,source[1].getCount());
//$$     }
//$$     @Test void wireRoundTripsAndTakeContainsNoClientQuantityOrNbt() {
//$$         // Codec is checked independently of server inventory mutations.
//$$         var query=new PlayerSortInventoryPayloads.Query(1,123,2,"minecraft:stone",0);
//$$         var take=new PlayerSortInventoryPayloads.Take(1,124,"token","minecraft:stone");
//$$         var b=new FriendlyByteBuf(Unpooled.buffer());
//$$         try {
//$$             PlayerSortInventoryPayloads.Query.CODEC.encode(b,query);
//$$             assertEquals(query,PlayerSortInventoryPayloads.Query.CODEC.decode(b));
//$$             b.clear(); PlayerSortInventoryPayloads.Take.CODEC.encode(b,take);
//$$             assertEquals(take,PlayerSortInventoryPayloads.Take.CODEC.decode(b));
//$$         } finally { b.release(); }
//$$         assertEquals(List.of("version","requestId","token","itemId"),
//$$                 Arrays.stream(PlayerSortInventoryPayloads.Take.class.getRecordComponents()).map(c->c.getName()).toList());
//$$     }
//$$     @Test void oversizedInputIsRejectedByCodec() {
//$$         var b=new FriendlyByteBuf(Unpooled.buffer());
//$$         try {
//$$             assertThrows(RuntimeException.class,() -> PlayerSortInventoryPayloads.Query.CODEC.encode(b,
//$$                     new PlayerSortInventoryPayloads.Query(1,1,1,"x".repeat(257),0)));
//$$             b.clear();
//$$             assertThrows(RuntimeException.class,() -> PlayerSortInventoryPayloads.Reply.CODEC.encode(b,
//$$                     new PlayerSortInventoryPayloads.Reply(1,"x".repeat(8193))));
//$$         } finally { b.release(); }
//$$     }
//$$     @Test void insufficientFirstVariantDoesNotHideAnotherFullHalfStack() {
//$$         ItemStack[] source=empty(37);
//$$         source[0]=new ItemStack(Items.STONE,1);
//$$         source[0].set(DataComponents.CUSTOM_NAME,Component.literal("one named stone"));
//$$         source[1]=new ItemStack(Items.STONE,32);
//$$         assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.STONE),
//$$                 PlayerSortInventoryApi.materialPrototype(source,"minecraft:stone")));
//$$     }
//$$     @Test void unrelatedBoxComponentsStayExactlyUnchanged() {
//$$         ItemStack[] source=empty(37); source[0]=new ItemStack(Items.SHULKER_BOX);
//$$         source[0].remove(DataComponents.CONTAINER);
//$$         source[1]=new ItemStack(Items.STONE,32);
//$$         ItemStack before=source[0].copy();
//$$         assertTrue(PlayerSortInventoryApi.extractMaterial(source,new ItemStack(Items.STONE),32));
//$$         assertTrue(ItemStack.matches(before,source[0]));
//$$     }
//$$     @Test void modernEquipmentOffhandIsDecodedWithoutExposingArmor() {
//$$         var data=new CompoundTag(); var equipment=new CompoundTag();
//$$         var ops=registry.createSerializationContext(NbtOps.INSTANCE);
//$$         equipment.put("offhand",ItemStack.CODEC.encodeStart(ops,new ItemStack(Items.SNOWBALL,16)).getOrThrow());
//$$         equipment.put("head",ItemStack.CODEC.encodeStart(ops,new ItemStack(Items.DIAMOND_HELMET)).getOrThrow());
//$$         data.put("equipment",equipment);
//$$         var result=PlayerSortInventoryApi.decodeOfflineInventory(registry,data);
//$$         assertEquals(37,result.length); assertTrue(result[36].is(Items.SNOWBALL)); assertEquals(16,result[36].getCount());
//$$         assertTrue(Arrays.stream(result,0,36).allMatch(ItemStack::isEmpty));
//$$     }
//$$     @Test void legacyOffhandAndDuplicateMainSlotsAreRejectedBeforeSpawning() {
//$$         var data=new CompoundTag(); var inventory=new ListTag(); var entry=new CompoundTag();
//$$         entry.putByte("Slot",(byte)150); inventory.add(entry); data.put("Inventory",inventory);
//$$         assertEquals("LEGACY_OFFHAND",assertThrows(IllegalArgumentException.class,
//$$                 ()->PlayerSortInventoryApi.decodeOfflineInventory(registry,data)).getMessage());
//$$         inventory.clear(); entry=(CompoundTag)ItemStack.CODEC.encodeStart(registry.createSerializationContext(NbtOps.INSTANCE),new ItemStack(Items.STONE)).getOrThrow(); entry.putByte("Slot",(byte)0);
//$$         inventory.add(entry); inventory.add(entry.copy());
//$$         assertEquals("READ_FAILED",assertThrows(IllegalArgumentException.class,
//$$                 ()->PlayerSortInventoryApi.decodeOfflineInventory(registry,data)).getMessage());
//$$     }
//$$     @Test void offlineVehicleIsNeverLoadedByInventoryRefill() {
//$$         var data=new CompoundTag(); data.put("RootVehicle",new CompoundTag());
//$$         assertEquals("PROTECTED_TARGET",assertThrows(IllegalArgumentException.class,
//$$                 ()->PlayerSortInventoryApi.decodeOfflineInventory(registry,data)).getMessage());
//$$     }
//$$     @Test void missingCacheUsesChineseAndCustomNamingIncludingBoxes() throws Exception {
//$$         var stateField=FakePlayerItemSortConfig.class.getDeclaredField("state"); stateField.setAccessible(true);
//$$         var previous=(FakePlayerItemSortConfig.State)stateField.get(null);
//$$         var routesField=FakePlayerItemSortManager.class.getDeclaredField("ROUTES"); routesField.setAccessible(true);
//$$         @SuppressWarnings("unchecked") var routes=(Map<String,String>)routesField.get(null);
//$$         var oldRoutes=new HashMap<>(routes);
//$$         try {
//$$             routes.put("minecraft:glass|{}","old_glass");
//$$             stateField.set(null,previous.withTargetLanguage("chinese").withPrefix("").withSuffix(""));
//$$             assertTrue(FakePlayerItemSortManager.apiMaterialBases("minecraft:glass").containsAll(List.of("old_glass","玻璃","玻璃_box","杂盒")));
//$$             assertFalse(routes.containsValue("玻璃"),"speculative candidate must not register stock");
//$$             FakePlayerItemSortManager.apiRememberMaterial("玻璃","minecraft:glass");
//$$             assertEquals("old_glass",routes.get("minecraft:glass|{}"),"do not overwrite an old naming route");
//$$             stateField.set(null,previous.withTargetLanguage("custom").withPrefix("前_").withSuffix("_后")
//$$                     .withNames(Map.of("minecraft:glass","建筑 玻璃")));
//$$             assertTrue(FakePlayerItemSortManager.apiMaterialBases("minecraft:glass")
//$$                     .containsAll(List.of("前_建筑_玻璃_后","前_建筑_玻璃_box_后")));
//$$         } finally { stateField.set(null,previous); routes.clear(); routes.putAll(oldRoutes); }
//$$     }
//$$     private static UUID offlineId(String name) {
//$$         return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
//$$     }
//$$     @Test void filenameIndexFindsSparseOverflowWithoutPrimaryOrIntermediateFiles() {
//$$         assertEquals(List.of("玻璃_1","玻璃_17","玻璃_box_999"),PlayerSortInventoryApi.existingMaterialCandidates(
//$$                 List.of("玻璃","玻璃_box"),Set.of(offlineId("玻璃_1"),offlineId("玻璃_17"),offlineId("玻璃_box_999"))));
//$$         assertEquals(List.of(),PlayerSortInventoryApi.existingMaterialCandidates(List.of("玻璃"),Set.of(offlineId("other"))));
//$$     }
//$$     @Test void discoveryHasBoundsAndDistinctMissingAndShortageStatuses() {
//$$         assertThrows(IllegalArgumentException.class,()->PlayerSortInventoryApi.existingMaterialCandidates(
//$$                 Collections.nCopies(65,"name"),Set.of()));
//$$         assertEquals("NOT_FOUND",PlayerSortInventoryApi.materialFailure(false,null));
//$$         assertEquals("NOT_ENOUGH",PlayerSortInventoryApi.materialFailure(true,null));
//$$         assertEquals("UNVERIFIED_TARGET",PlayerSortInventoryApi.materialFailure(false,"UNVERIFIED_TARGET"));
//$$     }
//$$ }
//$$
//#endif
