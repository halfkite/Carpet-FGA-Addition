package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PossessionPlayerAccessor {
    @Accessor("gameProfile")
    @Mutable
    void fga$gameProfile(GameProfile profile);
}
//#endif
