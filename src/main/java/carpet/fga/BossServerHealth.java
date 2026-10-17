package carpet.fga;
//#if MC >= 26.3
//$$ import java.util.UUID;
//$$ import carpet.fga.mixin.WitherBossEventAccessor;
//$$ import carpet.fga.mixin.DragonFightBossEventAccessor;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.entity.boss.wither.WitherBoss;
//$$ import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
//$$ public final class BossServerHealth {
//$$     private BossServerHealth() {}
//$$     public static HalfMasaSupportPayloads.HealthResult find(ServerPlayer player, UUID bossId) {
//$$         if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Boss health requires server thread");
//$$         for (var entity : player.level().getAllEntities())
//$$             if (entity instanceof WitherBoss wither) {
//$$                 var event = ((WitherBossEventAccessor)wither).fga$bossEvent();
//$$                 if (event.getId().equals(bossId) && event.getPlayers().contains(player))
//$$                     return new HalfMasaSupportPayloads.HealthResult(bossId, wither.getHealth(), wither.getMaxHealth());
//$$             }
//$$         var fight = player.level().getDragonFight();
//$$         if (fight != null) {
//$$             var access = (DragonFightBossEventAccessor)fight;
//$$             if (access.fga$bossEvent().getId().equals(bossId) && access.fga$bossEvent().getPlayers().contains(player))
//$$                 for (var dragon : player.level().getDragons())
//$$                     if (dragon.getUUID().equals(access.fga$dragonUUID()))
//$$                         return new HalfMasaSupportPayloads.HealthResult(bossId, dragon.getHealth(), dragon.getMaxHealth());
//$$         }
//$$         return new HalfMasaSupportPayloads.HealthResult(bossId, 0, 0);
//$$     }
//$$ }
//#endif
