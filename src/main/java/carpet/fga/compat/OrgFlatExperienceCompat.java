package carpet.fga.compat;

import carpet.fga.FlatExperienceMath;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;

public final class OrgFlatExperienceCompat {
    private static final SimpleCommandExceptionType RECIPIENT_OVERFLOW = new SimpleCommandExceptionType(
            Component.translatable("carpet.fga.experience.transfer_overflow"));

    private OrgFlatExperienceCompat() {}

    public static long total(ServerPlayer player, FlatExperienceMath.Mode mode) {
        return FlatExperienceMath.total(player.experienceLevel, player.experienceProgress, mode);
    }

    public static BigInteger transfer(ServerPlayer from, ServerPlayer to, BigInteger amount,
                                      FlatExperienceMath.Mode mode) throws CommandSyntaxException {
        long available = total(from, mode);
        if (amount.signum() < 0 || amount.compareTo(BigInteger.valueOf(available)) > 0) {
            throw new IllegalArgumentException("Experience transfer outside available points");
        }
        long count = amount.longValueExact();
        if (from == to) return amount;
        // Validate both final states before touching either player.
        if (count > FlatExperienceMath.maximum(mode) - total(to, mode)) {
            throw RECIPIENT_OVERFLOW.create();
        }
        // Each award uses FlatExperienceAwardMixin's bounded integer arithmetic.
        while (count > 0) {
            int chunk = (int) Math.min(count, Integer.MAX_VALUE);
            from.giveExperiencePoints(-chunk);
            to.giveExperiencePoints(chunk);
            count -= chunk;
        }
        return amount;
    }
}
