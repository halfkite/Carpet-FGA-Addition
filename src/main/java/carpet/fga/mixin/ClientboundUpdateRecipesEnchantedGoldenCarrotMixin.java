//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.EnchantedGoldenCarrotManager;
import carpet.CarpetServer;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientboundUpdateRecipesPacket.class)
public abstract class ClientboundUpdateRecipesEnchantedGoldenCarrotMixin {
    @Shadow
    @Final
    @Mutable
    private List<RecipeHolder<?>> recipes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void carpetFga$filterEnchantedGoldenCarrotRecipes(CallbackInfo ci) {
        // Dedicated and integrated servers share this packet class with the client.
        // Filter only while the packet is built on the server thread; a client-side
        // decode must retain the recipe list sent by the server.
        MinecraftServer server = CarpetServer.minecraft_server;
        if (server == null || !server.isSameThread()) return;
        recipes = recipes.stream()
                .filter(recipe -> !EnchantedGoldenCarrotManager.isDisabledRecipe(recipe))
                .toList();
    }
}
//#endif
