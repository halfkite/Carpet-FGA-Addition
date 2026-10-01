//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import java.io.IOException;
//$$ import java.nio.file.*;
//$$ import java.nio.file.attribute.BasicFileAttributes;
//$$ import java.util.*;
//$$ import java.util.concurrent.*;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.nbt.*;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.BlockItem;
//$$ import net.minecraft.world.level.block.ShulkerBoxBlock;
//$$ import net.minecraft.world.level.storage.LevelResource;
//$$ import org.slf4j.Logger;
//$$ import org.slf4j.LoggerFactory;
//$$
//$$ /** Bounded primary maintenance. No summons, directory/NBT reads or compressed writes on the tick thread. */
//$$ final class PlayerSortHeadRestock {
//$$     private static final Logger LOGGER=LoggerFactory.getLogger("carpet-fga-addition/playersort-restock");
//$$     private static final int LOW_STOCK=1152, FIRST_LOOSE_SLOT=9;
//$$     private static final Map<String,String> REQUESTS=new LinkedHashMap<>(), KNOWN=new LinkedHashMap<>();
//$$     private static final ConcurrentMap<Path,BasicFileAttributes> EMPTY=new ConcurrentHashMap<>();
//$$     private static final Map<String,LinkedHashSet<String>> BOX_RETURNS=new LinkedHashMap<>();
//$$     private static final Map<String,Long> RETURN_AFTER=new HashMap<>();
//$$     private static final Set<String> QUARANTINED=new HashSet<>();
//$$     private static Job active;
//$$     private static long nextCheck;
//$$     private static int cursor;
//$$     private PlayerSortHeadRestock() {}
//$$
//$$     static void request(MinecraftServer server,String name,String itemId) {
//$$         String head=FakePlayerItemSortManager.apiMaterialHead(name,itemId);
//$$         if (head==null || itemId.endsWith("shulker_box")) return;
//$$         if (REQUESTS.size()<256 || REQUESTS.containsKey(head)) REQUESTS.put(head,itemId);
//$$         if (KNOWN.size()<256 || KNOWN.containsKey(head)) KNOWN.put(head,itemId);
//$$     }
//$$     static void tick(MinecraftServer server) {
//$$         long time=System.currentTimeMillis();
//$$         if (!FGASettings.isFakePlayerItemSortEnabled() || active!=null || time<nextCheck) return;
//$$         nextCheck=time+1000;
//$$         Map.Entry<String,String> route;
//$$         if (!REQUESTS.isEmpty()) {
//$$             route=REQUESTS.entrySet().iterator().next();
//$$             route=Map.entry(route.getKey(),route.getValue()); REQUESTS.remove(route.getKey());
//$$         } else {
//$$             Map<String,String> routes=FakePlayerItemSortManager.apiPrimaryRoutes(); routes.putAll(KNOWN);
//$$             if (routes.isEmpty()) return;
//$$             List<Map.Entry<String,String>> values=new ArrayList<>(routes.entrySet());
//$$             route=values.get(Math.floorMod(cursor++,values.size()));
//$$         }
//$$         Job job=new Job(server,route.getKey(),route.getValue()); active=job;
//$$         try {
//$$             job.head=reserve(job,job.base,job.itemId); job.nodes.add(job.head);
//$$             load(job,List.of(job.head),()->{
//$$                 if (job.head.failure!=null) { finish(job); return; }
//$$                 Set<String> pending=BOX_RETURNS.get(job.base);
//$$                 if (pending!=null && !pending.isEmpty() && time>=RETURN_AFTER.getOrDefault(job.base,0L)) {
//$$                     job.returnOnly=true; RETURN_AFTER.put(job.base,time+10_000);
//$$                     loadTails(job,new ArrayList<>(pending).subList(0,Math.min(32,pending.size())));
//$$                     return;
//$$                 }
//$$                 if (looseCount(job.head.stacks,job.itemId)>=LOW_STOCK) { finish(job); return; }
//$$                 findTail(job);
//$$             });
//$$         } catch (IOException | RuntimeException e) { skip(job,e); }
//$$     }
//$$     static void clear() {
//$$         if (active!=null) finish(active);
//$$         REQUESTS.clear(); KNOWN.clear(); EMPTY.clear(); BOX_RETURNS.clear(); RETURN_AFTER.clear();
//$$         QUARANTINED.clear(); nextCheck=0; cursor=0;
//$$     }
//$$     static boolean quarantined(String name) { return QUARANTINED.contains(name.toLowerCase(Locale.ROOT)); }
//$$
//$$     private static Node reserve(Job job,String name,String itemId) throws IOException {
//$$         if (PlayerSortInventoryApi.hasLease(name)) throw new IllegalArgumentException("TARGET_BUSY");
//$$         FakePlayerItemSortManager.apiValidateTarget(job.server,name,itemId);
//$$         UUID owner=PlayerSortOfflineWithdrawal.reserve(name);
//$$         try {
//$$             Node node=new Node(name,itemId,owner,FakePlayerItemSortManager.apiDataPath(job.server,name,itemId),
//$$                     job.server.getPlayerList().getPlayerByName(name));
//$$             if (node.online!=null) capture(job,node);
//$$             return node;
//$$         } catch (IOException | RuntimeException e) { PlayerSortOfflineWithdrawal.release(name,owner); throw e; }
//$$     }
//$$     private static void capture(Job job,Node node) {
//$$         if (node.online!=job.server.getPlayerList().getPlayerByName(node.name)) throw new IllegalArgumentException("TARGET_BUSY");
//$$         node.original=FakePlayerItemSortManager.apiSnapshot(job.server,node.name,node.data,node.itemId);
//$$         int bytes=0;
//$$         var ops=job.server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
//$$         for (ItemStack stack:node.original) {
//$$             bytes+=ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow().toString()
//$$                     .getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
//$$             if (bytes>131_072) throw new IllegalArgumentException("INVENTORY_TOO_LARGE");
//$$         }
//$$         node.stacks=Arrays.stream(node.original).map(ItemStack::copy).toArray(ItemStack[]::new);
//$$     }
//$$     private static void load(Job job,List<Node> nodes,Runnable continuation) {
//$$         PlayerSortInventoryApi.io().execute(()->{
//$$             for (Node node:nodes) if (node.online==null) try {
//$$                 if (!Files.isRegularFile(node.path)) throw new IOException("missing inventory");
//$$                 node.stamp=Files.readAttributes(node.path,BasicFileAttributes.class);
//$$                 if (node.stamp.size()>2_097_152) throw new IOException("inventory too large");
//$$                 node.data=NbtIo.readCompressed(node.path,NbtAccounter.create(8_388_608L));
//$$                 if (!PlayerSortOfflineWithdrawal.sameFile(node.stamp,Files.readAttributes(node.path,BasicFileAttributes.class)))
//$$                     throw new IOException("inventory changed");
//$$             } catch (IOException | RuntimeException e) { node.failure=e.getMessage(); }
//$$             job.server.execute(()->{
//$$                 if (active!=job) { finish(job); return; }
//$$                 try {
//$$                     for (Node node:nodes) if (node.failure==null && node.online==null) try { capture(job,node); }
//$$                     catch (RuntimeException e) { node.failure=e.getMessage(); }
//$$                     continuation.run();
//$$                 } catch (RuntimeException e) { skip(job,e); }
//$$             });
//$$         });
//$$     }
//$$
//$$     private static void findTail(Job job) {
//$$         Set<UUID> online=new HashSet<>(); Set<String> onlineNames=new HashSet<>();
//$$         job.server.getPlayerList().getPlayers().forEach(p->{ online.add(p.getUUID()); onlineNames.add(p.getGameProfile().name()); });
//$$         Path directory=job.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
//$$         PlayerSortInventoryApi.io().execute(()->{
//$$             List<String> candidates=new ArrayList<>(); String failure=null;
//$$             try {
//$$                 Set<UUID> existing=new HashSet<>(online); int count=0;
//$$                 try (var files=Files.newDirectoryStream(directory,"*.dat")) {
//$$                     for (Path file:files) {
//$$                         if (++count>65536) throw new IOException("inventory index too large");
//$$                         String name=file.getFileName().toString();
//$$                         try { existing.add(UUID.fromString(name.substring(0,name.length()-4))); }
//$$                         catch (IllegalArgumentException ignored) { }
//$$                     }
//$$                 }
//$$                 for (String name:PlayerSortInventoryApi.existingMaterialCandidates(List.of(job.base),existing)) {
//$$                     if (name.equals(job.base)) continue;
//$$                     Path path=directory.resolve(offlineUuid(name)+".dat");
//$$                     BasicFileAttributes empty=EMPTY.get(path);
//$$                     if (!onlineNames.contains(name) && empty!=null && Files.isRegularFile(path)
//$$                             && PlayerSortOfflineWithdrawal.sameFile(empty,Files.readAttributes(path,BasicFileAttributes.class))) continue;
//$$                     candidates.add(name);
//$$                     if (candidates.size()>=32) break;
//$$                 }
//$$             } catch (IOException | RuntimeException e) { failure=e.getMessage(); }
//$$             String failed=failure;
//$$             job.server.execute(()->{
//$$                 if (active!=job) { finish(job); return; }
//$$                 if (failed!=null) { skip(job,new IOException(failed)); return; }
//$$                 loadTails(job,candidates);
//$$             });
//$$         });
//$$     }
//$$     private static void loadTails(Job job,List<String> candidates) {
//$$         List<Node> tails=new ArrayList<>();
//$$         for (String name:candidates) {
//$$             try {
//$$                 Node node=reserve(job,name,job.itemId); job.nodes.add(node); tails.add(node);
//$$                 if (tails.size()>=4) break;
//$$             } catch (IOException | RuntimeException e) {
//$$                 LOGGER.debug("Skipping restock source {}: {}",name,e.getMessage());
//$$                 if (job.returnOnly) {
//$$                     Set<String> pending=BOX_RETURNS.get(job.base);
//$$                     if (pending!=null && pending.remove(name)) pending.add(name);
//$$                 }
//$$             }
//$$         }
//$$         if (tails.isEmpty()) { finish(job); return; }
//$$         job.tails=tails;
//$$         // Empty boxes go to the inventory named exactly 潜影盒, independent of the crafting depot name.
//$$         try { job.boxes=reserve(job,"\u6f5c\u5f71\u76d2","minecraft:shulker_box"); job.nodes.add(job.boxes); }
//$$         catch (IOException | RuntimeException e) { LOGGER.debug("Empty box recipient unavailable: {}",e.getMessage()); }
//$$         List<Node> reading=new ArrayList<>(tails); if (job.boxes!=null) reading.add(job.boxes);
//$$         try { load(job,reading,()->plan(job,tails)); } catch (RuntimeException e) { skip(job,e); }
//$$     }
//$$
//$$     private static void plan(Job job,List<Node> tails) {
//$$         for (Node tail:tails) {
//$$             if (tail.failure!=null) {
//$$                 rememberEmpty(tail); continue; // Re-check excluded files when their metadata changes.
//$$             }
//$$             List<ItemStack> prototypes=new ArrayList<>();
//$$             for (ItemStack stack:tail.stacks) {
//$$                 addPrototype(prototypes,stack,job.itemId);
//$$                 if (box(stack) && stack.getCount()==1) for (ItemStack inner:PlayerSortInventoryApi.contents(stack))
//$$                     addPrototype(prototypes,inner,job.itemId);
//$$             }
//$$             if (!job.returnOnly) for (ItemStack prototype:prototypes) {
//$$                 int space=0;
//$$                 for (int i=FIRST_LOOSE_SLOT;i<36;i++) {
//$$                     ItemStack stack=job.head.stacks[i]; int max=Math.min(64,prototype.getMaxStackSize());
//$$                     if (stack.isEmpty()) space+=max;
//$$                     else if (ItemStack.isSameItemSameComponents(stack,prototype)) space+=Math.max(0,max-stack.getCount());
//$$                 }
//$$                 int amount=(int)Math.min(space,PlayerSortInventoryApi.materialCount(tail.stacks,prototype));
//$$                 if (amount<=0) continue;
//$$                 ItemStack[] destination=Arrays.stream(job.head.stacks,FIRST_LOOSE_SLOT,36).map(ItemStack::copy).toArray(ItemStack[]::new);
//$$                 if (!PlayerSortInventoryApi.planInsertion(destination,prototype.copyWithCount(amount))) continue;
//$$                 if (!PlayerSortInventoryApi.extractMaterial(tail.stacks,prototype,amount)) throw new IllegalStateException("restock extraction mismatch");
//$$                 System.arraycopy(destination,0,job.head.stacks,FIRST_LOOSE_SLOT,destination.length);
//$$             }
//$$             if (job.boxes!=null && job.boxes.failure==null) for (int slot=0;slot<37;slot++) {
//$$                 ItemStack stack=tail.stacks[slot];
//$$                 if (!box(stack) || stack.getCount()!=1 || PlayerSortInventoryApi.contents(stack).stream().anyMatch(s->!s.isEmpty())) continue;
//$$                 ItemStack[] destination=Arrays.stream(job.boxes.stacks,0,36).map(ItemStack::copy).toArray(ItemStack[]::new);
//$$                 if (!PlayerSortInventoryApi.planInsertion(destination,stack.copy())) continue;
//$$                 System.arraycopy(destination,0,job.boxes.stacks,0,36); tail.stacks[slot]=ItemStack.EMPTY;
//$$             }
//$$             if (prototypes.isEmpty()) rememberEmpty(tail);
//$$         }
//$$         List<Node> changed=job.nodes.stream().filter(n->n.failure==null && changed(n)).toList();
//$$         if (changed.isEmpty()) { rememberReturns(job); finish(job); return; }
//$$         for (Node node:changed) if (node.online==null) node.changed=PlayerSortOfflineWithdrawal.encode(job.server,node.data,node.stacks);
//$$         PlayerSortInventoryApi.io().execute(()->{
//$$             String failure=null;
//$$             try {
//$$                 for (Node node:changed) if (node.online==null) {
//$$                     node.temp=Files.createTempFile(node.path.getParent(),node.path.getFileName()+".restock-",".tmp");
//$$                     NbtIo.writeCompressed(node.changed,node.temp);
//$$                 }
//$$             } catch (IOException | RuntimeException e) { failure=e.getMessage(); }
//$$             String failed=failure;
//$$             job.server.execute(()->{
//$$                 if (active!=job) { finish(job); return; }
//$$                 if (failed!=null) { skip(job,new IOException(failed)); return; }
//$$                 commit(job,changed);
//$$             });
//$$         });
//$$     }
//$$
//$$     private static void commit(Job job,List<Node> nodes) {
//$$         try {
//$$             if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IOException("sorter disabled");
//$$             for (Node node:nodes) {
//$$                 if (PlayerSortInventoryApi.hasLease(node.name)) throw new IOException("inventory leased");
//$$                 FakePlayerItemSortManager.apiValidateTarget(job.server,node.name,node.itemId);
//$$                 if (node.online!=job.server.getPlayerList().getPlayerByName(node.name)) throw new IOException("online state changed");
//$$                 if (node.online==null) {
//$$                     if (!PlayerSortOfflineWithdrawal.sameFile(node.stamp,Files.readAttributes(node.path,BasicFileAttributes.class)))
//$$                         throw new IOException("inventory file changed");
//$$                 } else {
//$$                     ItemStack[] current=FakePlayerItemSortManager.apiSnapshot(job.server,node.name,null,node.itemId);
//$$                     for (int i=0;i<37;i++) if (!ItemStack.matches(current[i],node.original[i])) throw new IOException("live inventory changed");
//$$                 }
//$$             }
//$$             for (Node node:nodes) if (node.online==null) {
//$$                 Files.move(node.path,backup(node),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); node.backed=true;
//$$                 Files.move(node.temp,node.path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
//$$             }
//$$             for (Node node:nodes) if (node.online!=null) {
//$$                 node.applied=true;
//$$                 for (int i=0;i<37;i++) FakePlayerItemSortManager.apiSetSlot(node.online,i,node.stacks[i]);
//$$                 node.online.containerMenu.broadcastChanges();
//$$             }
//$$             FakePlayerItemSortManager.markDashboardDirty();
//$$             rememberReturns(job);
//$$         } catch (IOException | RuntimeException e) {
//$$             LOGGER.warn("Primary restock was cancelled; rolling back changed inventories",e);
//$$             for (int i=nodes.size()-1;i>=0;i--) {
//$$                 Node node=nodes.get(i);
//$$                 try {
//$$                     if (node.backed) Files.move(backup(node),node.path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
//$$                     if (node.applied) {
//$$                         for (int slot=0;slot<37;slot++) FakePlayerItemSortManager.apiSetSlot(node.online,slot,node.original[slot]);
//$$                         node.online.containerMenu.broadcastChanges();
//$$                     }
//$$                 } catch (IOException | RuntimeException restore) {
//$$                     for (Node affected:nodes) QUARANTINED.add(affected.name.toLowerCase(Locale.ROOT));
//$$                     LOGGER.error("Primary restock rollback failed for {}; inspect dat_old and all related inventories",node.name,restore);
//$$                 }
//$$             }
//$$         } finally { finish(job); }
//$$     }
//$$     private static void rememberReturns(Job job) {
//$$         LinkedHashSet<String> pending=BOX_RETURNS.get(job.base);
//$$         for (Node tail:job.tails) if (tail.failure==null) {
//$$             boolean empty=Arrays.stream(tail.stacks).anyMatch(stack -> box(stack) && stack.getCount()==1
//$$                     && PlayerSortInventoryApi.contents(stack).stream().allMatch(ItemStack::isEmpty));
//$$             if (empty) {
//$$                 if (pending==null && BOX_RETURNS.size()<256) {
//$$                     pending=new LinkedHashSet<>(); BOX_RETURNS.put(job.base,pending);
//$$                 }
//$$                 if (pending!=null) pending.add(tail.name);
//$$             } else if (pending!=null) pending.remove(tail.name);
//$$         }
//$$         if (pending!=null && pending.isEmpty()) { BOX_RETURNS.remove(job.base); RETURN_AFTER.remove(job.base); }
//$$         else if (job.returnOnly && pending!=null) {
//$$             // Rotate unavailable/full recipients' donors so one deferred file cannot starve others.
//$$             for (Node tail:job.tails) if (pending.remove(tail.name)) pending.add(tail.name);
//$$         }
//$$     }
//$$     private static void addPrototype(List<ItemStack> values,ItemStack stack,String itemId) {
//$$         if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)
//$$                 && values.stream().noneMatch(v->ItemStack.isSameItemSameComponents(v,stack))) values.add(stack.copyWithCount(1));
//$$     }
//$$     private static boolean box(ItemStack stack) {
//$$         return !stack.isEmpty() && stack.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock;
//$$     }
//$$     private static long looseCount(ItemStack[] stacks,String itemId) {
//$$         long result=0;
//$$         for (int i=0;i<36;i++) if (!stacks[i].isEmpty() && BuiltInRegistries.ITEM.getKey(stacks[i].getItem()).toString().equals(itemId)) result+=stacks[i].getCount();
//$$         return result;
//$$     }
//$$     private static boolean changed(Node node) {
//$$         for (int i=0;i<37;i++) if (!ItemStack.matches(node.original[i],node.stacks[i])) return true;
//$$         return false;
//$$     }
//$$     private static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
//$$     private static Path backup(Node node) { return node.path.resolveSibling(node.path.getFileName()+"_old"); }
//$$     private static void rememberEmpty(Node node) {
//$$         if (node.online==null && node.stamp!=null) { if (EMPTY.size()>=4096) EMPTY.clear(); EMPTY.put(node.path,node.stamp); }
//$$     }
//$$     private static void skip(Job job,Exception e) { LOGGER.debug("Skipping primary restock {}: {}",job.base,e.getMessage()); finish(job); }
//$$     private static void finish(Job job) {
//$$         for (Node node:job.nodes) {
//$$             if (!quarantined(node.name)) PlayerSortOfflineWithdrawal.release(node.name,node.owner);
//$$             if (node.temp!=null) try {
//$$                 PlayerSortInventoryApi.io().execute(()->{ try { Files.deleteIfExists(node.temp); } catch (IOException e) { LOGGER.warn("Restock temporary file retained {}",node.temp,e); } });
//$$             } catch (RejectedExecutionException e) { LOGGER.warn("Restock temporary file retained {}",node.temp); }
//$$         }
//$$         if (active==job) active=null;
//$$     }
//$$     private static final class Job {
//$$         final MinecraftServer server; final String base,itemId;
//$$         final List<Node> nodes=new ArrayList<>(); Node head,boxes;
//$$         List<Node> tails=List.of(); boolean returnOnly;
//$$         Job(MinecraftServer server,String base,String itemId) { this.server=server; this.base=base; this.itemId=itemId; }
//$$     }
//$$     private static final class Node {
//$$         final String name,itemId; final UUID owner; final Path path; final ServerPlayer online;
//$$         CompoundTag data,changed; BasicFileAttributes stamp; ItemStack[] original,stacks;
//$$         String failure; Path temp; boolean backed,applied;
//$$         Node(String name,String itemId,UUID owner,Path path,ServerPlayer online) { this.name=name; this.itemId=itemId; this.owner=owner; this.path=path; this.online=online; }
//$$     }
//$$ }
//#endif
