//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import java.io.IOException;
//$$ import java.nio.file.*;
//$$ import java.nio.file.attribute.BasicFileAttributes;
//$$ import java.util.*;
//$$ import java.util.concurrent.*;
//$$ import java.util.function.*;
//$$ import net.minecraft.nbt.*;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.entity.EntityEquipment;
//$$ import net.minecraft.world.entity.EquipmentSlot;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import org.slf4j.Logger;
//$$ import org.slf4j.LoggerFactory;
//$$
//$$ /** Stage bounded offline data off-thread; commit the source and recipient on the server thread. */
//$$ public final class PlayerSortOfflineWithdrawal {
//$$     private static final Logger LOGGER=LoggerFactory.getLogger("carpet-fga-addition/playersort-offline");
//$$     private static final ConcurrentMap<String,UUID> LOCKED=new ConcurrentHashMap<>();
//$$     private PlayerSortOfflineWithdrawal() {}
//$$     public static boolean isLocked(String name) { return LOCKED.containsKey(name.toLowerCase(Locale.ROOT)); }
//$$     static int lockCount() { return LOCKED.size(); }
//$$     static UUID reserve(String name) {
//$$         UUID owner=UUID.randomUUID();
//$$         if (LOCKED.size()>=8 || LOCKED.putIfAbsent(name.toLowerCase(Locale.ROOT),owner)!=null) throw new IllegalArgumentException("TARGET_BUSY");
//$$         return owner;
//$$     }
//$$     static void release(String name,UUID owner) { LOCKED.remove(name.toLowerCase(Locale.ROOT),owner); }
//$$
//$$     static void clearLocks() { LOCKED.clear(); }
//$$
//$$     static void withdraw(MinecraftServer server,ServerPlayer player,String name,String itemId,int requestedAmount,ItemStack[] expected,
//$$                          ExecutorService io,BooleanSupplier active,BiConsumer<String,Integer> done) throws IOException {
//$$         UUID owner=UUID.randomUUID();
//$$         if (LOCKED.size()>=8 || LOCKED.putIfAbsent(name.toLowerCase(Locale.ROOT),owner)!=null) throw new IllegalArgumentException("TARGET_BUSY");
//$$         try {
//$$             Path path=FakePlayerItemSortManager.apiDataPath(server,name,itemId);
//$$             io.execute(()->{
//$$                 CompoundTag tag=null; BasicFileAttributes stamp=null; String failure=null;
//$$                 try {
//$$                     if (!Files.isRegularFile(path)) throw new IllegalArgumentException("NOT_FOUND");
//$$                     stamp=Files.readAttributes(path,BasicFileAttributes.class);
//$$                     if (stamp.size()>2_097_152) throw new IllegalArgumentException("INVENTORY_TOO_LARGE");
//$$                     tag=NbtIo.readCompressed(path,NbtAccounter.create(8_388_608L));
//$$                     if (!sameFile(stamp,Files.readAttributes(path,BasicFileAttributes.class)))
//$$                         throw new IllegalArgumentException("STALE_SNAPSHOT");
//$$                 } catch (IllegalArgumentException e) { failure=e.getMessage(); }
//$$                 catch (Exception e) { failure="READ_FAILED"; }
//$$                 CompoundTag data=tag; BasicFileAttributes original=stamp; String failed=failure;
//$$                 server.execute(()->{
//$$                     Path staged=null;
//$$                     try {
//$$                         validate(server,player,name,itemId,active);
//$$                         if (failed!=null) throw new IllegalArgumentException(failed);
//$$                         ItemStack[] stacks=FakePlayerItemSortManager.apiSnapshot(server,name,data,itemId);
//$$                         int bytes=0;
//$$                         for (ItemStack stack:stacks) {
//$$                             bytes+=ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
//$$                             if (bytes>131_072) throw new IllegalArgumentException("INVENTORY_TOO_LARGE");
//$$                         }
//$$                         if (expected!=null) for (int i=0;i<37;i++)
//$$                             if (!ItemStack.matches(stacks[i],expected[i])) throw new IllegalArgumentException("STALE_SNAPSHOT");
//$$                         ItemStack prototype=PlayerSortInventoryApi.materialPrototype(stacks,itemId,requestedAmount);
//$$                         if (prototype.isEmpty()) throw new IllegalArgumentException("NOT_ENOUGH");
//$$                         int amount=PlayerSortInventoryApi.takeAmount(prototype,requestedAmount);
//$$                         ItemStack moved=prototype.copyWithCount(amount);
//$$                         if (!PlayerSortInventoryApi.planInsertion(inventory(player),moved)) throw new IllegalArgumentException("NO_SPACE");
//$$                         if (!PlayerSortInventoryApi.extractMaterial(stacks,prototype,amount)) throw new IllegalArgumentException("NOT_ENOUGH");
//$$                         CompoundTag changed=encode(server,data,stacks);
//$$                         staged=path.resolveSibling(path.getFileName()+".playersort-"+UUID.randomUUID()+".tmp");
//$$                         Path temp=staged;
//$$                         io.execute(()->{
//$$                             String writeFailure=null;
//$$                             try { NbtIo.writeCompressed(changed,temp); }
//$$                             catch (Exception e) { LOGGER.warn("Could not stage silent inventory withdrawal",e); writeFailure="WRITE_FAILED"; }
//$$                             String error=writeFailure;
//$$                             server.execute(()->commit(server,player,name,owner,itemId,path,temp,original,moved,io,active,done,error));
//$$                         });
//$$                     } catch (IllegalArgumentException e) { finish(name,owner,done,e.getMessage(),0); }
//$$                     catch (RejectedExecutionException e) { cleanup(io,staged); finish(name,owner,done,"SERVER_BUSY",0); }
//$$                     catch (RuntimeException e) { LOGGER.warn("Silent withdrawal preparation failed",e); cleanup(io,staged); finish(name,owner,done,"READ_FAILED",0); }
//$$                 });
//$$             });
//$$         } catch (IOException | RuntimeException e) { LOCKED.remove(name.toLowerCase(Locale.ROOT),owner); throw e; }
//$$     }
//$$
//$$     private static void validate(MinecraftServer server,ServerPlayer player,String name,String itemId,BooleanSupplier active) {
//$$         if (!active.getAsBoolean()) throw new IllegalArgumentException("REQUEST_CANCELLED");
//$$         if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IllegalArgumentException("DISABLED");
//$$         if (PlayerPossessionManager.isParticipant(player)
//$$                 || !FakePlayerItemSortConfig.canUse(player.createCommandSourceStack(),"stock")
//$$                 || !FakePlayerItemSortConfig.canUse(player.createCommandSourceStack(),"inventoryTake"))
//$$             throw new IllegalArgumentException("PERMISSION_DENIED");
//$$         FakePlayerItemSortManager.apiValidateTarget(server,name,itemId);
//$$         if (server.getPlayerList().getPlayerByName(name)!=null) throw new IllegalArgumentException("TARGET_BUSY");
//$$     }
//$$
//$$     private static void commit(MinecraftServer server,ServerPlayer player,String name,UUID owner,String itemId,Path path,Path temp,
//$$                                BasicFileAttributes original,ItemStack moved,ExecutorService io,BooleanSupplier active,
//$$                                BiConsumer<String,Integer> done,String error) {
//$$         boolean backed=false,committed=false;
//$$         Path backup=path.resolveSibling(path.getFileName()+"_old");
//$$         ItemStack[] before=null;
//$$         try {
//$$             validate(server,player,name,itemId,active);
//$$             if (error!=null) throw new IllegalArgumentException(error);
//$$             if (!sameFile(original,Files.readAttributes(path,BasicFileAttributes.class)))
//$$                 throw new IllegalArgumentException("STALE_SNAPSHOT");
//$$             before=inventory(player);
//$$             ItemStack[] destination=inventory(player);
//$$             if (!PlayerSortInventoryApi.planInsertion(destination,moved)) throw new IllegalArgumentException("NO_SPACE");
//$$             // Only atomic filesystem renames remain on the server thread. No async boundary after capacity validation.
//$$             Files.move(path,backup,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); backed=true;
//$$             Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); committed=true;
//$$             for (int i=0;i<36;i++) player.getInventory().setItem(i,destination[i]);
//$$             player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
//$$             FakePlayerItemSortManager.markDashboardDirty();
//$$             finish(name,owner,done,"OK",moved.getCount());
//$$         } catch (IOException | RuntimeException e) {
//$$             if (!backed && e instanceof IllegalArgumentException) { finish(name,owner,done,e.getMessage(),0); return; }
//$$             LOGGER.warn("Silent withdrawal commit failed; rolling back",e);
//$$             String status="WRITE_FAILED";
//$$             try {
//$$                 if (backed) Files.move(backup,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
//$$                 if (committed && before!=null) {
//$$                     for (int i=0;i<36;i++) player.getInventory().setItem(i,before[i]);
//$$                     player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
//$$                 }
//$$             } catch (IOException | RuntimeException rollback) {
//$$                 LOGGER.error("Silent withdrawal rollback failed; inspect both inventories",rollback); status="TRANSFER_FAILED";
//$$             }
//$$             finish(name,owner,done,status,0);
//$$         } finally { cleanup(io,temp); }
//$$     }
//$$
//$$     private static ItemStack[] inventory(ServerPlayer player) {
//$$         ItemStack[] result=new ItemStack[36];
//$$         for (int i=0;i<36;i++) result[i]=player.getInventory().getItem(i).copy();
//$$         return result;
//$$     }
//$$     static boolean sameFile(BasicFileAttributes a,BasicFileAttributes b) {
//$$         return a.size()==b.size() && a.lastModifiedTime().equals(b.lastModifiedTime()) && Objects.equals(a.fileKey(),b.fileKey());
//$$     }
//$$     static CompoundTag encode(MinecraftServer server,CompoundTag original,ItemStack[] stacks) {
//$$         CompoundTag result=original.copy(); ListTag entries=new ListTag();
//$$         for (Tag entry:original.getListOrEmpty("Inventory")) {
//$$             CompoundTag compound=(CompoundTag)entry;
//$$             int slot=compound.getByteOr("Slot",(byte)-1);
//$$             if (slot<0 || slot>=36) entries.add(compound.copy());
//$$         }
//$$         var ops=server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
//$$         for (int i=0;i<36;i++) if (!stacks[i].isEmpty()) {
//$$             CompoundTag entry=(CompoundTag)ItemStack.OPTIONAL_CODEC.encodeStart(ops,stacks[i]).getOrThrow();
//$$             entry.putByte("Slot",(byte)i); entries.add(entry);
//$$         }
//$$         result.put("Inventory",entries);
//$$         EntityEquipment equipment=original.get("equipment")==null ? new EntityEquipment()
//$$                 : EntityEquipment.CODEC.parse(ops,original.get("equipment")).getOrThrow();
//$$         equipment.set(EquipmentSlot.OFFHAND,stacks[36]);
//$$         result.put("equipment",EntityEquipment.CODEC.encodeStart(ops,equipment).getOrThrow());
//$$         return result;
//$$     }
//$$     private static void finish(String name,UUID owner,BiConsumer<String,Integer> done,String status,int amount) {
//$$         LOCKED.remove(name.toLowerCase(Locale.ROOT),owner); done.accept(status,amount);
//$$     }
//$$     private static void cleanup(ExecutorService io,Path temp) {
//$$         if (temp==null) return;
//$$         try { io.execute(()->{ try { Files.deleteIfExists(temp); } catch (IOException e) { LOGGER.warn("Could not remove staged withdrawal file {}",temp,e); } }); }
//$$         catch (RejectedExecutionException e) { LOGGER.warn("Staged withdrawal file retained at {}",temp); }
//$$     }
//$$ }
//#endif
