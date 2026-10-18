//#if MC >= 1.21.4 && MC <= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//$$ import java.util.Set;
//$$
//$$ @Mixin(value = EntityPlayerMPFake.class, remap = false)
//$$ public interface CarpetFakePlayerSpawningAccessor {
//$$     @Accessor(value = "spawning", remap = false)
//$$     static Set<String> fga$spawningPlayers() {
//$$         throw new AssertionError("Mixin accessor was not applied");
//$$     }
//$$ }
//#endif
