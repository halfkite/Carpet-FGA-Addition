package carpet.fga;

//#if MC >= 1.19
import carpet.api.settings.SettingsManager;
//#else
//$$ import carpet.settings.SettingsManager;
//#endif
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerBreedingAnimalization {
    public static final int WILLING_TICKS = 600;
    private static boolean observerRegistered;
    private static volatile boolean tisBreedingCooldownDisabled;

    private VillagerBreedingAnimalization() {
    }

    public static void registerRuleObserver() {
        if (observerRegistered) {
            return;
        }
        observerRegistered = true;
        //#if MC >= 1.19
        SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if (rule.name().equals("breedingCooldownDisabled")) {
                tisBreedingCooldownDisabled = Boolean.TRUE.equals(rule.value());
            }
        });
        //#else
        //$$ SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if (rule.name.equals("breedingCooldownDisabled")) {
        //$$         tisBreedingCooldownDisabled = Boolean.TRUE.equals(rule.get());
        //$$     }
        //$$ });
        //#endif
    }

    public static boolean isEnabled() {
        return !FGASettings.villagerBreedingAnimalization.equals("false");
    }

    public static boolean isOnlyMode() {
        return FGASettings.villagerBreedingAnimalization.equals("only");
    }

    public static boolean tryFeed(Player player, Villager villager, InteractionHand hand) {
        if (!isEnabled()) {
            return false;
        }

        ItemStack stack = player.getItemInHand(hand);
        int growthFoodUnits = growthFoodUnits(stack);
        if (villager.isBaby()) {
            if (growthFoodUnits == 0) {
                return false;
            }
            if (!FGACompat.level(player).isClientSide() && !FGACompat.isCreative(player)) {
                stack.shrink(1);
            }
            for (int i = 0; i < growthFoodUnits; i++) {
                villager.ageUp(
                        //#if MC >= 1.19
                        AgeableMob.getSpeedUpSecondsWhenFeeding(-villager.getAge())
                        //#else
                        //$$ Math.max(1, (int) ((-villager.getAge() / 20) * 0.1F))
                        //#endif
                        , true);
            }
            return true;
        }

        if (!isBreedingAgeEligible(villager)) {
            return false;
        }

        VillagerBreedingAccess access = (VillagerBreedingAccess) villager;
        if (access.carpetFga$getAnimalizedWillingTicks() > 0) {
            return false;
        }

        int required = requiredFoodCount(stack);
        if (required == 0 || stack.getCount() < required) {
            return false;
        }

        if (!FGACompat.level(player).isClientSide()) {
            if (!FGACompat.isCreative(player)) {
                stack.shrink(required);
            }
            if (villager.getAge() > 0) {
                villager.setAge(0);
            }
            access.carpetFga$setAnimalizedWillingTicks(WILLING_TICKS);
            FGACompat.level(villager).broadcastEntityEvent(villager, (byte) 18);
        }
        return true;
    }

    private static int requiredFoodCount(ItemStack stack) {
        if (FGACompat.isItem(stack, Items.BREAD)) {
            return 3;
        }
        if (FGACompat.isItem(stack, Items.CARROT) || FGACompat.isItem(stack, Items.POTATO) || FGACompat.isItem(stack, Items.BEETROOT)) {
            return 12;
        }
        return 0;
    }

    private static int growthFoodUnits(ItemStack stack) {
        if (FGACompat.isItem(stack, Items.BREAD)) {
            return 4;
        }
        if (FGACompat.isItem(stack, Items.CARROT) || FGACompat.isItem(stack, Items.POTATO) || FGACompat.isItem(stack, Items.BEETROOT)) {
            return 1;
        }
        return 0;
    }

    public static boolean isBreedingAgeEligible(Villager villager) {
        int age = villager.getAge();
        return age == 0 || age > 0 && isTisBreedingCooldownDisabled();
    }

    private static boolean isTisBreedingCooldownDisabled() {
        return tisBreedingCooldownDisabled;
    }

}
