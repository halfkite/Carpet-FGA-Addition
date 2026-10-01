package carpet.fga.smoke;

import carpet.fga.FGASettings;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.math.BigInteger;

/** Isolated-server probe for the optional ORG Mixin target and injected XP curve. */
public final class OrgExperienceMixinProbe implements DedicatedServerModInitializer {
    private static final String TRANSFER_CLASS =
            //#if MC < 26.0
            "org.carpetorgaddition.wheel.ExperienceTransfer"
            //#else
            //#if MC < 26.2
            //$$ "boat.carpetorgaddition.wheel.ExperienceTransfer"
            //#else
            //#if MC < 26.3
            //$$ "boat.carpetorgaddition.wheel.misc.ExperienceTransfer"
            //#else
            //$$ "boat.carpetorgaddition.command.XpTransferCommand$ExperienceTransfer"
            //#endif
            //#endif
            //#endif
            ;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                check(FabricLoader.getInstance().isModLoaded("carpet-org-addition"), "ORG is not loaded");
                Class<?> transferClass = Class.forName(TRANSFER_CLASS, true,
                        Thread.currentThread().getContextClassLoader());
                var method = transferClass.getDeclaredMethod("calculateUpgradeExperience", int.class, int.class);
                method.setAccessible(true);

                FGASettings.experienceLevelCost = "29-30";
                check(BigInteger.valueOf(107).equals(method.invoke(null, 30, 31)),
                        "29-30 injected upgrade cost is not 107");
                FGASettings.experienceLevelCost = "0-1";
                check(BigInteger.valueOf(7).equals(method.invoke(null, 30, 31)),
                        "0-1 injected upgrade cost is not 7");
                FGASettings.experienceLevelCost = "false";
                System.out.println("FGA_ORG_XP_PASS: target=" + TRANSFER_CLASS);
            } catch (Throwable error) {
                System.err.println("FGA_ORG_XP_FAIL: " + error);
                error.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
