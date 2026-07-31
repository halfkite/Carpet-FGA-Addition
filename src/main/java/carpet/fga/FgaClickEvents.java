//#if MC >= 1.19.4
package carpet.fga;

import net.minecraft.network.chat.ClickEvent;

/** Normalizes Minecraft's ClickEvent factory migration. */
public final class FgaClickEvents {
    private FgaClickEvents() {}

    public static ClickEvent runCommand(String command) {
        //#if MC >= 1.21.5
        //$$ return new ClickEvent.RunCommand(command);
        //#else
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //#endif
    }

    public static ClickEvent suggestCommand(String command) {
        //#if MC >= 1.21.5
        //$$ return new ClickEvent.SuggestCommand(command);
        //#else
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        //#endif
    }
}
//#endif
