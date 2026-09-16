//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEnchantedGoldenCarrotMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void carpetFga$restoreKnownRecipes(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) return;
        ClientRecipeBook recipeBook = Minecraft.getInstance().player.getRecipeBook();
        //#if MC < 1.21.3
        for (RecipeCollection collection : recipeBook.getCollections()) {
            collection.updateKnownRecipes(recipeBook);
        }
        //#else
        //$$ // Recipe display collections are rebuilt by the vanilla packet handler
        //#endif
    }
}
//#endif
