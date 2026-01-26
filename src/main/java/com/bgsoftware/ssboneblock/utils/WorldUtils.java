package com.bgsoftware.ssboneblock.utils;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.api.OneBlockSlot;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.InventoryHolder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public class WorldUtils {

    private static final OneBlockModule module = OneBlockModule.getModule();
    private static final boolean isShulkerBoxSupported = ServerVersion.isAtLeast(ServerVersion.v1_9);

    private WorldUtils() {

    }

    public static boolean shouldDropInventory(InventoryHolder inventoryHolder) {
        if (isShulkerBoxSupported && inventoryHolder instanceof org.bukkit.block.ShulkerBox)
            return false;

        return true;
    }

    public static void lookupOneBlock(Chunk chunk, BiConsumer<Location, Island> consumer) {
        List<Island> islands = module.getPlugin().getGrid().getIslandsAt(chunk);
        if (islands.size() != 1)
            return;

        lookupOneBlockWithIsland(islands.get(0), (oneBlockLocation, island) -> {
            if (oneBlockLocation.getBlockX() >> 4 != chunk.getX() || oneBlockLocation.getBlockZ() >> 4 != chunk.getZ())
                consumer.accept(oneBlockLocation, island);
        });
    }

    public static void lookupOneBlock(Location location, BiConsumer<Location, Island> consumer) {
        Island islandAtLocation = module.getPlugin().getGrid().getIslandAt(location);
        lookupOneBlockWithIsland(islandAtLocation, (oneBlockLocation, island) -> {
            if (oneBlockLocation.equals(location))
                consumer.accept(oneBlockLocation, island);
        });
    }

    public static void lookupOneBlockInIsland(Location location, BiConsumer<Location, Island> consumer) {
        Island islandAtLocation = module.getPlugin().getGrid().getIslandAt(location);
        lookupOneBlockWithIsland(islandAtLocation, consumer);
    }

    public static Location getOneBlock(Island island) {
        return getOneBlock(island, getDefaultDimensionKey(), 0);
    }

    private static void lookupOneBlockWithIsland(@Nullable Island island,
                                                 BiConsumer<Location, Island> consumer) {
        if (island == null || !module.getPhasesHandler().canHaveOneBlock(island))
            return;

        for (Location oneBlockLocation : getOneBlockLocations(island)) {
            consumer.accept(oneBlockLocation, island);
        }
    }

    public static String buildOneBlockKey(String dimensionKey, int id) {
        return dimensionKey.toUpperCase(Locale.ENGLISH) + "-" + id;
    }

    public static String getDimensionKey(Location location) {
        World world = location.getWorld();
        return world == null ? "" : world.getEnvironment().name();
    }

    public static String getDefaultDimensionKey() {
        Dimension dimension = module.getPlugin().getSettings().getWorlds().getDefaultWorldDimension();
        return dimension == null ? "" : dimension.getEnvironment().name();
    }

    @Nullable
    public static Location getOneBlock(Island island, String dimensionKey, int id) {
        if (island == null)
            return null;

        String key = buildOneBlockKey(dimensionKey, id);
        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, false);
        if (islandPhaseData != null) {
            IslandPhaseData.OneBlockLocation stored = islandPhaseData.getOneBlockLocations().get(key);
            if (stored != null) {
                return toLocation(island, dimensionKey, stored);
            }
        }

        if (id == 0 && key.equalsIgnoreCase(buildOneBlockKey(getDefaultDimensionKey(), 0))) {
            Location islandCenter = island.getCenter(module.getPlugin().getSettings().getWorlds().getDefaultWorldDimension());
            if (islandCenter == null)
                return null;
            return module.getSettings().blockOffset.applyToLocation(islandCenter.subtract(0.5, 0, 0.5));
        }

        return null;
    }

    private static List<Location> getOneBlockLocations(Island island) {
        if (island == null)
            return Collections.emptyList();

        List<Location> locations = new ArrayList<>();
        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, false);
        String defaultKey = buildOneBlockKey(getDefaultDimensionKey(), 0);

        IslandPhaseData.OneBlockLocation defaultLocation = null;
        if (islandPhaseData != null) {
            defaultLocation = islandPhaseData.getOneBlockLocations().get(defaultKey);
        }

        Location resolvedDefault = defaultLocation == null ? getOneBlock(island, getDefaultDimensionKey(), 0) :
                toLocation(island, getDefaultDimensionKey(), defaultLocation);
        if (resolvedDefault != null) {
            locations.add(resolvedDefault);
        }

        if (islandPhaseData == null || islandPhaseData.getOneBlockLocations().isEmpty())
            return locations;

        for (java.util.Map.Entry<String, IslandPhaseData.OneBlockLocation> entry : islandPhaseData.getOneBlockLocations().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(defaultKey))
                continue;

            OneBlockSlot slot = OneBlockSlot.fromKey(entry.getKey());
            if (slot == null)
                continue;

            if (!module.getOneBlockUnlocksHandler().isUnlocked(island, slot))
                continue;

            Location location = toLocation(island, slot.getDimension(), entry.getValue());
            if (location != null)
                locations.add(location);
        }

        return locations;
    }

    @Nullable
    private static Location toLocation(Island island, String dimensionKey, IslandPhaseData.OneBlockLocation position) {
        Dimension dimension = getDimensionByKey(dimensionKey);
        if (dimension == null)
            return null;

        Location islandCenter = island.getCenter(dimension);
        if (islandCenter == null || islandCenter.getWorld() == null)
            return null;

        return new Location(islandCenter.getWorld(), position.getX(), position.getY(), position.getZ());
    }

    @Nullable
    private static Dimension getDimensionByKey(String dimensionKey) {
        if (dimensionKey == null || dimensionKey.isEmpty())
            return null;

        for (Dimension dimension : Dimension.values()) {
            if (dimension.getEnvironment().name().equalsIgnoreCase(dimensionKey) ||
                    dimension.getName().equalsIgnoreCase(dimensionKey)) {
                return dimension;
            }
        }

        return Dimension.getByName(dimensionKey);
    }

}
