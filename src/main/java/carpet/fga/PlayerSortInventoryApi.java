//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import com.google.gson.*;
//$$ import com.mojang.serialization.JsonOps;
//$$ import net.fabricmc.fabric.api.networking.v1.*;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.core.NonNullList;
//$$ import net.minecraft.core.HolderLookup;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.nbt.*;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.BlockItem;
//$$ import net.minecraft.world.item.component.ItemContainerContents;
//$$ import net.minecraft.world.entity.EntityEquipment;
//$$ import net.minecraft.world.entity.EquipmentSlot;
//$$ import net.minecraft.world.level.block.ShulkerBoxBlock;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.world.level.storage.LevelResource;
//$$ import java.nio.file.*;
//$$ import java.util.*;
//$$ import java.util.concurrent.*;
//$$ import java.util.concurrent.atomic.AtomicBoolean;
//$$ import java.util.function.Consumer;
//$$ import org.slf4j.Logger;
//$$ import org.slf4j.LoggerFactory;
//$$
//$$ /**
//$$  * API v1. Query only on demand; withdraw through live vanilla inventories, never edit offline playerdata.
//$$  * All authority/mutations run on the server thread; offline NBT reads use one bounded IO worker.
//$$  * Upgrade smoke: query list and offline slots, grant/revoke inventoryTake, withdraw exact quantities,
//$$  * retry the same nonce, change a queried stack, fill recipient inventory, take a full component-bearing
//$$  * shulker's materials and inspect both saved inventories after logout. Take materials from boxes at half-stack
//$$  * quantities (64 -> 32, 16 -> 8). Empty boxes stay in source slots. No unsolicited packets.
//$$  * Discovery upgrade: omit glass cache routes, save verified Chinese glass=1728, query/take32 and verify1696;
//$$  * repeat with a custom prefixed/suffixed boxed _17 source without primary/intermediate files. Compare
//$$  * NOT_FOUND with NOT_ENOUGH, verify newly remembered mappings and repeat in the projection client.
//$$  */
//$$ public final class PlayerSortInventoryApi {
//$$     private static final Logger LOGGER=LoggerFactory.getLogger("carpet-fga-addition/playersort-api");
//$$     public static final int QUERY_INTERVAL_MS = 1000, TAKE_INTERVAL_MS = 1000, MAX_TAKE = 32, MAX_CUSTOM_TAKE = 2304;
//$$     private static final int MAX_SESSIONS = 64, HISTORY = 64, MAX_PENDING = 8;
//$$     private static final long SNAPSHOT_MS = 30_000, SESSION_MS = 60_000;
//$$     private static final Map<UUID, Session> SESSIONS = new HashMap<>();
//$$     private static final Map<String, Lease> LEASES = new HashMap<>();
//$$     private static final Map<String, DeferredClose> CLOSING = new HashMap<>();
//$$     private static final ConcurrentMap<UUID, Gate> GATES = new ConcurrentHashMap<>();
//$$     private static ExecutorService io;
//$$     private static long globalQuery, globalTake;
//$$     private PlayerSortInventoryApi() {}
//$$
//$$     public static void register() {
//$$         PayloadTypeRegistry.serverboundPlay().register(PlayerSortInventoryPayloads.Query.TYPE, PlayerSortInventoryPayloads.Query.CODEC);
//$$         PayloadTypeRegistry.serverboundPlay().register(PlayerSortInventoryPayloads.Take.TYPE, PlayerSortInventoryPayloads.Take.CODEC);
//$$         PayloadTypeRegistry.serverboundPlay().register(PlayerSortInventoryPayloads.DirectTake.TYPE, PlayerSortInventoryPayloads.DirectTake.CODEC);
//$$         PayloadTypeRegistry.serverboundPlay().register(PlayerSortInventoryPayloads.SilentTake.TYPE, PlayerSortInventoryPayloads.SilentTake.CODEC);
//$$         PayloadTypeRegistry.serverboundPlay().register(PlayerSortInventoryPayloads.AmountQuery.TYPE, PlayerSortInventoryPayloads.AmountQuery.CODEC);
//$$         PayloadTypeRegistry.serverboundPlay().register(PlayerSortInventoryPayloads.AmountTake.TYPE, PlayerSortInventoryPayloads.AmountTake.CODEC);
//$$         PayloadTypeRegistry.clientboundPlay().register(PlayerSortInventoryPayloads.Reply.TYPE, PlayerSortInventoryPayloads.Reply.CODEC);
//$$         ServerPlayNetworking.registerGlobalReceiver(PlayerSortInventoryPayloads.AmountQuery.TYPE,
//$$                 (p,c) -> dispatch(c.server(),c.player(),()->query(c.server(),c.player(),p,reply->send(c.player(),reply))));
//$$         ServerPlayNetworking.registerGlobalReceiver(PlayerSortInventoryPayloads.AmountTake.TYPE,
//$$                 (p,c) -> dispatch(c.server(),c.player(),()->{
//$$                     Consumer<PlayerSortInventoryPayloads.Reply> sink=reply->send(c.player(),reply);
//$$                     if (p.silent()) silentTake(c.server(),c.player(),p,sink);
//$$                     else if (p.token().isEmpty()) directTake(c.server(),c.player(),p,sink);
//$$                     else take(c.server(),c.player(),p,sink);
//$$                 }));
//$$         ServerPlayNetworking.registerGlobalReceiver(PlayerSortInventoryPayloads.Query.TYPE,
//$$                 (p,c) -> dispatch(c.server(), c.player(), () -> query(c.server(),c.player(),p, reply -> send(c.player(),reply))));
//$$         ServerPlayNetworking.registerGlobalReceiver(PlayerSortInventoryPayloads.Take.TYPE,
//$$                 (p,c) -> dispatch(c.server(), c.player(), () -> take(c.server(),c.player(),p, reply -> send(c.player(),reply))));
//$$         ServerPlayNetworking.registerGlobalReceiver(PlayerSortInventoryPayloads.DirectTake.TYPE,
//$$                 (p,c) -> dispatch(c.server(), c.player(), () -> directTake(c.server(),c.player(),p, reply -> send(c.player(),reply))));
//$$         ServerPlayNetworking.registerGlobalReceiver(PlayerSortInventoryPayloads.SilentTake.TYPE,
//$$                 (p,c) -> dispatch(c.server(), c.player(), () -> silentTake(c.server(),c.player(),p, reply -> send(c.player(),reply))));
//$$     }
//$$
//$$     private static void dispatch(MinecraftServer server, ServerPlayer player, Runnable task) {
//$$         if (!ServerPlayNetworking.canSend(player, PlayerSortInventoryPayloads.Reply.TYPE)) return;
//$$         Gate gate = GATES.computeIfAbsent(player.getUUID(), ignored -> new Gate());
//$$         long now = System.nanoTime();
//$$         synchronized (gate) {
//$$             if (now - gate.last < 250_000_000L || !gate.queued.compareAndSet(false,true)) return;
//$$             gate.last = now;
//$$         }
//$$         server.execute(() -> {
//$$             try { if (server.getPlayerList().getPlayer(player.getUUID()) == player) task.run(); }
//$$             finally { gate.queued.set(false); }
//$$         });
//$$     }
//$$
//$$     private static void send(ServerPlayer player, PlayerSortInventoryPayloads.Reply reply) {
//$$         try {
//$$             if (ServerPlayNetworking.canSend(player, PlayerSortInventoryPayloads.Reply.TYPE)) ServerPlayNetworking.send(player,reply);
//$$         } catch(RuntimeException e) { LOGGER.warn("Failed to deliver playersort API reply; cached outcome retained",e); }
//$$     }
//$$
//$$     private static Session begin(MinecraftServer server, ServerPlayer player, long id, Object request, int version,
//$$                                   boolean taking, Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         if (!server.isSameThread()) throw new IllegalStateException("playersort API requires server thread");
//$$         if (server.getPlayerList().getPlayer(player.getUUID()) != player) return null;
//$$         if (id < 0) { sink.accept(error(id,"INVALID_REQUEST")); return null; }
//$$         Session session = SESSIONS.get(player.getUUID());
//$$         if (session == null) {
//$$             if (SESSIONS.size() >= MAX_SESSIONS) { sink.accept(error(id,"SERVER_BUSY")); return null; }
//$$             session = new Session(player);
//$$             SESSIONS.put(player.getUUID(),session);
//$$         }
//$$         session.used = System.currentTimeMillis();
//$$         Cached cached = session.history.get(id);
//$$         if (cached != null) {
//$$             sink.accept(cached.request.equals(request) ? cached.reply : error(id,"REQUEST_ID_REUSED"));
//$$             return null;
//$$         }
//$$         if (session.pending && id == session.inflightId) {
//$$             sink.accept(error(id,request.equals(session.inflight) ? "REQUEST_PENDING" : "REQUEST_ID_REUSED"));
//$$             return null;
//$$         }
//$$         if (id <= session.lastId) { sink.accept(error(id,"REPLAY_EXPIRED")); return null; }
//$$         session.lastId = id;
//$$         if (version != PlayerSortInventoryPayloads.VERSION) { finish(session,id,request,error(id,"UNSUPPORTED_VERSION"),sink); return null; }
//$$         if (!FGASettings.isFakePlayerItemSortEnabled() || FakePlayerItemSortConfig.isInvalid()) {
//$$             finish(session,id,request,error(id,"DISABLED"),sink); return null;
//$$         }
//$$         if (PlayerPossessionManager.isParticipant(player)
//$$                 || taking && !FakePlayerItemSortConfig.canUse(player.createCommandSourceStack(), "stock")
//$$                 || !FakePlayerItemSortConfig.canUse(player.createCommandSourceStack(), taking ? "inventoryTake" : "stock")) {
//$$             finish(session,id,request,error(id,"PERMISSION_DENIED"),sink); return null;
//$$         }
//$$         long now = System.currentTimeMillis();
//$$         long previous = taking ? session.lastTake : session.lastQuery;
//$$         if (now - previous < (taking ? TAKE_INTERVAL_MS : QUERY_INTERVAL_MS)) {
//$$             finish(session,id,request,error(id,"RATE_LIMITED"),sink); return null;
//$$         }
//$$         if (now - (taking ? globalTake : globalQuery) < (taking ? 250 : 100)) {
//$$             finish(session,id,request,error(id,"SERVER_BUSY"),sink); return null;
//$$         }
//$$         if (taking) globalTake=now; else globalQuery=now;
//$$         if (taking) session.lastTake = now; else session.lastQuery = now;
//$$         if (session.pending) { finish(session,id,request,error(id,"REQUEST_PENDING"),sink); return null; }
//$$         session.pending = true;
//$$         session.inflightId = id; session.inflight = request;
//$$         return session;
//$$     }
//$$
//$$     private static PlayerSortInventoryPayloads.Reply error(long id, String status) {
//$$         return reply(id,status,new JsonObject());
//$$     }
//$$     private static PlayerSortInventoryPayloads.Reply reply(long id, String status, JsonObject data) {
//$$         data.addProperty("version",1); data.addProperty("status",status);
//$$         if (data.toString().length()>PlayerSortInventoryPayloads.MAX_JSON_CHARS)
//$$             return error(id,"REPLY_TOO_LARGE");
//$$         return new PlayerSortInventoryPayloads.Reply(id,data.toString());
//$$     }
//$$     private static void finish(Session s, long id, Object request, PlayerSortInventoryPayloads.Reply reply,
//$$                                Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         if (s.inflightId == id) { s.pending = false; s.inflight = null; }
//$$         s.history.put(id,new Cached(request,reply));
//$$         if (s.history.size() > HISTORY) s.history.remove(s.history.keySet().iterator().next());
//$$         sink.accept(reply);
//$$     }
//$$     private static boolean active(MinecraftServer server, Session s) {
//$$         return SESSIONS.get(s.player.getUUID()) == s
//$$                 && server.getPlayerList().getPlayer(s.player.getUUID()) == s.player;
//$$     }
//$$
//$$     /** Preserve the existing Java entrypoint descriptors as well as the original wire codecs. */
//$$     public static void query(MinecraftServer server,ServerPlayer player,PlayerSortInventoryPayloads.Query p,
//$$                              Consumer<PlayerSortInventoryPayloads.Reply> sink) { query(server,player,(PlayerSortInventoryPayloads.QueryRequest)p,sink); }
//$$     public static void take(MinecraftServer server,ServerPlayer player,PlayerSortInventoryPayloads.Take p,
//$$                             Consumer<PlayerSortInventoryPayloads.Reply> sink) { take(server,player,(PlayerSortInventoryPayloads.TakeRequest)p,sink); }
//$$     public static void directTake(MinecraftServer server,ServerPlayer player,PlayerSortInventoryPayloads.DirectTake p,
//$$                                   Consumer<PlayerSortInventoryPayloads.Reply> sink) { directTake(server,player,(PlayerSortInventoryPayloads.TakeRequest)p,sink); }
//$$     public static void silentTake(MinecraftServer server,ServerPlayer player,PlayerSortInventoryPayloads.SilentTake p,
//$$                                   Consumer<PlayerSortInventoryPayloads.Reply> sink) { silentTake(server,player,(PlayerSortInventoryPayloads.TakeRequest)p,sink); }
//$$
//$$     /** Server Java API, also useful to independent probes; the same permissions and limits apply. */
//$$     public static void query(MinecraftServer server, ServerPlayer player, PlayerSortInventoryPayloads.QueryRequest p,
//$$                              Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         Session s = begin(server,player,p.requestId(),p,p.version(),false,sink);
//$$         if (s == null) return;
//$$         try {
//$$             validateAmount(p.amount());
//$$             if (p.cursor() < 0 || p.kind() < 0 || p.kind() > 2) throw new IllegalArgumentException("INVALID_REQUEST");
//$$             if (p.kind() == 2) {
//$$                 Identifier item = Identifier.tryParse(p.target());
//$$                 if (item == null || !item.toString().equals(p.target()) || !BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("INVALID_ITEM");
//$$                 s.stacks=null; s.token="";
//$$                 startMaterialSearch(server,s,p,sink);
//$$                 return;
//$$             }
//$$             s.search=null;
//$$             if (p.kind() == 0) {
//$$                 if (!p.target().isEmpty()) throw new IllegalArgumentException("INVALID_REQUEST");
//$$                 List<Map.Entry<String,String>> routes = new ArrayList<>(FakePlayerItemSortManager.apiRoutes().entrySet());
//$$                 if (routes.size() > 4096) throw new IllegalArgumentException("TOO_MANY_ROUTES");
//$$                 if (p.cursor() > routes.size()) throw new IllegalArgumentException("INVALID_CURSOR");
//$$                 JsonObject out = new JsonObject(); JsonArray entries = new JsonArray();
//$$                 int end = Math.min(routes.size(),p.cursor()+24);
//$$                 int actualEnd=p.cursor();
//$$                 for (int i=p.cursor();i<end;i++) {
//$$                     JsonObject entry = new JsonObject();
//$$                     entry.addProperty("target",routes.get(i).getKey());
//$$                     entry.addProperty("itemId",routes.get(i).getValue());
//$$                     if (entries.toString().length()+entry.toString().length()>6500 && !entries.isEmpty()) break;
//$$                     entries.add(entry);
//$$                     actualEnd=i+1;
//$$                 }
//$$                 out.addProperty("kind","routes"); out.addProperty("nextCursor",actualEnd < routes.size() ? actualEnd : -1);
//$$                 out.addProperty("overflowMax",999); out.add("entries",entries);
//$$                 out.addProperty("queryIntervalMs",QUERY_INTERVAL_MS); out.addProperty("takeIntervalMs",TAKE_INTERVAL_MS);
//$$                 out.addProperty("maxTake",MAX_TAKE);
//$$                 finish(s,p.requestId(),p,reply(p.requestId(),"OK",out),sink); return;
//$$             }
//$$             FakePlayerItemSortManager.apiValidateTarget(server,p.target());
//$$             if (p.cursor() > 36) throw new IllegalArgumentException("INVALID_CURSOR");
//$$             if (p.cursor() > 0) {
//$$                 if (!validSnapshot(s,p.target())) throw new IllegalArgumentException("SNAPSHOT_EXPIRED");
//$$                 finish(s,p.requestId(),p,inventoryReply(server,s,p),sink); return;
//$$             }
//$$             ServerPlayer online = server.getPlayerList().getPlayerByName(p.target());
//$$             if (online != null) {
//$$                 snapshot(server,s,p.target(),null);
//$$                 finish(s,p.requestId(),p,inventoryReply(server,s,p),sink); return;
//$$             }
//$$             Path path = FakePlayerItemSortManager.apiDataPath(server,p.target());
//$$             ExecutorService executor = io();
//$$             executor.execute(() -> {
//$$                 CompoundTag tag = null; String failure = null;
//$$                 try {
//$$                     if (!Files.isRegularFile(path)) failure = "NOT_FOUND";
//$$                     else if (Files.size(path) > 2_097_152) failure = "INVENTORY_TOO_LARGE";
//$$                     else tag = NbtIo.readCompressed(path,NbtAccounter.create(8_388_608L));
//$$                 } catch (Exception e) { failure = "READ_FAILED"; }
//$$                 CompoundTag data = tag; String failed = failure;
//$$                 server.execute(() -> {
//$$                     if (!active(server,s)) return;
//$$                     try {
//$$                         if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IllegalArgumentException("DISABLED");
//$$                         if (PlayerPossessionManager.isParticipant(player)
//$$                                 || !FakePlayerItemSortConfig.canUse(player.createCommandSourceStack(),"stock"))
//$$                             throw new IllegalArgumentException("PERMISSION_DENIED");
//$$                         if (failed != null) throw new IllegalArgumentException(failed);
//$$                         snapshot(server,s,p.target(),data);
//$$                         finish(s,p.requestId(),p,inventoryReply(server,s,p),sink);
//$$                     } catch (IllegalArgumentException e) { finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink); }
//$$                     catch (RuntimeException e) { finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink); }
//$$                 });
//$$             });
//$$         } catch (RejectedExecutionException e) { finish(s,p.requestId(),p,error(p.requestId(),"SERVER_BUSY"),sink); }
//$$         catch (IllegalArgumentException e) { finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink); }
//$$         catch (Exception e) { finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink); }
//$$     }
//$$
//$$
//$$     /** Index filenames only, off-thread; never read arbitrary players' inventories to discover stock. */
//$$     private static void startMaterialSearch(MinecraftServer server,Session s,PlayerSortInventoryPayloads.QueryRequest p,
//$$                                             Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         if (p.cursor()>0) {
//$$             Search search=s.search;
//$$             if (search==null || !search.itemId.equals(p.target()) || search.amount!=p.amount() || System.currentTimeMillis()>search.expires)
//$$                 throw new IllegalArgumentException("SNAPSHOT_EXPIRED");
//$$             if (p.cursor()!=search.nextCursor) throw new IllegalArgumentException("INVALID_CURSOR");
//$$             searchMaterial(server,s,p,sink,p.cursor(),4); return;
//$$         }
//$$         s.search=null;
//$$         List<String> bases=FakePlayerItemSortManager.apiMaterialBases(p.target());
//$$         if (bases.size()>64) throw new IllegalArgumentException("TOO_MANY_ROUTES");
//$$         Set<UUID> online=new HashSet<>();
//$$         server.getPlayerList().getPlayers().forEach(player->online.add(player.getUUID()));
//$$         Path directory=server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
//$$         io().execute(()->{
//$$             List<String> candidates=null; String failure=null;
//$$             try {
//$$                 Set<UUID> existing=new HashSet<>(online); int entries=0;
//$$                 if (Files.isDirectory(directory)) try (var files=Files.newDirectoryStream(directory,"*.dat")) {
//$$                     for (Path file:files) {
//$$                         if (++entries>65536) throw new IllegalArgumentException("INVENTORY_INDEX_TOO_LARGE");
//$$                         String name=file.getFileName().toString();
//$$                         try { existing.add(UUID.fromString(name.substring(0,name.length()-4))); }
//$$                         catch (IllegalArgumentException ignored) { /* Non-UUID filenames are not playerdata. */ }
//$$                     }
//$$                 }
//$$                 candidates=existingMaterialCandidates(bases,existing);
//$$             } catch (IllegalArgumentException e) { failure=e.getMessage(); }
//$$             catch (Exception e) { failure="READ_FAILED"; }
//$$             List<String> found=candidates; String failed=failure;
//$$             server.execute(()->{
//$$                 if (!active(server,s)) return;
//$$                 if (failed!=null) { finish(s,p.requestId(),p,error(p.requestId(),failed),sink); return; }
//$$                 s.search=new Search(p.target(),p.amount(),found,System.currentTimeMillis()+SNAPSHOT_MS);
//$$                 searchMaterial(server,s,p,sink,0,4);
//$$             });
//$$         });
//$$     }
//$$
//$$     static List<String> existingMaterialCandidates(List<String> bases,Set<UUID> existing) {
//$$         if (bases.size()>64) throw new IllegalArgumentException("TOO_MANY_ROUTES");
//$$         Set<String> names=new LinkedHashSet<>();
//$$         for (String base:bases) for (int order=0;order<=999;order++) {
//$$             int index=order==0 ? 0 : 1000-order; // Primary first, then the highest existing overflow.
//$$             String name=index==0 ? base : base+"_"+index;
//$$             if (name.length()>64) continue;
//$$             UUID uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
//$$             if (existing.contains(uuid)) names.add(name);
//$$             if (names.size()>4096) throw new IllegalArgumentException("TOO_MANY_ROUTES");
//$$         }
//$$         return List.copyOf(names);
//$$     }
//$$
//$$     static String materialFailure(boolean verified,String rejection) {
//$$         return verified ? "NOT_ENOUGH" : rejection==null ? "NOT_FOUND" : rejection;
//$$     }
//$$
//$$     /** Four existing candidate inventories per request, including sparse _1 .. _999 suffixes. */
//$$     private static void searchMaterial(MinecraftServer server, Session s, PlayerSortInventoryPayloads.QueryRequest p,
//$$                                        Consumer<PlayerSortInventoryPayloads.Reply> sink,
//$$                                        int cursor, int budget) {
//$$         if (!active(server,s)) return;
//$$         try {
//$$             if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IllegalArgumentException("DISABLED");
//$$             if (PlayerPossessionManager.isParticipant(s.player)
//$$                     || !FakePlayerItemSortConfig.canUse(s.player.createCommandSourceStack(),"stock"))
//$$                 throw new IllegalArgumentException("PERMISSION_DENIED");
//$$             Search search=s.search;
//$$             if (search==null || System.currentTimeMillis()>search.expires) throw new IllegalArgumentException("SNAPSHOT_EXPIRED");
//$$             if (cursor >= search.candidates.size()) {
//$$                 finish(s,p.requestId(),p,error(p.requestId(),materialFailure(search.verified,search.rejection)),sink); return;
//$$             }
//$$             if (budget == 0) {
//$$                 JsonObject out = new JsonObject(); out.addProperty("kind","material"); out.addProperty("nextCursor",cursor);
//$$                 search.nextCursor=cursor;
//$$                 finish(s,p.requestId(),p,reply(p.requestId(),"SEARCH_CONTINUE",out),sink); return;
//$$             }
//$$             String target=search.candidates.get(cursor);
//$$             try { FakePlayerItemSortManager.apiValidateTarget(server,target,p.target()); }
//$$             catch (IllegalArgumentException e) {
//$$                 search.rejection=e.getMessage(); searchMaterial(server,s,p,sink,cursor+1,budget-1); return;
//$$             }
//$$             ServerPlayer online = server.getPlayerList().getPlayerByName(target);
//$$             if (online != null) {
//$$                 considerMaterial(server,s,p,sink,cursor,budget,target,null); return;
//$$             }
//$$             Path path = FakePlayerItemSortManager.apiDataPath(server,target,p.target());
//$$             io().execute(() -> {
//$$                 CompoundTag tag=null; String failed=null;
//$$                 try {
//$$                     if (!Files.isRegularFile(path)) failed="NOT_FOUND";
//$$                     else if (Files.size(path)>2_097_152) failed="INVENTORY_TOO_LARGE";
//$$                     else tag=NbtIo.readCompressed(path,NbtAccounter.create(8_388_608L));
//$$                 } catch (Exception e) { failed="READ_FAILED"; }
//$$                 CompoundTag data=tag; String failure=failed;
//$$                 server.execute(() -> {
//$$                     if (!active(server,s)) return;
//$$                     try {
//$$                         if ("NOT_FOUND".equals(failure))
//$$                             searchMaterial(server,s,p,sink,cursor+1,budget-1);
//$$                         else if (failure!=null) finish(s,p.requestId(),p,error(p.requestId(),failure),sink);
//$$                         else considerMaterial(server,s,p,sink,cursor,budget,target,data);
//$$                     } catch (IllegalArgumentException e) { finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink); }
//$$                     catch (RuntimeException e) { finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink); }
//$$                 });
//$$             });
//$$         } catch (IllegalArgumentException e) { finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink); }
//$$         catch (Exception e) { finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink); }
//$$     }
//$$
//$$     private static void considerMaterial(MinecraftServer server, Session s, PlayerSortInventoryPayloads.QueryRequest p,
//$$                                          Consumer<PlayerSortInventoryPayloads.Reply> sink,
//$$                                          int cursor, int budget, String target, CompoundTag tag) {
//$$         if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IllegalArgumentException("DISABLED");
//$$         if (PlayerPossessionManager.isParticipant(s.player)
//$$                 || !FakePlayerItemSortConfig.canUse(s.player.createCommandSourceStack(),"stock"))
//$$             throw new IllegalArgumentException("PERMISSION_DENIED");
//$$         try { snapshot(server,s,target,tag,p.target()); }
//$$         catch (IllegalArgumentException e) {
//$$             if (!Set.of("UNVERIFIED_TARGET","PROTECTED_TARGET","TARGET_BUSY","INVALID_TARGET").contains(e.getMessage())) throw e;
//$$             s.search.rejection=e.getMessage(); searchMaterial(server,s,p,sink,cursor+1,budget-1); return;
//$$         }
//$$         s.search.verified=true;
//$$         FakePlayerItemSortManager.apiRememberMaterial(target,p.target());
//$$         ItemStack prototype=materialPrototype(s.stacks,p.target(),p.amount());
//$$         int half=prototype.isEmpty() ? 0 : takeAmount(prototype,p.amount());
//$$         long available=prototype.isEmpty() ? 0 : materialCount(s.stacks,prototype);
//$$         if (half>0 && available>=half) {
//$$             JsonObject out=new JsonObject();
//$$             out.addProperty("kind","material"); out.addProperty("itemId",p.target()); out.addProperty("target",target);
//$$             out.addProperty("token",s.token); out.addProperty("available",available); out.addProperty("takeCount",half);
//$$             out.addProperty("expiresInMs",SNAPSHOT_MS);
//$$             finish(s,p.requestId(),p,reply(p.requestId(),"OK",out),sink);
//$$         } else {
//$$             s.stacks=null; s.token="";
//$$             searchMaterial(server,s,p,sink,cursor+1,budget-1);
//$$         }
//$$     }
//$$
//$$     private static void validateAmount(int amount) {
//$$         if (amount<0 || amount>MAX_CUSTOM_TAKE) throw new IllegalArgumentException("INVALID_REQUEST");
//$$     }
//$$     static int takeAmount(ItemStack prototype,int amount) { return amount==0 ? halfStack(prototype) : amount; }
//$$     static int halfStack(ItemStack prototype) { return Math.max(1,Math.min(64,prototype.getMaxStackSize())/2); }
//$$
//$$     /** Decode current 26.3 saves without claiming obsolete offhand slots that vanilla would ignore. */
//$$     static ItemStack[] decodeOfflineInventory(HolderLookup.Provider registry, CompoundTag data) {
//$$         if(data.get("RootVehicle")!=null) throw new IllegalArgumentException("PROTECTED_TARGET");
//$$         ItemStack[] result=new ItemStack[37]; Arrays.fill(result,ItemStack.EMPTY);
//$$         Set<Integer> seen=new HashSet<>();
//$$         if(data.get("Inventory")!=null && !(data.get("Inventory") instanceof ListTag)) throw new IllegalArgumentException("READ_FAILED");
//$$         ListTag entries=data.getListOrEmpty("Inventory");
//$$         for(int i=0;i<entries.size();i++) {
//$$             if(!(entries.get(i) instanceof CompoundTag entry)) throw new IllegalArgumentException("READ_FAILED");
//$$             if(!(entry.get("Slot") instanceof ByteTag)) throw new IllegalArgumentException("READ_FAILED");
//$$             int slot=entry.getByteOr("Slot",(byte)-1);
//$$             if(slot==(byte)150 || slot==36 || slot==40) throw new IllegalArgumentException("LEGACY_OFFHAND");
//$$             if(slot<0 || slot>=36) continue;
//$$             if(!seen.add(slot)) throw new IllegalArgumentException("READ_FAILED");
//$$             result[slot]=ItemStack.OPTIONAL_CODEC.parse(registry.createSerializationContext(NbtOps.INSTANCE),entry).getOrThrow();
//$$         }
//$$         Tag equipment=data.get("equipment");
//$$         if(equipment!=null) result[36]=EntityEquipment.CODEC.parse(registry.createSerializationContext(NbtOps.INSTANCE),equipment)
//$$                 .getOrThrow().get(EquipmentSlot.OFFHAND).copy();
//$$         return result;
//$$     }
//$$
//$$     private static boolean box(ItemStack stack) {
//$$         return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
//$$     }
//$$     static NonNullList<ItemStack> contents(ItemStack stack) {
//$$         NonNullList<ItemStack> result=NonNullList.withSize(27,ItemStack.EMPTY);
//$$         ItemContainerContents container=stack.get(DataComponents.CONTAINER);
//$$         if (container!=null) container.copyInto(result);
//$$         for (int i=0;i<result.size();i++) result.set(i,result.get(i).copy());
//$$         return result;
//$$     }
//$$     static ItemStack materialPrototype(ItemStack[] stacks, String itemId) {
//$$         return materialPrototype(stacks,itemId,0);
//$$     }
//$$     static ItemStack materialPrototype(ItemStack[] stacks,String itemId,int amount) {
//$$         Map<MaterialKey,Long> counts=new LinkedHashMap<>();
//$$         for (ItemStack stack:stacks) {
//$$             if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId))
//$$                 counts.merge(new MaterialKey(stack.copyWithCount(1)),(long)stack.getCount(),Long::sum);
//$$             else if (box(stack) && stack.getCount()==1) for (ItemStack inner:contents(stack))
//$$                 if (!inner.isEmpty() && BuiltInRegistries.ITEM.getKey(inner.getItem()).toString().equals(itemId))
//$$                     counts.merge(new MaterialKey(inner.copyWithCount(1)),(long)inner.getCount(),Long::sum);
//$$         }
//$$         for (var entry:counts.entrySet()) if (entry.getValue()>=takeAmount(entry.getKey().stack(),amount))
//$$             return entry.getKey().stack();
//$$         return ItemStack.EMPTY;
//$$     }
//$$     static long materialCount(ItemStack[] stacks, ItemStack prototype) {
//$$         long count=0;
//$$         for (ItemStack stack:stacks) {
//$$             if (ItemStack.isSameItemSameComponents(stack,prototype)) count+=stack.getCount();
//$$             // Invalid stacked container items are not interpreted as one duplicated box.
//$$             else if (box(stack) && stack.getCount()==1) for (ItemStack inner:contents(stack))
//$$                 if (ItemStack.isSameItemSameComponents(inner,prototype)) count+=inner.getCount();
//$$         }
//$$         return count;
//$$     }
//$$     /** Mutates copies only; preserves empty boxes and every unmatched component-bearing stack. */
//$$     static boolean extractMaterial(ItemStack[] stacks, ItemStack prototype, int amount) {
//$$         int left=amount;
//$$         for (int i=0;i<stacks.length && left>0;i++) {
//$$             ItemStack stack=stacks[i];
//$$             if (ItemStack.isSameItemSameComponents(stack,prototype)) {
//$$                 int n=Math.min(left,stack.getCount()); stack.shrink(n); left-=n;
//$$                 if (stack.isEmpty()) stacks[i]=ItemStack.EMPTY;
//$$             } else if (box(stack) && stack.getCount()==1) {
//$$                 NonNullList<ItemStack> values=contents(stack);
//$$                 boolean changed=false;
//$$                 for (int j=0;j<values.size() && left>0;j++) {
//$$                     ItemStack inner=values.get(j);
//$$                     if (ItemStack.isSameItemSameComponents(inner,prototype)) {
//$$                         int n=Math.min(left,inner.getCount()); inner.shrink(n); left-=n; changed|=n>0;
//$$                         if (inner.isEmpty()) values.set(j,ItemStack.EMPTY);
//$$                     }
//$$                 }
//$$                 if (changed) stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(values));
//$$             }
//$$         }
//$$         return left==0;
//$$     }
//$$
//$$     static ExecutorService io() {
//$$         if (io == null) io = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),
//$$                 task -> { Thread t = new Thread(task,"fga-playersort-api-read"); t.setDaemon(true); return t; },
//$$                 new ThreadPoolExecutor.AbortPolicy());
//$$         return io;
//$$     }
//$$     private static void snapshot(MinecraftServer server, Session s, String target, CompoundTag tag) {
//$$         snapshot(server,s,target,tag,null);
//$$     }
//$$     private static void snapshot(MinecraftServer server,Session s,String target,CompoundTag tag,String materialId) {
//$$         ItemStack[] stacks = FakePlayerItemSortManager.apiSnapshot(server,target,tag,materialId);
//$$         int bytes = 0;
//$$         for (ItemStack stack : stacks) {
//$$             bytes += ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack)
//$$                     .getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
//$$             if (bytes > 131_072) throw new IllegalArgumentException("INVENTORY_TOO_LARGE");
//$$         }
//$$         s.stacks = stacks; s.target = target; s.token = UUID.randomUUID().toString();
//$$         s.materialId=materialId;
//$$         s.expires = System.currentTimeMillis()+SNAPSHOT_MS;
//$$         if (materialId!=null) PlayerSortHeadRestock.request(server,target,materialId);
//$$     }
//$$     private static boolean validSnapshot(Session s, String target) {
//$$         return s.stacks != null && s.target.equals(target) && System.currentTimeMillis() < s.expires;
//$$     }
//$$     private static PlayerSortInventoryPayloads.Reply inventoryReply(MinecraftServer server, Session s,
//$$                                                                     PlayerSortInventoryPayloads.QueryRequest p) {
//$$         JsonObject out = new JsonObject(); JsonArray entries = new JsonArray();
//$$         int end=p.cursor();
//$$         for (;end < Math.min(37,p.cursor()+12);end++) {
//$$             ItemStack stack=s.stacks[end];
//$$             JsonObject entry = new JsonObject(); entry.addProperty("slot",end); entry.addProperty("count",stack.getCount());
//$$             if (!stack.isEmpty()) {
//$$                 entry.addProperty("itemId",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
//$$                 String name=stack.getHoverName().getString();
//$$                 entry.addProperty("name",name.substring(0,Math.min(128,name.length())));
//$$                 JsonElement full=ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow();
//$$                 if (full.toString().length() <= 2048) entry.add("stack",full);
//$$                 else entry.addProperty("previewOmitted",true);
//$$             }
//$$             if (entries.toString().length()+entry.toString().length() > 6500 && !entries.isEmpty()) break;
//$$             entries.add(entry);
//$$         }
//$$         out.addProperty("kind","inventory"); out.addProperty("target",s.target); out.addProperty("token",s.token);
//$$         out.addProperty("expiresInMs",Math.max(0,s.expires-System.currentTimeMillis()));
//$$         out.addProperty("nextCursor",end < 37 ? end : -1); out.add("entries",entries);
//$$         return reply(p.requestId(),"OK",out);
//$$     }
//$$
//$$     public static void take(MinecraftServer server, ServerPlayer player, PlayerSortInventoryPayloads.TakeRequest p,
//$$                             Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         Session s = begin(server,player,p.requestId(),p,p.version(),true,sink);
//$$         if (s == null) return;
//$$         try {
//$$             validateAmount(p.amount());
//$$             Identifier item=Identifier.tryParse(p.itemId());
//$$             if (item==null || !item.toString().equals(p.itemId()) || !BuiltInRegistries.ITEM.containsKey(item))
//$$                 throw new IllegalArgumentException("INVALID_REQUEST");
//$$             if (!validSnapshot(s,s.target) || !s.token.equals(p.token())) throw new IllegalArgumentException("SNAPSHOT_EXPIRED");
//$$             if (s.materialId!=null && !s.materialId.equals(p.itemId())) throw new IllegalArgumentException("INVALID_ITEM");
//$$             prepareTake(server,s,p,p.requestId(),p.itemId(),sink);
//$$         } catch (IllegalArgumentException e) {
//$$             finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink);
//$$             releaseOwnedRequest(server,s,p);
//$$         } catch (RuntimeException e) {
//$$             finish(s,p.requestId(),p,error(p.requestId(),"TRANSFER_FAILED"),sink);
//$$             releaseOwnedRequest(server,s,p);
//$$         }
//$$     }
//$$
//$$     public static void directTake(MinecraftServer server, ServerPlayer player, PlayerSortInventoryPayloads.TakeRequest p,
//$$                                   Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         Session s=begin(server,player,p.requestId(),p,p.version(),true,sink);
//$$         if (s==null) return;
//$$         s.stacks=null; s.token=""; s.search=null;
//$$         try {
//$$             validateAmount(p.amount());
//$$             Identifier item=Identifier.tryParse(p.itemId());
//$$             if (item==null || !item.toString().equals(p.itemId()) || !BuiltInRegistries.ITEM.containsKey(item))
//$$                 throw new IllegalArgumentException("INVALID_ITEM");
//$$             String target=FakePlayerItemSortManager.apiChineseMaterialName(p.itemId());
//$$             FakePlayerItemSortManager.apiValidateTarget(server,target,p.itemId());
//$$             if (server.getPlayerList().getPlayerByName(target)!=null) {
//$$                 snapshot(server,s,target,null,p.itemId());
//$$                 prepareTake(server,s,p,p.requestId(),p.itemId(),sink); return;
//$$             }
//$$             Path path=FakePlayerItemSortManager.apiDataPath(server,target,p.itemId());
//$$             io().execute(()->{
//$$                 CompoundTag tag=null; String failure=null;
//$$                 try {
//$$                     if (!Files.isRegularFile(path)) failure="NOT_FOUND";
//$$                     else if (Files.size(path)>2_097_152) failure="INVENTORY_TOO_LARGE";
//$$                     else tag=NbtIo.readCompressed(path,NbtAccounter.create(8_388_608L));
//$$                 } catch (Exception e) { failure="READ_FAILED"; }
//$$                 CompoundTag data=tag; String failed=failure;
//$$                 server.execute(()->{
//$$                     if (!active(server,s)) return;
//$$                     try {
//$$                         if (failed!=null) throw new IllegalArgumentException(failed);
//$$                         snapshot(server,s,target,data,p.itemId());
//$$                         prepareTake(server,s,p,p.requestId(),p.itemId(),sink);
//$$                     } catch (IllegalArgumentException e) {
//$$                         finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink);
//$$                         releaseOwnedRequest(server,s,p);
//$$                     } catch (RuntimeException e) {
//$$                         LOGGER.warn("Playersort direct take preparation failed",e);
//$$                         finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink);
//$$                         releaseOwnedRequest(server,s,p);
//$$                     }
//$$                 });
//$$             });
//$$         } catch (RejectedExecutionException e) { finish(s,p.requestId(),p,error(p.requestId(),"SERVER_BUSY"),sink); }
//$$         catch (IllegalArgumentException e) { finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink); releaseOwnedRequest(server,s,p); }
//$$         catch (Exception e) { finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink); releaseOwnedRequest(server,s,p); }
//$$     }
//$$
//$$     public static void silentTake(MinecraftServer server,ServerPlayer player,PlayerSortInventoryPayloads.TakeRequest p,
//$$                                   Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         Session s=begin(server,player,p.requestId(),p,p.version(),true,sink);
//$$         if (s==null) return;
//$$         try {
//$$             validateAmount(p.amount());
//$$             Identifier item=Identifier.tryParse(p.itemId());
//$$             if (item==null || !item.toString().equals(p.itemId()) || !BuiltInRegistries.ITEM.containsKey(item))
//$$                 throw new IllegalArgumentException("INVALID_ITEM");
//$$             boolean direct=p.token().isEmpty();
//$$             String target=direct ? FakePlayerItemSortManager.apiChineseMaterialName(p.itemId()) : s.target;
//$$             if (!direct && (!validSnapshot(s,target) || !p.token().equals(s.token)))
//$$                 throw new IllegalArgumentException("SNAPSHOT_EXPIRED");
//$$             if (!direct && s.materialId!=null && !s.materialId.equals(p.itemId())) throw new IllegalArgumentException("INVALID_ITEM");
//$$             FakePlayerItemSortManager.apiValidateTarget(server,target,p.itemId());
//$$             if (LEASES.containsKey(target) || CLOSING.containsKey(target)
//$$                     || LEASES.size()+CLOSING.size()+PlayerSortOfflineWithdrawal.lockCount()>=MAX_PENDING)
//$$                 throw new IllegalArgumentException("TARGET_BUSY");
//$$             if (server.getPlayerList().getPlayerByName(target)!=null) {
//$$                 if (direct) snapshot(server,s,target,null,p.itemId());
//$$                 prepareTake(server,s,p,p.requestId(),p.itemId(),sink); return;
//$$             }
//$$             ItemStack[] expected=direct ? null : s.stacks;
//$$             PlayerSortOfflineWithdrawal.withdraw(server,player,target,p.itemId(),p.amount(),expected,io(),
//$$                     ()->active(server,s) && s.pending && s.inflightId==p.requestId(),(status,moved)->{
//$$                         JsonObject out=new JsonObject();
//$$                         if (status.equals("OK")) {
//$$                             s.stacks=null; s.token=""; s.search=null;
//$$                             out.addProperty("kind","take"); out.addProperty("itemId",p.itemId());
//$$                             out.addProperty("moved",moved); out.addProperty("refreshRequired",true);
//$$                             PlayerSortHeadRestock.request(server,target,p.itemId());
//$$                         }
//$$                         finish(s,p.requestId(),p,reply(p.requestId(),status,out),sink);
//$$                     });
//$$         } catch (RejectedExecutionException e) { finish(s,p.requestId(),p,error(p.requestId(),"SERVER_BUSY"),sink); }
//$$         catch (IllegalArgumentException e) { finish(s,p.requestId(),p,error(p.requestId(),e.getMessage()),sink); releaseOwnedRequest(server,s,p); }
//$$         catch (Exception e) { finish(s,p.requestId(),p,error(p.requestId(),"READ_FAILED"),sink); releaseOwnedRequest(server,s,p); }
//$$     }
//$$
//$$     private static void prepareTake(MinecraftServer server,Session s,PlayerSortInventoryPayloads.TakeRequest request,long id,String itemId,
//$$                                     Consumer<PlayerSortInventoryPayloads.Reply> sink) {
//$$         if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IllegalArgumentException("DISABLED");
//$$         if (PlayerPossessionManager.isParticipant(s.player)
//$$                 || !FakePlayerItemSortConfig.canUse(s.player.createCommandSourceStack(),"stock")
//$$                 || !FakePlayerItemSortConfig.canUse(s.player.createCommandSourceStack(),"inventoryTake"))
//$$             throw new IllegalArgumentException("PERMISSION_DENIED");
//$$         FakePlayerItemSortManager.apiValidateTarget(server,s.target,s.materialId);
//$$         if (LEASES.containsKey(s.target) || CLOSING.containsKey(s.target) || PlayerSortOfflineWithdrawal.isLocked(s.target)
//$$                 || LEASES.size()+CLOSING.size()+PlayerSortOfflineWithdrawal.lockCount() >= MAX_PENDING) throw new IllegalArgumentException("TARGET_BUSY");
//$$         ItemStack prototype=materialPrototype(s.stacks,itemId,request.amount());
//$$         if (prototype.isEmpty() || materialCount(s.stacks,prototype)<takeAmount(prototype,request.amount()))
//$$             throw new IllegalArgumentException("NOT_ENOUGH");
//$$         boolean owned = server.getPlayerList().getPlayerByName(s.target)==null;
//$$         Lease lease=new Lease(server,s,request,id,itemId,takeAmount(prototype,request.amount()),sink,owned,System.currentTimeMillis()+10_000);
//$$         LEASES.put(s.target,lease);
//$$         if (owned) {
//$$             if (!FakePlayerItemSortManager.apiSpawn(server,s.player,s.target,s.materialId)) throw new IllegalArgumentException("SPAWN_FAILED");
//$$         }
//$$         if (server.getPlayerList().getPlayerByName(s.target)!=null) complete(lease);
//$$         // Otherwise tick finishes the one in-flight Carpet spawn; duplicates cannot create another.
//$$     }
//$$
//$$     private static void complete(Lease lease) {
//$$         Session s=lease.session; var p=lease.request; MinecraftServer server=lease.server;
//$$         try {
//$$             if (!active(server,s)) return;
//$$             if (!FGASettings.isFakePlayerItemSortEnabled()) throw new IllegalArgumentException("DISABLED");
//$$             if (!FakePlayerItemSortConfig.canUse(s.player.createCommandSourceStack(),"inventoryTake")
//$$                     || !FakePlayerItemSortConfig.canUse(s.player.createCommandSourceStack(),"stock")
//$$                     || PlayerPossessionManager.isParticipant(s.player)) throw new IllegalArgumentException("PERMISSION_DENIED");
//$$             FakePlayerItemSortManager.apiValidateTarget(server,s.target,s.materialId);
//$$             ServerPlayer target=server.getPlayerList().getPlayerByName(s.target);
//$$             if (!(target instanceof EntityPlayerMPFake) || target==s.player) throw new IllegalArgumentException("PROTECTED_TARGET");
//$$             if (lease.owned) FakePlayerItemSortManager.apiSpawnResolved(s.target);
//$$             ItemStack[] current=FakePlayerItemSortManager.apiSnapshot(server,s.target,null,s.materialId);
//$$             for (int i=0;i<37;i++) if (!ItemStack.matches(current[i],s.stacks[i])) throw new IllegalArgumentException("STALE_SNAPSHOT");
//$$             ItemStack prototype=materialPrototype(current,lease.itemId,lease.amount);
//$$             int amount=lease.amount;
//$$             if (prototype.isEmpty() || materialCount(current,prototype)<amount) throw new IllegalArgumentException("NOT_ENOUGH");
//$$             ItemStack[] destinations=new ItemStack[36];
//$$             for (int i=0;i<36;i++) destinations[i]=s.player.getInventory().getItem(i).copy();
//$$             ItemStack moved=prototype.copyWithCount(amount);
//$$             if (!planInsertion(destinations,moved)) throw new IllegalArgumentException("NO_SPACE");
//$$             if (!extractMaterial(current,prototype,amount)) throw new IllegalArgumentException("NOT_ENOUGH");
//$$             // After validation no disk operation or asynchronous boundary occurs between the two mutations.
//$$             for (int i=0;i<37;i++) FakePlayerItemSortManager.apiSetSlot(target,i,current[i]);
//$$             for (int i=0;i<36;i++) s.player.getInventory().setItem(i,destinations[i]);
//$$             s.player.getInventory().setChanged(); s.player.containerMenu.broadcastChanges();
//$$             s.stacks=null; s.token="";
//$$             JsonObject out=new JsonObject(); out.addProperty("kind","take"); out.addProperty("moved",amount);
//$$             out.addProperty("itemId",lease.itemId);
//$$             out.addProperty("refreshRequired",true);
//$$             finish(s,lease.id,p,reply(lease.id,"OK",out),lease.sink);
//$$         } catch (IllegalArgumentException e) { finish(s,lease.id,p,error(lease.id,e.getMessage()),lease.sink); }
//$$         catch (RuntimeException e) {
//$$             LOGGER.warn("Playersort API transfer failed; inspect both inventories before retrying",e);
//$$             s.stacks=null; s.token="";
//$$             finish(s,lease.id,p,error(lease.id,"TRANSFER_FAILED"),lease.sink);
//$$         }
//$$         finally { release(server,s.target); }
//$$         PlayerSortHeadRestock.request(server,s.target,lease.itemId);
//$$     }
//$$
//$$     /** All-or-nothing capacity plan on copies. Neither equipment nor offhand nor world drops are destinations. */
//$$     static boolean planInsertion(ItemStack[] slots, ItemStack from) {
//$$         int left=from.getCount(), max=Math.min(64,from.getMaxStackSize());
//$$         if (max<1) return false;
//$$         for (ItemStack stack:slots) if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack,from)) {
//$$             int n=Math.max(0,Math.min(left,max-stack.getCount())); stack.grow(n); left-=n;
//$$         }
//$$         for (int i=0;i<slots.length && left>0;i++) if (slots[i].isEmpty()) {
//$$             int n=Math.min(left,max); slots[i]=from.copyWithCount(n); left-=n;
//$$         }
//$$         return left==0;
//$$     }
//$$
//$$     private static void release(MinecraftServer server, String target) {
//$$         Lease lease=LEASES.remove(target);
//$$         if (lease!=null && lease.owned) {
//$$             if (server.getPlayerList().getPlayerByName(target)==null)
//$$                 CLOSING.put(target,new DeferredClose(server,System.currentTimeMillis()+120_000));
//$$             else FakePlayerItemSortManager.apiCloseOwned(server,target);
//$$         }
//$$     }
//$$     private static void releaseOwnedRequest(MinecraftServer server, Session s, Object p) {
//$$         Lease lease=LEASES.get(s.target);
//$$         if (lease!=null && lease.session==s && lease.request.equals(p)) release(server,s.target);
//$$     }
//$$     public static void tick(MinecraftServer server) {
//$$         PlayerSortHeadRestock.tick(server);
//$$         long now=System.currentTimeMillis();
//$$         for (String target:new ArrayList<>(CLOSING.keySet())) {
//$$             DeferredClose close=CLOSING.get(target);
//$$             if (close.server!=server) continue;
//$$             if (server.getPlayerList().getPlayerByName(target)!=null) {
//$$                 FakePlayerItemSortManager.apiCloseOwned(server,target); CLOSING.remove(target);
//$$             } else if (now>=close.deadline) CLOSING.remove(target);
//$$         }
//$$         for (Lease lease:new ArrayList<>(LEASES.values())) {
//$$             if (lease.server!=server) continue;
//$$             if (!active(server,lease.session)) { release(server,lease.session.target); continue; }
//$$             if (server.getPlayerList().getPlayerByName(lease.session.target)!=null) complete(lease);
//$$             else if (System.currentTimeMillis()>=lease.deadline) {
//$$                 finish(lease.session,lease.id,lease.request,error(lease.id,"SPAWN_TIMEOUT"),lease.sink);
//$$                 release(server,lease.session.target);
//$$             }
//$$         }
//$$         SESSIONS.entrySet().removeIf(entry -> !entry.getValue().pending && now-entry.getValue().used>SESSION_MS);
//$$     }
//$$     public static void disconnect(ServerPlayer player) {
//$$         GATES.remove(player.getUUID());
//$$         Session s=SESSIONS.remove(player.getUUID());
//$$         if (s!=null) {
//$$             Lease lease=LEASES.get(s.target);
//$$             if (lease!=null && lease.session==s) release(lease.server,s.target);
//$$         }
//$$     }
//$$     public static void clear() {
//$$         PlayerSortHeadRestock.clear();
//$$         for (Lease lease:new ArrayList<>(LEASES.values())) release(lease.server,lease.session.target);
//$$         SESSIONS.clear(); GATES.clear();
//$$         PlayerSortOfflineWithdrawal.clearLocks();
//$$         CLOSING.clear(); globalQuery=0; globalTake=0;
//$$         if (io!=null) io.shutdownNow();
//$$         io=null;
//$$     }
//$$
//$$     private static final class Gate { long last; final AtomicBoolean queued=new AtomicBoolean(); }
//$$     static boolean hasLease(String name) { return LEASES.containsKey(name) || CLOSING.containsKey(name); }
//$$     private static final class Session {
//$$         final ServerPlayer player;
//$$         final Map<Long,Cached> history=new LinkedHashMap<>();
//$$         long lastId=-1, used, lastQuery, lastTake, expires, inflightId=-1;
//$$         Object inflight;
//$$         boolean pending;
//$$         String target="", token="";
//$$         String materialId;
//$$         Search search;
//$$         ItemStack[] stacks;
//$$         Session(ServerPlayer player) { this.player=player; }
//$$     }
//$$     private static final class Search {
//$$         final String itemId;
//$$         final List<String> candidates;
//$$         final long expires;
//$$         final int amount;
//$$         boolean verified;
//$$         String rejection;
//$$         int nextCursor;
//$$         Search(String itemId,int amount,List<String> candidates,long expires) {
//$$             this.itemId=itemId; this.amount=amount; this.candidates=candidates; this.expires=expires;
//$$         }
//$$     }
//$$     private record Cached(Object request, PlayerSortInventoryPayloads.Reply reply) {}
//$$     private record MaterialKey(ItemStack stack) {
//$$         @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
//$$         @Override public boolean equals(Object other) {
//$$             return other instanceof MaterialKey key && ItemStack.isSameItemSameComponents(stack,key.stack);
//$$         }
//$$     }
//$$     private record DeferredClose(MinecraftServer server, long deadline) {}
//$$     private record Lease(MinecraftServer server, Session session, Object request, long id, String itemId, int amount,
//$$                          Consumer<PlayerSortInventoryPayloads.Reply> sink, boolean owned, long deadline) {}
//$$ }
//$$
//#endif
