//#if MC >= 1.16.5 && MC < 1.20.2
//$$ package carpet.fga.mixin;
//$$
//$$ import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
//$$ import net.minecraft.resources.ResourceLocation;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//$$
//$$ @Mixin(ServerboundCustomPayloadPacket.class)
//$$ public interface LegacyCustomPayloadAccessor {
//$$     @Accessor("identifier")
//$$     ResourceLocation carpetFga$getIdentifier();
//$$ }
//#endif
