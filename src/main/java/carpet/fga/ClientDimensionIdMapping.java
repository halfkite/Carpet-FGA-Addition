//#if MC == 1.20.1 || MC >= 1.21.1
package carpet.fga;

//#if MC == 1.20.1
//$$ import carpet.fga.mixin.ClientboundRespawnPacketAccessor;
//#endif

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
//#if MC >= 1.20.2
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
//#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ClientDimensionIdMapping {
    public static final String DEFAULT_VALUE = "[overworld,the_nether,the_end]";

    private static volatile Mapping current = Mapping.identity();

    private ClientDimensionIdMapping() {
    }

    public static void validateAndApply(String value, CommandSourceStack source) {
        if (value.length() < 3 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException("format must be [overworld,the_nether,the_end]");
        }

        String[] entries = value.substring(1, value.length() - 1).split(",", -1);
        if (entries.length != 3) {
            throw new IllegalArgumentException("clientDimensionIds requires exactly three dimension ids");
        }

        ResourceLocation[] ids = new ResourceLocation[3];
        Set<ResourceLocation> unique = new LinkedHashSet<>();
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index].trim();
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (entry.isEmpty() || id == null) {
                throw new IllegalArgumentException("invalid dimension id: " + entry);
            }
            if (!unique.add(id)) {
                throw new IllegalArgumentException("duplicate dimension id: " + id);
            }
            ids[index] = id;
        }

        Mapping parsed = new Mapping(levelKey(ids[0]), levelKey(ids[1]), levelKey(ids[2]));
        if (source != null) {
            Set<ResourceKey<Level>> serverLevels = source.getServer().levelKeys();
            for (ResourceKey<Level> alias : parsed.aliases()) {
                if (serverLevels.contains(alias) && !isVanillaDimension(alias)) {
                    throw new IllegalArgumentException("client dimension id conflicts with a server dimension: "
                            +
                            //#if MC >= 1.21.11
                            //$$ alias.identifier());
                            //#else
                            alias.location());
                            //#endif
                }
            }
        }
        current = parsed;
    }

    public static Packet<?> remap(Packet<?> packet) {
        Mapping mapping = current;
        if (mapping.isIdentity()) {
            return packet;
        }
        if (packet instanceof ClientboundLoginPacket login) {
            Set<ResourceKey<Level>> levels = new LinkedHashSet<>();
            for (ResourceKey<Level> level : login.levels()) {
                levels.add(mapping.remap(level));
            }
            return new ClientboundLoginPacket(
                    //#if MC >= 1.20.2
                    login.playerId(), login.hardcore(), Set.copyOf(levels), login.maxPlayers(),
                    login.chunkRadius(), login.simulationDistance(), login.reducedDebugInfo(),
                    login.showDeathScreen(), login.doLimitedCrafting(),
                    remapSpawnInfo(login.commonPlayerSpawnInfo(), mapping),
                    //#if MC >= 26.2
                    //$$ login.onlineMode(),
                    //#endif
                    login.enforcesSecureChat());
                    //#else
                    //$$ login.playerId(), login.hardcore(), login.gameType(), login.previousGameType(),
                    //$$ Set.copyOf(levels), login.registryHolder(), login.dimensionType(),
                    //$$ mapping.remap(login.dimension()), login.seed(), login.maxPlayers(), login.chunkRadius(),
                    //$$ login.simulationDistance(), login.reducedDebugInfo(), login.showDeathScreen(),
                    //$$ login.isDebug(), login.isFlat(), remapDeathLocation(login.lastDeathLocation(), mapping),
                    //$$ login.portalCooldown());
                    //#endif
        }
        if (packet instanceof ClientboundRespawnPacket respawn) {
            //#if MC >= 1.20.2
            return new ClientboundRespawnPacket(
                    remapSpawnInfo(respawn.commonPlayerSpawnInfo(), mapping), respawn.dataToKeep());
            //#else
            //$$ return new ClientboundRespawnPacket(respawn.getDimensionType(), mapping.remap(respawn.getDimension()),
            //$$         respawn.getSeed(), respawn.getPlayerGameType(), respawn.getPreviousPlayerGameType(),
            //$$         respawn.isDebug(), respawn.isFlat(),
            //$$         ((ClientboundRespawnPacketAccessor) respawn).carpetFga$getDataToKeep(),
            //$$         remapDeathLocation(respawn.getLastDeathLocation(), mapping), respawn.getPortalCooldown());
            //#endif
        }
        return packet;
    }

    private static Optional<GlobalPos> remapDeathLocation(Optional<GlobalPos> location, Mapping mapping) {
        return location.map(position -> GlobalPos.of(mapping.remap(position.dimension()), position.pos()));
    }

    //#if MC >= 1.20.2
    private static CommonPlayerSpawnInfo remapSpawnInfo(CommonPlayerSpawnInfo info, Mapping mapping) {
        ResourceKey<Level> dimension = mapping.remap(info.dimension());
        Optional<GlobalPos> deathLocation = remapDeathLocation(info.lastDeathLocation(), mapping);
        return new CommonPlayerSpawnInfo(
                info.dimensionType(), dimension, info.seed(), info.gameType(), info.previousGameType(),
                info.isDebug(), info.isFlat(), deathLocation, info.portalCooldown()
                //#if MC >= 1.21.2
                //$$ , info.seaLevel()
                //#endif
        );
    }
    //#endif

    private static ResourceKey<Level> levelKey(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    private static boolean isVanillaDimension(ResourceKey<Level> key) {
        return key.equals(Level.OVERWORLD) || key.equals(Level.NETHER) || key.equals(Level.END);
    }

    private record Mapping(ResourceKey<Level> overworld, ResourceKey<Level> nether, ResourceKey<Level> end) {
        private static Mapping identity() {
            return new Mapping(Level.OVERWORLD, Level.NETHER, Level.END);
        }

        private List<ResourceKey<Level>> aliases() {
            return List.of(overworld, nether, end);
        }

        private ResourceKey<Level> remap(ResourceKey<Level> key) {
            if (key.equals(Level.OVERWORLD)) {
                return overworld;
            }
            if (key.equals(Level.NETHER)) {
                return nether;
            }
            if (key.equals(Level.END)) {
                return end;
            }
            return key;
        }

        private boolean isIdentity() {
            return overworld.equals(Level.OVERWORLD)
                    && nether.equals(Level.NETHER)
                    && end.equals(Level.END);
        }
    }
}
//#endif
