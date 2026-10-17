//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.DeepslateStonecuttingRecipes;
//#if MC >= 1.21.1
import carpet.CarpetServer;
import net.minecraft.server.MinecraftServer;
//#endif
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//$$ import net.minecraft.world.item.crafting.StonecutterRecipe;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientboundUpdateRecipesPacket.class)
public abstract class ClientboundUpdateRecipesDeepslateMixin {
    @Shadow
    @Final
    @Mutable
    //#if MC >= 1.21.3
    //$$ private SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes;
    //#else
    private List<?> recipes;
    //#endif

    @Inject(method = "<init>", at = @At("RETURN"))
    private void carpetFga$filterDisabledRecipes(CallbackInfo callback) {
        //#if MC >= 1.21.1
        // A physical client also hosts the integrated server. Only filter outgoing
        // server packets; client decoding must preserve the remote server's rules.
        MinecraftServer server = CarpetServer.minecraft_server;
        if (server == null || !server.isSameThread()) return;
        //#endif
        //#if MC >= 1.21.3
        //$$ stonecutterRecipes = DeepslateStonecuttingRecipes.filter(stonecutterRecipes);
        //#else
        recipes = recipes.stream().filter(recipe -> !DeepslateStonecuttingRecipes.isDisabledFgaRecipe(recipe)).toList();
        //#endif
    }
}
//#endif
