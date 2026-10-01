//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.*;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import com.google.gson.*;
//$$ import net.minecraft.core.NonNullList;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.nbt.*;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.world.item.*;
//$$ import net.minecraft.world.item.component.ItemContainerContents;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.storage.LevelResource;
//$$ import java.nio.file.*;
//$$ import java.lang.reflect.Field;
//$$ import java.util.*;
//$$
//$$ public final class FakePlayerItemSortProbeApi {
//$$     private static ServerPlayer actor, target, snow;
//$$     private static MinecraftServer current;
//$$     private static int stage=-1;
//$$     private static long next, deadline;
//$$     private static String expected, token;
//$$     private static PlayerSortInventoryPayloads.Reply result;
//$$     private static boolean awaiting, waitingLogout;
//$$     private static String waitingSpawn;
//$$     private static ItemStack[] savedActor;
//$$     private static final String GOLD="minecraft:gold_block", SNOW="minecraft:snowball";
//$$     private FakePlayerItemSortProbeApi() {}
//$$
//$$     public static int start(MinecraftServer server, String actorName) {
//$$         if (stage>=0) throw new IllegalStateException("API probe already running");
//$$         current=server; actor=server.getPlayerList().getPlayerByName(actorName);
//$$         require(actor instanceof EntityPlayerMPFake,"probe actor must be a fake");
//$$         FakePlayerItemSortManager.stop(actor);
//$$         try {
//$$             Field routes=FakePlayerItemSortManager.class.getDeclaredField("ROUTES"); routes.setAccessible(true);
//$$             @SuppressWarnings("unchecked") Map<String,String> map=(Map<String,String>)routes.get(null);
//$$             map.put(GOLD+"|{}", "ApiStock26"); map.put(SNOW+"|{}", "ApiSnow26");
//$$             FakePlayerItemSortConfig.setPermission("stock",actorName,true);
//$$             FakePlayerItemSortConfig.setPermission("inventoryTake",actorName,false);
//$$             spawn("ApiStock26");
//$$             waitingLogout=false;
//$$             stage=0; awaiting=false; next=System.currentTimeMillis()+1100;
//$$             System.out.println("FGA_SORT_PROBE_BEGIN: inventory-api");
//$$             return 1;
//$$         } catch (Exception e) { stage=-1; throw new IllegalStateException(e); }
//$$     }
//$$     private static void spawn(String name) {
//$$         current.services().nameToIdCache().add(NameAndId.createOffline(name));
//$$         boolean ok=EntityPlayerMPFake.createFake(name,current,actor.position(),0,0,actor.level().dimension(),GameType.SURVIVAL,false);
//$$         require(ok,"could not request API fixture spawn");
//$$         waitingSpawn=name; deadline=System.currentTimeMillis()+20_000;
//$$     }
//$$     private static void q(long id,String item) { result=null; PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,id,2,item,0),r->result=r); }
//$$     private static void take(long id,String item) { result=null; PlayerSortInventoryApi.take(current,actor,new PlayerSortInventoryPayloads.Take(1,id,token,item),r->result=r); }
//$$     private static long count(ServerPlayer player, Item item) {
//$$         long n=0; for(int i=0;i<36;i++) n+=count(player.getInventory().getItem(i),item); return n;
//$$     }
//$$     private static long count(ItemStack stack,Item item) {
//$$         long n=stack.is(item) ? stack.getCount() : 0;
//$$         var container=stack.get(DataComponents.CONTAINER);
//$$         if (container!=null) { var inner=NonNullList.withSize(27,ItemStack.EMPTY); container.copyInto(inner);
//$$             for (ItemStack value:inner) if(value.is(item)) n+=value.getCount(); }
//$$         return n;
//$$     }
//$$     private static ItemStack[] disk(String name) throws Exception {
//$$         UUID uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
//$$         var tag=NbtIo.readCompressed(current.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid+".dat"),NbtAccounter.create(8_388_608));
//$$         var stacks=new ItemStack[36]; Arrays.fill(stacks,ItemStack.EMPTY);
//$$         ListTag inventory=tag.getListOrEmpty("Inventory");
//$$         for (int i=0;i<inventory.size();i++) {
//$$             CompoundTag entry=inventory.getCompoundOrEmpty(i); int slot=entry.getByteOr("Slot",(byte)-1);
//$$             if(slot>=0 && slot<36) stacks[slot]=ItemStack.OPTIONAL_CODEC.parse(current.registryAccess().createSerializationContext(NbtOps.INSTANCE),entry).getOrThrow();
//$$         }
//$$         return stacks;
//$$     }
//$$     private static void require(boolean ok,String reason) { if(!ok) throw new IllegalStateException(reason); }
//$$
//$$     public static void tick(MinecraftServer server) {
//$$         if(stage<0 || server!=current) return;
//$$         long now=System.currentTimeMillis();
//$$         try {
//$$             if(waitingSpawn!=null) {
//$$                 ServerPlayer spawned=server.getPlayerList().getPlayerByName(waitingSpawn);
//$$                 if(spawned==null) { require(now<deadline,"fixture spawn timeout"); return; }
//$$                 require(spawned instanceof EntityPlayerMPFake,"fixture source is not a fake");
//$$                 if(stage==0) {
//$$                     target=spawned;
//$$                     for(int i=0;i<36;i++) { actor.getInventory().setItem(i,ItemStack.EMPTY); target.getInventory().setItem(i,ItemStack.EMPTY); }
//$$                     var inner=NonNullList.withSize(27,ItemStack.EMPTY);
//$$                     inner.set(0,new ItemStack(Items.GOLD_BLOCK,64)); inner.set(1,new ItemStack(Items.GOLD_BLOCK,16));
//$$                     var box=new ItemStack(Items.SHULKER_BOX); box.set(DataComponents.CUSTOM_NAME,Component.literal("API keeper"));
//$$                     box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(inner));
//$$                     target.getInventory().setItem(0,box); target.getInventory().setItem(1,new ItemStack(Items.GOLD_BLOCK,32));
//$$                     target.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.DIRT,31));
//$$                     next=now+1100;
//$$                 } else {
//$$                     snow=spawned; for(int i=0;i<36;i++) snow.getInventory().setItem(i,ItemStack.EMPTY);
//$$                     snow.getInventory().setItem(0,new ItemStack(Items.SNOWBALL,16)); q(109,SNOW);
//$$                 }
//$$                 waitingSpawn=null; return;
//$$             }
//$$             if(waitingLogout) {
//$$                 if(server.getPlayerList().getPlayerByName("ApiStock26")!=null) { require(now<deadline,"fixture logout timeout"); return; }
//$$                 waitingLogout=false; q(107,GOLD); return;
//$$             }
//$$             if(awaiting) {
//$$                 if(result==null) { require(now<deadline,"API reply timed out at stage "+stage); return; }
//$$                 JsonObject response=JsonParser.parseString(result.json()).getAsJsonObject();
//$$                 require(expected.equals(response.get("status").getAsString()),"stage "+stage+" expected "+expected+" got "+response);
//$$                 if(stage==10 && server.getPlayerList().getPlayerByName("ApiStock26")!=null) {
//$$                     require(now<deadline,"temporary stock fake logout timed out"); return;
//$$                 }
//$$                 if(response.has("token")) token=response.get("token").getAsString();
//$$                 switch(stage) {
//$$                     case 1 -> require(count(actor,Items.GOLD_BLOCK)==0 && count(target,Items.GOLD_BLOCK)==112,"denied take changed inventories");
//$$                     case 2,3,4 -> require(count(actor,Items.GOLD_BLOCK)==32 && count(target,Items.GOLD_BLOCK)==80,"duplicate/changed nonce moved items twice");
//$$                     case 6 -> require(count(target,Items.GOLD_BLOCK)==80 && count(actor,Items.GOLD_BLOCK)==0,"full inventory debit");
//$$                     case 8 -> require(count(actor,Items.GOLD_BLOCK)==32 && count(target,Items.GOLD_BLOCK)==80,"stale snapshot debit");
//$$                     case 10 -> {
//$$                         require(count(actor,Items.GOLD_BLOCK)==64,"offline half-stack missing");
//$$                         require(server.getPlayerList().getPlayerByName("ApiStock26")==null,"temporary stock fake stayed online");
//$$                         ItemStack[] stored=disk("ApiStock26"); long total=0; for(ItemStack stack:stored) total+=count(stack,Items.GOLD_BLOCK);
//$$                         require(total==48 && total+count(actor,Items.GOLD_BLOCK)==112,"offline material conservation");
//$$                         require("API keeper".equals(stored[0].getHoverName().getString()),"box name lost");
//$$                         UUID uuid=UUID.nameUUIDFromBytes("OfflinePlayer:ApiStock26".getBytes(java.nio.charset.StandardCharsets.UTF_8));
//$$                         var nbt=NbtIo.readCompressed(current.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid+".dat"),NbtAccounter.create(8_388_608));
//$$                         var equipment=net.minecraft.world.entity.EntityEquipment.CODEC.parse(current.registryAccess().createSerializationContext(NbtOps.INSTANCE),nbt.get("equipment")).getOrThrow();
//$$                         var hand=equipment.get(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
//$$                         require(hand.is(Items.DIRT) && hand.getCount()==31,"offline offhand was not preserved");
//$$                     }
//$$                     case 12 -> require(count(actor,Items.SNOWBALL)==8 && count(snow,Items.SNOWBALL)==8,"16 stack must give eight");
//$$                     case 15 -> {
//$$                         FGASettings.fakePlayerItemSort=true;
//$$                         FakePlayerItemSortConfig.setPermission("stock",actor.getGameProfile().name(),true);
//$$                     }
//$$                     case 16 -> require(response.getAsJsonArray("entries").asList().stream()
//$$                             .anyMatch(entry->entry.getAsJsonObject().get("target").getAsString().equals("ApiStock26")),"route page missing source");
//$$                     case 17 -> require(response.getAsJsonArray("entries").get(0).getAsJsonObject().get("count").getAsInt()==8,"online inventory preview wrong");
//$$                     case 18 -> require(response.get("nextCursor").getAsInt()==12,"offline inventory first page wrong");
//$$                     case 19 -> require(response.getAsJsonArray("entries").get(0).getAsJsonObject().get("slot").getAsInt()==12,"offline continuation wrong");
//$$                     case 21 -> {
//$$                         ((EntityPlayerMPFake)snow).isAShadow=false;
//$$                         FakePlayerItemSortConfig.setPermission("inventoryTake",actor.getGameProfile().name(),false);
//$$                         snow.kill(snow.level());
//$$                         System.out.println("FGA_SORT_PROBE_PASS: inventory-api half64=32 half16=8 offline=true duplicate=true no-space=true stale=true permissions=true conserved=true");
//$$                         stage=-1; return;
//$$                     }
//$$                 }
//$$                 stage++; awaiting=false; next=now+1100; return;
//$$             }
//$$             if(now<next) return;
//$$             expected="OK"; awaiting=true; deadline=now+20_000;
//$$             switch(stage) {
//$$                 case 0 -> q(100,GOLD);
//$$                 case 1 -> { expected="PERMISSION_DENIED"; take(101,GOLD); }
//$$                 case 2 -> { FakePlayerItemSortConfig.setPermission("inventoryTake",actor.getGameProfile().name(),true); take(102,GOLD); }
//$$                 case 3 -> take(102,GOLD);
//$$                 case 4 -> { expected="REQUEST_ID_REUSED"; take(102,"minecraft:dirt"); }
//$$                 case 5 -> {
//$$                     savedActor=new ItemStack[36];
//$$                     for(int i=0;i<36;i++) { savedActor[i]=actor.getInventory().getItem(i).copy(); actor.getInventory().setItem(i,new ItemStack(Items.STONE,64)); }
//$$                     q(103,GOLD);
//$$                 }
//$$                 case 6 -> { expected="NO_SPACE"; take(104,GOLD); }
//$$                 case 7 -> { for(int i=0;i<36;i++) actor.getInventory().setItem(i,savedActor[i]); q(105,GOLD); }
//$$                 case 8 -> { target.getInventory().setItem(2,new ItemStack(Items.DIRT)); expected="STALE_SNAPSHOT"; take(106,GOLD); }
//$$                 case 9 -> { target.getInventory().setItem(2,ItemStack.EMPTY); target.kill(target.level()); result=null; waitingLogout=true; }
//$$                 case 10 -> take(108,GOLD);
//$$                 case 11 -> { result=null; spawn("ApiSnow26"); }
//$$                 case 12 -> take(110,SNOW);
//$$                 case 13 -> { expected="INVALID_ITEM"; q(111,"minecraft:does_not_exist"); }
//$$                 case 14 -> { FakePlayerItemSortConfig.setPermission("stock",actor.getGameProfile().name(),false);
//$$                     expected="PERMISSION_DENIED"; q(112,SNOW); }
//$$                 case 15 -> { FGASettings.fakePlayerItemSort=false; expected="DISABLED"; q(113,SNOW); }
//$$                 case 16 -> { result=null; PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,114,0,"",0),r->result=r); }
//$$                 case 17 -> { result=null; PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,115,1,"ApiSnow26",0),r->result=r); }
//$$                 case 18 -> { result=null; PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,116,1,"ApiStock26",0),r->result=r); }
//$$                 case 19 -> { result=null; PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,117,1,"ApiStock26",12),r->result=r); }
//$$                 case 20 -> { expected="INVALID_TARGET"; result=null; PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,118,1,"UnknownRealPlayer",0),r->result=r); }
//$$                 case 21 -> { expected="PROTECTED_TARGET"; result=null; ((EntityPlayerMPFake)snow).isAShadow=true;
//$$                     PlayerSortInventoryApi.query(current,actor,new PlayerSortInventoryPayloads.Query(1,119,1,"ApiSnow26",0),r->result=r); }
//$$                 default -> throw new IllegalStateException("bad probe stage");
//$$             }
//$$         } catch(Exception e) {
//$$             System.out.println("FGA_SORT_PROBE_FAIL: inventory-api stage="+stage+" "+e);
//$$             FGASettings.fakePlayerItemSort=true; stage=-1;
//$$         }
//$$     }
//$$ }
//$$
//#endif
