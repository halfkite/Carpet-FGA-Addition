//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.*;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import com.google.gson.*;
//$$ import net.minecraft.core.NonNullList;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.nbt.*;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.world.item.*;
//$$ import net.minecraft.world.item.component.ItemContainerContents;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.storage.LevelResource;
//$$ import java.lang.reflect.Field;
//$$ import java.util.*;
//$$
//$$ /** Disposable-world regression: no cache, Chinese primary, custom boxed sparse overflow, failure reasons. */
//$$ public final class FakePlayerItemSortProbeDiscovery {
//$$     private static final String GLASS="minecraft:glass", SPARSE="前_建材_box_后_17";
//$$     private static MinecraftServer server;
//$$     private static ServerPlayer actor;
//$$     private static int stage=-1;
//$$     private static long next,deadline,requestId;
//$$     private static String waitingSpawn,waitingLogout,token,expected;
//$$     private static PlayerSortInventoryPayloads.Reply reply;
//$$     private static boolean awaiting;
//$$     private FakePlayerItemSortProbeDiscovery() {}
//$$     @SuppressWarnings("unchecked") private static Map<String,String> routes() throws Exception {
//$$         Field field=FakePlayerItemSortManager.class.getDeclaredField("ROUTES"); field.setAccessible(true);
//$$         return (Map<String,String>)field.get(null);
//$$     }
//$$     public static int start(MinecraftServer current,String name) {
//$$         if(stage>=0) throw new IllegalStateException("discovery probe already running");
//$$         server=current; actor=current.getPlayerList().getPlayerByName(name);
//$$         require(actor instanceof EntityPlayerMPFake,"actor must be test fake");
//$$         try {
//$$             FakePlayerItemSortConfig.setOption("targetLanguage","chinese");
//$$             FakePlayerItemSortConfig.setPrefix(""); FakePlayerItemSortConfig.setFormat(false,"_");
//$$             // The API uses the same prefix/suffix config; reset suffix exactly for the Chinese primary.
//$$             Field state=FakePlayerItemSortConfig.class.getDeclaredField("state"); state.setAccessible(true);
//$$             var value=(FakePlayerItemSortConfig.State)state.get(null);
//$$             var suffix=value.getClass().getDeclaredMethod("withSuffix",String.class); suffix.setAccessible(true);
//$$             state.set(null,suffix.invoke(value,""));
//$$             routes().entrySet().removeIf(entry->entry.getKey().contains("minecraft:glass") || entry.getKey().equals("@fga:mixed-boxes"));
//$$             FakePlayerItemSortConfig.setPermission("stock",name,true);
//$$             FakePlayerItemSortConfig.setPermission("inventoryTake",name,true);
//$$             clearActor(); stage=0; awaiting=false; spawn("玻璃");
//$$             System.out.println("FGA_SORT_PROBE_BEGIN: inventory-discovery"); return 1;
//$$         } catch(Exception e) { stage=-1; throw new IllegalStateException(e); }
//$$     }
//$$     private static void require(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
//$$     private static void clearActor() {
//$$         for(int i=0;i<36;i++) actor.getInventory().setItem(i,ItemStack.EMPTY);
//$$         actor.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
//$$         var box=new ItemStack(Items.SHULKER_BOX); var contents=NonNullList.withSize(27,ItemStack.EMPTY);
//$$         contents.set(0,new ItemStack(Items.DIRT,64)); box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));
//$$         actor.getInventory().setItem(35,box);
//$$     }
//$$     private static void spawn(String name) {
//$$         server.services().nameToIdCache().add(NameAndId.createOffline(name));
//$$         require(EntityPlayerMPFake.createFake(name,server,actor.position(),0,0,actor.level().dimension(),GameType.SPECTATOR,false),"spawn refused");
//$$         waitingSpawn=name; deadline=System.currentTimeMillis()+20_000;
//$$     }
//$$     private static ItemStack[] disk(String name) throws Exception {
//$$         var uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
//$$         var tag=NbtIo.readCompressed(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid+".dat"),NbtAccounter.create(8_388_608));
//$$         require(name.equals(tag.getStringOr("fgaOfflineSorterName","")),"identity marker missing");
//$$         var result=new ItemStack[36]; Arrays.fill(result,ItemStack.EMPTY);
//$$         var inventory=tag.getListOrEmpty("Inventory");
//$$         for(int i=0;i<inventory.size();i++) { var entry=inventory.getCompoundOrEmpty(i); int slot=entry.getByteOr("Slot",(byte)-1);
//$$             if(slot>=0 && slot<36) result[slot]=ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),entry).getOrThrow(); }
//$$         return result;
//$$     }
//$$     private static long glass(ItemStack stack) {
//$$         long total=stack.is(Items.GLASS) ? stack.getCount() : 0;
//$$         var container=stack.get(DataComponents.CONTAINER);
//$$         if(container!=null) for(ItemStack value:container.nonEmptyItemCopyStream().toList()) if(value.is(Items.GLASS)) total+=value.getCount();
//$$         return total;
//$$     }
//$$     private static long actorGlass() { long total=0; for(int i=0;i<36;i++) total+=glass(actor.getInventory().getItem(i)); return total; }
//$$     private static void query(String item) {
//$$         reply=null; awaiting=true; deadline=System.currentTimeMillis()+20_000;
//$$         PlayerSortInventoryApi.query(server,actor,new PlayerSortInventoryPayloads.Query(1,requestId++,2,item,0),result->reply=result);
//$$     }
//$$     private static void take() {
//$$         reply=null; awaiting=true; deadline=System.currentTimeMillis()+20_000;
//$$         PlayerSortInventoryApi.take(server,actor,new PlayerSortInventoryPayloads.Take(1,requestId++,token,GLASS),result->reply=result);
//$$     }
//$$     public static void tick(MinecraftServer current) {
//$$         if(stage<0 || server!=current) return;
//$$         long now=System.currentTimeMillis();
//$$         try {
//$$             if(waitingSpawn!=null) {
//$$                 var source=server.getPlayerList().getPlayerByName(waitingSpawn);
//$$                 if(source==null) { require(now<deadline,"fixture spawn timeout"); return; }
//$$                 for(int i=0;i<36;i++) source.getInventory().setItem(i,ItemStack.EMPTY);
//$$                 if(stage==0) for(int i=0;i<27;i++) source.getInventory().setItem(i,new ItemStack(Items.GLASS,64));
//$$                 else {
//$$                     var box=new ItemStack(Items.SHULKER_BOX); var contents=NonNullList.withSize(27,ItemStack.EMPTY);
//$$                     contents.set(0,new ItemStack(Items.GLASS,33)); box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));
//$$                     box.set(DataComponents.CUSTOM_NAME,Component.literal("retain box")); source.getInventory().setItem(0,box);
//$$                 }
//$$                 require(actorGlass()==0,"actor or carried box already contains glass");
//$$                 ((EntityPlayerMPFake)source).kill(source.level()); waitingLogout=waitingSpawn; waitingSpawn=null; return;
//$$             }
//$$             if(waitingLogout!=null) {
//$$                 if(server.getPlayerList().getPlayerByName(waitingLogout)!=null) { require(now<deadline,"fixture logout timeout"); return; }
//$$                 require(Arrays.stream(disk(waitingLogout)).mapToLong(FakePlayerItemSortProbeDiscovery::glass).sum()==(stage==0 ? 1728 : 33),"fixture quantity wrong");
//$$                 waitingLogout=null; next=now+1100;
//$$             }
//$$             if(awaiting) {
//$$                 if(reply==null) { require(now<deadline,"query timeout"); return; }
//$$                 var result=JsonParser.parseString(reply.json()).getAsJsonObject();
//$$                 require(expected.equals(result.get("status").getAsString()),"stage "+stage+" got "+result);
//$$                 if(stage==1 || stage==3) {
//$$                     String name=stage==1 ? "玻璃" : SPARSE;
//$$                     if(server.getPlayerList().getPlayerByName(name)!=null) { require(now<deadline,"temporary fake cleanup timeout"); return; }
//$$                     var stock=disk(name); long remaining=Arrays.stream(stock).mapToLong(FakePlayerItemSortProbeDiscovery::glass).sum();
//$$                     require(actorGlass()==32 && remaining==(stage==1 ? 1696 : 1),"material conservation failed");
//$$                     require(routes().containsValue(stage==1 ? "玻璃" : "前_建材_box_后"),"discovered route not registered");
//$$                     if(stage==3) require("retain box".equals(stock[0].getHoverName().getString()),"box metadata lost");
//$$                 }
//$$                 if(result.has("token")) {
//$$                     token=result.get("token").getAsString();
//$$                     require(result.get("target").getAsString().equals(stage==0 ? "玻璃" : SPARSE),"wrong discovery source");
//$$                 }
//$$                 if(stage==5) {
//$$                     FakePlayerItemSortConfig.setPermission("inventoryTake",actor.getGameProfile().name(),false);
//$$                     System.out.println("FGA_SORT_PROBE_PASS: inventory-discovery chinese=32 remaining=1696 sparse-box=32 missing=true shortage=true conserved=true");
//$$                     stage=-1; return;
//$$                 }
//$$                 stage++; awaiting=false; next=now+1100; return;
//$$             }
//$$             if(now<next) return;
//$$             expected="OK";
//$$             switch(stage) {
//$$                 case 0 -> { requestId=200; query(GLASS); }
//$$                 case 1 -> take();
//$$                 case 2 -> {
//$$                     clearActor(); FakePlayerItemSortConfig.setOption("targetLanguage","custom");
//$$                     FakePlayerItemSortConfig.setName(GLASS,"建材"); FakePlayerItemSortConfig.setPrefix("前_");
//$$                     FakePlayerItemSortConfig.setFormat(false,"_后");
//$$                     routes().entrySet().removeIf(entry->entry.getKey().contains(GLASS) || entry.getKey().equals("@fga:mixed-boxes"));
//$$                     spawn(SPARSE); stage=6;
//$$                 }
//$$                 case 6 -> { stage=2; query(GLASS); }
//$$                 case 3 -> take();
//$$                 case 4 -> { expected="NOT_ENOUGH"; query(GLASS); }
//$$                 case 5 -> { expected="NOT_FOUND"; query("minecraft:black_stained_glass"); }
//$$                 default -> throw new IllegalStateException("bad discovery stage");
//$$             }
//$$         } catch(Exception e) {
//$$             System.out.println("FGA_SORT_PROBE_FAIL: inventory-discovery stage="+stage+" "+e);
//$$             stage=-1;
//$$         }
//$$     }
//$$ }
//#endif
