package com.bgsoftware.ssboneblock.utils;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.InventoryHolder;

import javax.annotation.Nullable;
import java.util.*;
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
            if (oneBlockLocation.getBlockX() == location.getBlockX()
                    && oneBlockLocation.getBlockY() == location.getBlockY()
                    && oneBlockLocation.getBlockZ() == location.getBlockZ())
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

    public static String getDimensionKey(Location location) {
        World world = location.getWorld();
        return world == null ? "" : world.getEnvironment().name();
    }

    public static String getDefaultDimensionKey() {
        Dimension dimension = module.getPlugin().getSettings().getWorlds().getDefaultWorldDimension();
        return dimension == null ? "" : dimension.getEnvironment().name();
    }

    @Nullable
    public static Location getOneBlock(Island island, String dimensionKey, int index) {
        if (island == null)
            return null;

        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        IslandPhaseData ensured = ensureDefaultLocation(island, islandPhaseData);
        if (ensured != null)
            islandPhaseData = ensured;
        String normalizedKey = dimensionKey.toUpperCase(Locale.ENGLISH);
        List<IslandPhaseData.OneBlockSlotData> unlocks = module.getOneBlockUnlocksHandler().getUnlocks(island)
                .get(normalizedKey);
        List<IslandPhaseData.OneBlockSlotData> apiUnlocks = islandPhaseData.getApiUnlocks()
                .get(normalizedKey);
        int unlockSize = unlocks == null ? 0 : unlocks.size();
        if (index < 0)
            return null;
        if (unlocks != null && index < unlocks.size()) {
            IslandPhaseData.OneBlockSlotData slot = unlocks.get(index);
            if (slot != null && slot.getLocation() != null)
                return toLocation(island, normalizedKey, slot.getLocation());
            return null;
        }
        int apiIndex = index - unlockSize;
        if (apiUnlocks != null && apiIndex >= 0) {
            IslandPhaseData.OneBlockSlotData slot = getVisibleApiSlot(apiUnlocks, apiIndex);
            if (slot != null && slot.getLocation() != null)
                return toLocation(island, normalizedKey, slot.getLocation());
        }

        return null;
    }

    private static List<Location> getOneBlockLocations(Island island) {
        if (island == null)
            return Collections.emptyList();

        List<Location> locations = new ArrayList<>();
        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = module.getOneBlockUnlocksHandler().getUnlocks(island);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks = islandPhaseData.getApiUnlocks();
        Set<String> dimensionKeys = new HashSet<>();
        dimensionKeys.addAll(unlocks.keySet());
        dimensionKeys.addAll(apiUnlocks.keySet());
        for (String dimensionKey : dimensionKeys) {
            List<IslandPhaseData.OneBlockSlotData> unlockList = unlocks.get(dimensionKey);
            if (unlockList != null) {
                for (IslandPhaseData.OneBlockSlotData slot : unlockList) {
                    if (slot != null && slot.getLocation() != null) {
                        Location location = toLocation(island, dimensionKey, slot.getLocation());
                        if (location != null)
                            locations.add(location);
                    }
                }
            }
            List<IslandPhaseData.OneBlockSlotData> apiList = apiUnlocks.get(dimensionKey);
            if (apiList != null) {
                for (IslandPhaseData.OneBlockSlotData slot : apiList) {
                    if (!isVisibleApiSlot(slot))
                        continue;
                    if (slot != null && slot.getLocation() != null) {
                        Location location = toLocation(island, dimensionKey, slot.getLocation());
                        if (location != null)
                            locations.add(location);
                    }
                }
            }
        }

        return locations;
    }

    public static List<Location> getOneBlockLocations(Island island, String dimensionKey) {
        if (island == null || dimensionKey == null || dimensionKey.isEmpty())
            return Collections.emptyList();

        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        ensureDefaultLocation(island, islandPhaseData);

        String normalized = dimensionKey.toUpperCase(Locale.ENGLISH);
        List<Location> locations = new ArrayList<>();
        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = module.getOneBlockUnlocksHandler().getUnlocks(island);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks = islandPhaseData.getApiUnlocks();

        List<IslandPhaseData.OneBlockSlotData> unlockList = unlocks.get(normalized);
        if (unlockList != null) {
            for (IslandPhaseData.OneBlockSlotData slot : unlockList) {
                if (slot != null && slot.getLocation() != null) {
                    Location location = toLocation(island, normalized, slot.getLocation());
                    if (location != null)
                        locations.add(location);
                }
            }
        }

        List<IslandPhaseData.OneBlockSlotData> apiList = apiUnlocks.get(normalized);
        if (apiList != null) {
            for (IslandPhaseData.OneBlockSlotData slot : apiList) {
                if (!isVisibleApiSlot(slot))
                    continue;
                if (slot != null && slot.getLocation() != null) {
                    Location location = toLocation(island, normalized, slot.getLocation());
                    if (location != null)
                        locations.add(location);
                }
            }
        }

        return locations;
    }

    public static IslandPhaseData.OneBlockLocation getDefaultOneBlockLocation(Island island) {
        if (island == null)
            return null;

        Location islandCenter = island.getCenter(module.getPlugin().getSettings().getWorlds().getDefaultWorldDimension());
        if (islandCenter == null)
            return null;

        Location resolved = module.getSettings().blockOffset.applyToLocation(islandCenter.subtract(0.5, 0, 0.5));
        return new IslandPhaseData.OneBlockLocation(
                resolved.getBlockX() + 0.5,
                resolved.getBlockY() + 0.5,
                resolved.getBlockZ() + 0.5);
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

    private static IslandPhaseData ensureDefaultLocation(Island island, IslandPhaseData islandPhaseData) {
        String defaultDimension = getDefaultDimensionKey();
        if (defaultDimension == null || defaultDimension.isEmpty())
            return islandPhaseData;
        List<IslandPhaseData.OneBlockSlotData> slots = islandPhaseData.getUnlocks()
                .get(defaultDimension.toUpperCase(Locale.ENGLISH));
        boolean needsDefault = slots == null || slots.isEmpty();
        if (!needsDefault)
            return islandPhaseData;

        IslandPhaseData.OneBlockLocation defaultLocation = getDefaultOneBlockLocation(island);
        if (defaultLocation == null)
            return islandPhaseData;

        IslandPhaseData updated = islandPhaseData.withUnlockLocation(defaultDimension, 0, defaultLocation, false);
        module.getPhasesHandler().getDataStore().setPhaseData(island, updated);
        return updated;
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

    private static boolean isVisibleApiSlot(@Nullable IslandPhaseData.OneBlockSlotData slot) {
        return slot == null || slot.isActive();
    }

    @Nullable
    private static IslandPhaseData.OneBlockSlotData getVisibleApiSlot(List<IslandPhaseData.OneBlockSlotData> slots, int index) {
        int visibleIndex = 0;
        for (IslandPhaseData.OneBlockSlotData slot : slots) {
            if (!isVisibleApiSlot(slot))
                continue;
            if (visibleIndex == index)
                return slot;
            visibleIndex++;
        }
        return null;
    }

}
