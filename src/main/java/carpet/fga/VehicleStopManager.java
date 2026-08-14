package carpet.fga;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class VehicleStopManager {
    private static String lastAction = "none";

    private VehicleStopManager() {
    }

    public static void driverDismounted(Entity vehicle, ServerPlayer driver) {
        if (FGACompat.isClientSide(FGACompat.level(vehicle))) return;
        if (vehicle instanceof AbstractMinecart minecart) {
            if (!enabled(driver, Kind.MINECART)) {
                lastAction = "minecart:disabled";
                return;
            }
            stopMinecart(minecart);
        } else if (vehicle instanceof Boat boat) {
            if (!enabled(driver, Kind.BOAT)) {
                lastAction = "boat:disabled";
                return;
            }
            if (hasOtherPlayer(boat)) {
                lastAction = "boat:occupied:motionPreserved=true";
                return;
            }
            recordStop("boat", List.of(boat));
        }
    }

    public static String lastAction() {
        return lastAction;
    }

    public static void clear() {
        lastAction = "none";
    }

    public static boolean enabled(ServerPlayer player, Kind kind) {
        return switch (FGASettings.vehicleStopOnDismount.toLowerCase(java.util.Locale.ROOT)) {
            case "minecart" -> kind == Kind.MINECART;
            case "boat" -> kind == Kind.BOAT;
            case "all" -> true;
            case "custom" -> {
                VehicleStopConfig.Entry preference = VehicleStopConfig.preference(player.getUUID());
                yield kind == Kind.MINECART ? preference.minecart() : preference.boat();
            }
            default -> false;
        };
    }

    private static void stopMinecart(AbstractMinecart minecart) {
        if (!(minecart instanceof Minecart normal)) {
            if (hasOtherPlayer(minecart)) {
                lastAction = "minecart:occupied:motionPreserved=true";
            } else {
                recordStop("minecart", List.of(minecart));
            }
            return;
        }

        //#if MC == 1.21.1
        List<Minecart> train = MinecartFeatureManager.loadedTrain(normal);
        if (train.stream().anyMatch(VehicleStopManager::hasOtherPlayer)) {
            MinecartFeatureManager.cancelBoost(normal);
            lastAction = "minecart:occupied:vehicles=" + train.size() + ":motionPreserved=true";
            return;
        }
        for (Minecart cart : train) {
            MinecartFeatureManager.cancelBoost(cart);
        }
        //#else
        //$$ List<Minecart> train = List.of(normal);
        //$$ if (hasOtherPlayer(normal)) {
        //$$     lastAction = "minecart:occupied:vehicles=1:motionPreserved=true";
        //$$     return;
        //$$ }
        //#endif
        recordStop("minecart", train);
    }

    private static boolean hasOtherPlayer(Entity vehicle) {
        return vehicle.getPassengers().stream().anyMatch(passenger -> passenger instanceof Player);
    }

    private static void recordStop(String kind, List<? extends Entity> vehicles) {
        double[] vertical = vehicles.stream().mapToDouble(vehicle -> vehicle.getDeltaMovement().y).toArray();
        boolean hadHorizontalMotion = vehicles.stream().anyMatch(vehicle -> {
            Vec3 movement = vehicle.getDeltaMovement();
            return movement.x != 0.0D || movement.z != 0.0D;
        });
        for (Entity vehicle : vehicles) stopHorizontal(vehicle);
        boolean verticalPreserved = true;
        boolean horizontalStopped = true;
        for (int index = 0; index < vehicles.size(); index++) {
            Vec3 movement = vehicles.get(index).getDeltaMovement();
            verticalPreserved &= Double.compare(vertical[index], movement.y) == 0;
            horizontalStopped &= movement.x == 0.0D && movement.z == 0.0D;
        }
        lastAction = kind + ":stopped:vehicles=" + vehicles.size()
                + ":hadHorizontalMotion=" + hadHorizontalMotion
                + ":horizontalStopped=" + horizontalStopped
                + ":verticalPreserved=" + verticalPreserved;
    }

    private static void stopHorizontal(Entity vehicle) {
        Vec3 movement = vehicle.getDeltaMovement();
        vehicle.setDeltaMovement(0.0D, movement.y, 0.0D);
        vehicle.hasImpulse = true;
        if (FGACompat.level(vehicle) instanceof ServerLevel level) {
            level.getChunkSource().broadcastAndSend(vehicle, new ClientboundSetEntityMotionPacket(vehicle));
        }
    }

    public enum Kind {
        MINECART,
        BOAT
    }
}
