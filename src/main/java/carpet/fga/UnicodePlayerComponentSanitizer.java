//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import com.mojang.authlib.GameProfile;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.network.chat.ComponentContents;
//$$ import net.minecraft.network.chat.HoverEvent;
//$$ import net.minecraft.network.chat.MutableComponent;
//$$ import net.minecraft.network.chat.Style;
//$$ import net.minecraft.network.chat.contents.ObjectContents;
//$$ import net.minecraft.network.chat.contents.objects.ObjectInfo;
//$$ import net.minecraft.network.chat.contents.objects.PlayerSprite;
//$$ import net.minecraft.world.item.component.ResolvableProfile;
//$$
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.util.UUID;
//$$
//$$ /** Rewrites invalid profile names embedded in 26.3 components before packet encoding. */
//$$ public final class UnicodePlayerComponentSanitizer {
//$$     private UnicodePlayerComponentSanitizer() {
//$$     }
//$$
//$$     public static Component sanitize(Component component) {
//$$         ComponentContents contents = component.getContents();
//$$         if (contents instanceof ObjectContents objectContents) {
//$$             ObjectInfo object = objectContents.contents();
//$$             if (object instanceof PlayerSprite playerSprite) {
//$$                 object = sanitize(playerSprite);
//$$             }
//$$             contents = new ObjectContents(object, objectContents.fallback().map(UnicodePlayerComponentSanitizer::sanitize));
//$$         }
//$$
//$$         MutableComponent sanitized = MutableComponent.create(contents)
//$$                 .setStyle(sanitizeStyle(component.getStyle()));
//$$         for (Component sibling : component.getSiblings()) {
//$$             sanitized.append(sanitize(sibling));
//$$         }
//$$         return sanitized;
//$$     }
//$$
//$$     private static PlayerSprite sanitize(PlayerSprite sprite) {
//$$         ResolvableProfile profile = sprite.player();
//$$         GameProfile partialProfile = profile.partialProfile();
//$$         String profileName = profile.name().orElse(partialProfile.name());
//$$         if (isVanillaProfileName(profileName)) {
//$$             return sprite;
//$$         }
//$$
//$$         UUID id = partialProfile.id();
//$$         if (id == null) {
//$$             id = UUID.nameUUIDFromBytes(String.valueOf(profileName).getBytes(StandardCharsets.UTF_8));
//$$         }
//$$         String alias = "fga_" + id.toString().replace("-", "").substring(0, 12);
//$$         GameProfile safeProfile = new GameProfile(id, alias, partialProfile.properties());
//$$         return new PlayerSprite(ResolvableProfile.createResolved(safeProfile), sprite.hat());
//$$     }
//$$
//$$     private static boolean isVanillaProfileName(String name) {
//$$         if (name == null || name.isEmpty() || name.length() > 16) {
//$$             return false;
//$$         }
//$$         for (int i = 0; i < name.length(); i++) {
//$$             char character = name.charAt(i);
//$$             if (!((character >= 'a' && character <= 'z')
//$$                     || (character >= 'A' && character <= 'Z')
//$$                     || (character >= '0' && character <= '9')
//$$                     || character == '_')) {
//$$                 return false;
//$$             }
//$$         }
//$$         return true;
//$$     }
//$$
//$$     private static Style sanitizeStyle(Style style) {
//$$         HoverEvent hover = style.getHoverEvent();
//$$         if (hover instanceof HoverEvent.ShowText showText) {
//$$             return style.withHoverEvent(new HoverEvent.ShowText(sanitize(showText.value())));
//$$         }
//$$         if (hover instanceof HoverEvent.ShowEntity showEntity) {
//$$             HoverEvent.EntityTooltipInfo entity = showEntity.entity();
//$$             return style.withHoverEvent(new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(
//$$                     entity.type,
//$$                     entity.uuid,
//$$                     entity.name.map(UnicodePlayerComponentSanitizer::sanitize))));
//$$         }
//$$         return style;
//$$     }
//$$ }
//#endif
