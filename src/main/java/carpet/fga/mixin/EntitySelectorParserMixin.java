package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 放宽命令实体参数直接解析玩家名时使用的原版 16 字符限制。
 */
@Mixin(EntitySelectorParser.class)
public abstract class EntitySelectorParserMixin {
    @ModifyConstant(method = "parseNameOrUUID", constant = @Constant(intValue = 16))
    private int increasePlayerNameLimit(int maxLength) {
        int configured = FGASettings.fakePlayerNameLength;
        return configured > 16 ? Math.min(128, configured) : maxLength;
    }
}
