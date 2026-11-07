package carpet.fga.smoke;

import carpet.CarpetSettings;
import carpet.fga.FGASettings;
import carpet.fga.PlayerPossessionManager;
import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.allay.AllayAi;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.projectile.FishingHook;
//#if MC >= 26.2
//$$ import net.minecraft.world.entity.EntityTypes;
//#else
import net.minecraft.world.entity.EntityType;
//#endif
//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.animal.wolf.Wolf;
//#else
import net.minecraft.world.entity.animal.Wolf;
//#endif
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.animal.equine.Horse;
//$$ import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
//$$ import net.minecraft.world.entity.projectile.arrow.Arrow;
//$$ import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
//$$ import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
//#else
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
//#endif
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Opt-in dedicated server probe using real Carpet players and transformed vanilla classes. */
public final class PossessionOwnerProbe implements ModInitializer {
    private int ticks;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks == 1) {
                var commands = server.getCommands();
                commands.performPrefixedCommand(server.createCommandSourceStack(), "setworldspawn 0 -63 0");
                commands.performPrefixedCommand(server.createCommandSourceStack(), "forceload add -32 -32 32 32");
            }
            if (ticks != 40) return;
            try {
                verify(server);
                System.out.println("FGA_OWNER_BEHAVIOR_PASS");
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("FGA_OWNER_BEHAVIOR_FAIL " + failure);
            } finally {
                server.halt(false);
            }
        });
    }

    private static void verify(MinecraftServer server) {
        var level = server.overworld();
        var info = ClientInformation.createDefault();
        var controllerProfile = new GameProfile(UUID.randomUUID(), "OwnerA");
        var fakeProfile = new GameProfile(UUID.randomUUID(), "OwnerB");
        var controller = new ServerPlayer(server, level, controllerProfile, info);
        var fake = EntityPlayerMPFake.respawnFake(server, level, fakeProfile, info);
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), controller,
                new CommonListenerCookie(controllerProfile, 0, info, false));
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), fake,
                new CommonListenerCookie(fakeProfile, 0, info, false));
        CarpetSettings.commandPlayer = "true";
        FGASettings.playerPossession = "onlyfake";
        check(PlayerPossessionManager.start(controller, fake) == null, "start");
        var arrow = new Arrow(level, controller, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        //#if MC >= 26.2
        //$$ var pearl = new ThrownEnderpearl(EntityTypes.ENDER_PEARL, level);
        //$$ var pet = new Wolf(EntityTypes.WOLF, level);
        //$$ var horse = new Horse(EntityTypes.HORSE, level);
        //$$ var fireball = new LargeFireball(EntityTypes.FIREBALL, level);
        //$$ var bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
        //$$ var allay = new Allay(EntityTypes.ALLAY, level);
        //$$ var interaction = new Interaction(EntityTypes.INTERACTION, level);
        //$$ var cloud = new AreaEffectCloud(EntityTypes.AREA_EFFECT_CLOUD, level);
        //#else
        var pearl = new ThrownEnderpearl(EntityType.ENDER_PEARL, level);
        var pet = new Wolf(EntityType.WOLF, level);
        var horse = new Horse(EntityType.HORSE, level);
        var fireball = new LargeFireball(EntityType.FIREBALL, level);
        var bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        var allay = new Allay(EntityType.ALLAY, level);
        var interaction = new Interaction(EntityType.INTERACTION, level);
        var cloud = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        //#endif
        pearl.setOwner(controller);
        pet.tame(controller);
        pet.setInLove(controller);
        cloud.setOwner(controller);
        var tnt = new PrimedTnt(level, 0, 0, 0, controller);
        var item = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.STONE));
        item.setThrower(controller);
        var melee = controller.damageSources().playerAttack(controller);
        //#if MC == 1.21.3
        //$$ var hook = new FishingHook(controller, level, 0, 0, new ItemStack(Items.FISHING_ROD));
        //#else
        var hook = new FishingHook(controller, level, 0, 0);
        //#endif
        horse.tameWithName(controller);
        controller.attack(fireball);
        bolt.setCause(controller);
        allay.setPos(controller.position());
        controller.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        //#if MC >= 26.1
        //$$ allay.interact(controller, InteractionHand.MAIN_HAND, allay.position());
        //#else
        allay.interact(controller, InteractionHand.MAIN_HAND);
        //#endif
        var trident = new ThrownTrident(level, controller, new ItemStack(Items.TRIDENT));
        interaction.skipAttackInteraction(controller);
        //#if MC >= 1.21.3
        //$$ pet.hurtServer(level, melee, 1);
        //#else
        pet.hurt(melee, 1);
        //#endif
        var entities = List.<TraceableEntity>of(arrow, pearl, trident, hook, fireball, tnt, cloud, item);
        for (var entity : entities) check(entity.getOwner() == controller, "active " + entity.getClass());
        check(bolt.getCause() == controller && AllayAi.getLikedPlayer(allay).orElse(null) == controller, "active lightning and allay");
        check(pet.isOwnedBy(controller) && horse.getOwner() == controller && melee.getEntity() == controller, "active pet and damage");
        check(interaction.getLastAttacker() == controller && pet.getKillCredit() == controller, "active performer");
        check(controller.fishing == hook && fake.fishing == null, "active rod");
        check(pet.getLoveCause() == controller, "active breeding credit");
        //#if MC >= 1.21.3
        //$$ check(fake.getEnderPearls().contains(pearl) && !controller.getEnderPearls().contains(pearl),
        //$$         "pearl belongs to fake playerdata during possession");
        //#endif
        check(PlayerPossessionManager.stopCurrent(controller), "release");
        for (var entity : entities) check(entity.getOwner() == fake, "released " + entity.getClass());
        check(bolt.getCause() == fake && AllayAi.getLikedPlayer(allay).orElse(null) == fake, "released lightning and allay");
        check(pet.isOwnedBy(fake) && horse.getOwner() == fake && melee.getEntity() == fake, "released pet and damage");
        check(interaction.getLastAttacker() == fake && pet.getKillCredit() == fake, "released performer");
        check(fake.fishing == hook && controller.fishing == null, "released rod");
        check(pet.getLoveCause() == fake, "released breeding credit");
        //#if MC >= 1.21.3
        //$$ var looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        //#else
        var looting = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.LOOTING);
        //#endif
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(looting, 3);
        var source = controller.damageSources().arrow(arrow, arrow.getOwner());
        fake.setItemInHand(InteractionHand.MAIN_HAND, sword);
        check(EnchantmentHelper.getEnchantmentLevel(looting, (ServerPlayer) source.getEntity()) == 3, "B holds Looting III");
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        controller.setItemInHand(InteractionHand.MAIN_HAND, sword);
        check(EnchantmentHelper.getEnchantmentLevel(looting, (ServerPlayer) source.getEntity()) == 0, "A holds Looting III");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
