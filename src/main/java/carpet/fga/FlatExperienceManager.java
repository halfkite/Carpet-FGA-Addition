package carpet.fga;

import net.minecraft.world.entity.player.Player;

public final class FlatExperienceManager {
    private FlatExperienceManager() {}

    public static FlatExperienceMath.Mode mode() {
        if (FGASettings.usesExperienceLevelCost0To1()) return FlatExperienceMath.Mode.ALL;
        if (FGASettings.usesExperienceLevelCost29To30()) return FlatExperienceMath.Mode.AFTER_THIRTY;
        return null;
    }

    public static boolean award(Player player, int amount) {
        FlatExperienceMath.Mode mode = mode();
        if (mode == null) return false;
        long before = FlatExperienceMath.total(player.experienceLevel, player.experienceProgress, mode);
        long after = before + amount;
        FlatExperienceMath.State state = FlatExperienceMath.resolve(after, mode);
        player.increaseScore(amount);
        player.totalExperience = after < 0 ? 0
                : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) player.totalExperience + amount));
        int oldLevel = player.experienceLevel;
        // Keep the first vanilla five-level sound/cooldown boundary when skipping levels.
        long soundLevel = ((long) oldLevel / 5 + 1) * 5;
        if (state.level() > oldLevel && soundLevel <= state.level()) {
            player.giveExperienceLevels((int) soundLevel - oldLevel);
        }
        player.giveExperienceLevels(state.level() - player.experienceLevel);
        player.experienceProgress = FlatExperienceMath.progress(state, mode);
        return true;
    }
}
