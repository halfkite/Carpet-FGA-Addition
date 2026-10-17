package carpet.fga;
//#if MC >= 1.21.1
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Separate from stock withdrawal: only consumes food in the requesting player's live inventory. */
public final class HalfMasaSupportServer {
    private record Cached(HalfMasaSupportPayloads.Eat request, HalfMasaSupportPayloads.EatResult result) {}
    private static final class Session {
        final Map<Long,Cached> outcomes = new LinkedHashMap<>();
        long lastEat = Long.MIN_VALUE;
        long lastRequestId = -1;
        long healthSecond = Long.MIN_VALUE;
        int healthRequests;
    }
    private static final Map<ServerPlayer,Session> SESSIONS = new WeakHashMap<>();
    private static final Map<UUID,AtomicInteger> QUEUED = new ConcurrentHashMap<>();
    private HalfMasaSupportServer() {}
    public static void register() {
//#if MC >= 26.0
//$$         PayloadTypeRegistry.serverboundPlay().register(HalfMasaSupportPayloads.Eat.TYPE,HalfMasaSupportPayloads.Eat.CODEC);
//$$         PayloadTypeRegistry.clientboundPlay().register(HalfMasaSupportPayloads.EatResult.TYPE,HalfMasaSupportPayloads.EatResult.CODEC);
//#else
        PayloadTypeRegistry.playC2S().register(HalfMasaSupportPayloads.Eat.TYPE,HalfMasaSupportPayloads.Eat.CODEC);
        PayloadTypeRegistry.playS2C().register(HalfMasaSupportPayloads.EatResult.TYPE,HalfMasaSupportPayloads.EatResult.CODEC);
//#endif
//#if MC == 26.3
//$$         PayloadTypeRegistry.serverboundPlay().register(HalfMasaSupportPayloads.HealthQuery.TYPE,HalfMasaSupportPayloads.HealthQuery.CODEC);
//$$         PayloadTypeRegistry.clientboundPlay().register(HalfMasaSupportPayloads.HealthResult.TYPE,HalfMasaSupportPayloads.HealthResult.CODEC);
//#endif
        ServerPlayNetworking.registerGlobalReceiver(HalfMasaSupportPayloads.Eat.TYPE,(request,context) -> {
            AtomicInteger queued = QUEUED.computeIfAbsent(context.player().getUUID(),id -> new AtomicInteger());
            if (queued.incrementAndGet() > 32) { queued.decrementAndGet(); return; }
            context.server().execute(() -> {
                try {
                    if (context.server().getPlayerList().getPlayer(context.player().getUUID()) != context.player()) return;
                    var reply = eat(context.player(),request);
                    if (ServerPlayNetworking.canSend(context.player(),HalfMasaSupportPayloads.EatResult.TYPE)) ServerPlayNetworking.send(context.player(),reply);
                } finally { queued.decrementAndGet(); }
            });
        });
//#if MC == 26.3
//$$         ServerPlayNetworking.registerGlobalReceiver(HalfMasaSupportPayloads.HealthQuery.TYPE,(request,context) -> {
//$$             AtomicInteger queued = QUEUED.computeIfAbsent(context.player().getUUID(),id -> new AtomicInteger());
//$$             if (queued.incrementAndGet() > 32) { queued.decrementAndGet(); return; }
//$$             context.server().execute(() -> {
//$$                 try {
//$$                     var player = context.player();
//$$                     if (context.server().getPlayerList().getPlayer(player.getUUID()) != player) return;
//$$                     var session = SESSIONS.computeIfAbsent(player,p -> new Session());
//$$                     long second = player.level().getGameTime() / 20;
//$$                     if (session.healthSecond != second) { session.healthSecond = second; session.healthRequests = 0; }
//$$                     if (++session.healthRequests > 32) return;
//$$                     if (ServerPlayNetworking.canSend(player,HalfMasaSupportPayloads.HealthResult.TYPE))
//$$                         ServerPlayNetworking.send(player,BossServerHealth.find(player,request.bossId()));
//$$                 } finally { queued.decrementAndGet(); }
//$$             });
//$$         });
//#endif
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> {
            QUEUED.remove(handler.player.getUUID()); server.execute(() -> SESSIONS.remove(handler.player));
        });
    }
    public static HalfMasaSupportPayloads.EatResult eat(ServerPlayer player, HalfMasaSupportPayloads.Eat request) {
        if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Silent eat API requires server thread");
        var session = SESSIONS.computeIfAbsent(player,p -> new Session());
        var cached = session.outcomes.get(request.requestId());
        if (cached != null) {
            var old = cached.request;
            boolean same = old.version() == request.version() && old.slot() == request.slot() && old.target() == request.target()
                    && old.pauseCombat() == request.pauseCombat() && ItemStack.matches(old.expected(),request.expected());
            return same ? cached.result : new HalfMasaSupportPayloads.EatResult(request.requestId(),"REQUEST_ID_REUSED");
        }
        String status;
        long tick = player.level().getGameTime();
        if (request.version() != 1 || request.requestId() < 0) status = "INVALID_REQUEST";
        else if (request.requestId() <= session.lastRequestId) status = "EXPIRED_REQUEST";
        else if (!FGASettings.silentAutoEat) status = "DISABLED";
        else if (session.lastEat == tick) status = "RATE_LIMITED";
        else {
            session.lastEat = tick;
            status = SilentFoodTransaction.consume(player,request.slot(),request.expected(),request.target(),request.pauseCombat());
        }
        var result = new HalfMasaSupportPayloads.EatResult(request.requestId(),status);
        session.lastRequestId = Math.max(session.lastRequestId, request.requestId());
        session.outcomes.put(request.requestId(),new Cached(request,result));
        if (session.outcomes.size() > 64) session.outcomes.remove(session.outcomes.keySet().iterator().next());
        return result;
    }
}
//#endif
