package fga.possessiontest;

import carpet.fga.PlayerPossessionManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Runs vanilla arrow damage, death and loot generation with a deterministic test loot table. */
final class OwnerLootProbe {
    static void run(ServerPlayer controller, ServerPlayer fake) {
        if (PlayerPossessionManager.start(controller, fake) != null) throw new AssertionError("start loot probe");
        HitArrow first = new HitArrow(controller);
        HitArrow second = new HitArrow(controller);
        if (!PlayerPossessionManager.stopCurrent(controller)) throw new AssertionError("stop loot probe");
        ItemStack oldA = controller.getMainHandItem();
        ItemStack oldB = fake.getMainHandItem();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(controller.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LOOTING), 3);
        try {
            controller.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fake.setItemInHand(InteractionHand.MAIN_HAND, sword);
            first.checkDrop(4);
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            controller.setItemInHand(InteractionHand.MAIN_HAND, sword);
            second.checkDrop(1);
        } finally {
            controller.setItemInHand(InteractionHand.MAIN_HAND, oldA);
            fake.setItemInHand(InteractionHand.MAIN_HAND, oldB);
        }
        System.out.println("FGA_POSSESSION_LOOT_PASS fake_only=4 controller_only=1");
    }

    private static final class HitArrow extends Arrow {
        HitArrow(ServerPlayer shooter) {
            super(shooter.serverLevel(), shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        }

        void checkDrop(int expected) {
            ServerLevel level = (ServerLevel) level();
            var chicken = EntityType.CHICKEN.create(level);
            if (chicken == null) throw new AssertionError("create loot target");
            chicken.setPos(position());
            CompoundTag data = chicken.saveWithoutId(new CompoundTag());
            data.putString("DeathLootTable", "fga_possession_test:owner_probe");
            chicken.load(data);
            chicken.setHealth(1);
            level.addFreshEntity(chicken);
            setDeltaMovement(new Vec3(1, 0, 0));
            onHitEntity(new EntityHitResult(chicken));
            if (chicken.isAlive()) throw new AssertionError("arrow failed to kill loot target");
            var drops = level.getEntitiesOfClass(ItemEntity.class, chicken.getBoundingBox().inflate(2),
                    item -> item.getItem().is(Items.LAPIS_LAZULI));
            int actual = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
            drops.forEach(ItemEntity::discard);
            if (actual != expected) throw new AssertionError("expected " + expected + " lapis drops, got " + actual);
        }
    }
}
