package carpet.fga;

//#if MC == 1.21.1
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Grants the current recipe set once per player per minute after login. */
public final class RecipeBookAlwaysUnlockedManager {
    private static final long COOLDOWN_TICKS = 20L * 60L;
    private static final Map<UUID, Long> LAST_GRANT_TICK = new HashMap<>();

    private RecipeBookAlwaysUnlockedManager() {
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (!FGASettings.recipeBookAlwaysUnlocked) return;
        if (player.getServer() == null) return;

        long now = player.getServer().getTickCount();
        Long previous = LAST_GRANT_TICK.get(player.getUUID());
        if (previous != null && now - previous < COOLDOWN_TICKS) return;

        LAST_GRANT_TICK.put(player.getUUID(), now);
        player.awardRecipes(player.getServer().getRecipeManager().getRecipes());
    }

    public static void clear() {
        LAST_GRANT_TICK.clear();
    }
}
//#endif
