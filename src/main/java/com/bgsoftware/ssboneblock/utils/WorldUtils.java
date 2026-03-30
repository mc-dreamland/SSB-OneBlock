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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class WorldUtils {

    private static final OneBlockModule module = OneBlockModule.getModule();
    private static final boolean isShulkerBoxSupported = ServerVersion.isAtLeast(ServerVersion.v1_9);
    private static final Map<UUID, CachedOneBlocks> oneBlockCache = new ConcurrentHashMap<>();

    private WorldUtils() {

    }

    public static boolean shouldDropInventory(InventoryHolder inventoryHolder) {
        if (isShulkerBoxSupported && inventoryHolder instanceof org.bukkit.block.ShulkerBox)
            return false;

        return true;
    }

    public static void invalidateOneBlockCache(@Nullable Island island) {
        if (island == null)
            return;

        invalidateOneBlockCache(island.getUniqueId());
    }

    public static void invalidateOneBlockCache(@Nullable UUID islandUUID) {
        if (islandUUID == null)
            return;

        oneBlockCache.remove(islandUUID);
    }

    public static void clearOneBlockCache() {
        oneBlockCache.clear();
    }

    public static void lookupOneBlock(Chunk chunk, BiConsumer<Location, Island> consumer) {
        List<Island> islands = module.getPlugin().getGrid().getIslandsAt(chunk);
        if (islands.size() != 1)
            return;

        Island island = islands.getFirst();
        if (!module.getPhasesHandler().canHaveOneBlock(island))
            return;

        String dimensionKey = normalizeDimensionKey(chunk.getWorld().getEnvironment().name());
        for (CachedOneBlock cachedOneBlock : getCachedOneBlocks(island).getByDimension(dimensionKey)) {
            if (cachedOneBlock.getBlockX() >> 4 != chunk.getX() || cachedOneBlock.getBlockZ() >> 4 != chunk.getZ())
                continue;

            consumer.accept(cachedOneBlock.toLocation(chunk.getWorld()), island);
        }
    }

    public static void lookupOneBlock(Location location, BiConsumer<Location, Island> consumer) {
        Island islandAtLocation = module.getPlugin().getGrid().getIslandAt(location);
        if (islandAtLocation == null || !module.getPhasesHandler().canHaveOneBlock(islandAtLocation))
            return;

        CachedOneBlock cachedOneBlock = getCachedOneBlocks(islandAtLocation)
                .getByBlock(DimensionBlockKey.of(location));
        if (cachedOneBlock != null)
            consumer.accept(cachedOneBlock.toLocation(location.getWorld()), islandAtLocation);
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

        String normalizedKey = normalizeDimensionKey(dimensionKey);
        if (index < 0)
            return null;
        List<CachedOneBlock> cachedBlocks = getCachedOneBlocks(island).getByDimension(normalizedKey);
        if (index >= cachedBlocks.size())
            return null;
        return cachedBlocks.get(index).toLocation(island);
    }

    private static List<Location> getOneBlockLocations(Island island) {
        if (island == null)
            return Collections.emptyList();

        List<Location> locations = new ArrayList<>();
        for (CachedOneBlock cachedOneBlock : getCachedOneBlocks(island).getAll()) {
            Location location = cachedOneBlock.toLocation(island);
            if (location != null)
                locations.add(location);
        }
        return locations;
    }

    public static List<Location> getOneBlockLocations(Island island, String dimensionKey) {
        if (island == null || dimensionKey == null || dimensionKey.isEmpty())
            return Collections.emptyList();

        String normalized = normalizeDimensionKey(dimensionKey);
        World world = resolveWorld(island, normalized);
        if (world == null)
            return Collections.emptyList();

        List<CachedOneBlock> cachedBlocks = getCachedOneBlocks(island).getByDimension(normalized);
        if (cachedBlocks.isEmpty())
            return Collections.emptyList();

        List<Location> locations = new ArrayList<>(cachedBlocks.size());
        for (CachedOneBlock cachedOneBlock : cachedBlocks) {
            locations.add(cachedOneBlock.toLocation(world));
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
        World world = resolveWorld(island, dimensionKey);
        if (world == null)
            return null;

        return new Location(world, position.getX(), position.getY(), position.getZ());
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

    private static CachedOneBlocks getCachedOneBlocks(Island island) {
        return oneBlockCache.computeIfAbsent(island.getUniqueId(), ignored -> buildCachedOneBlocks(island));
    }

    private static CachedOneBlocks buildCachedOneBlocks(Island island) {
        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        IslandPhaseData ensured = ensureDefaultLocation(island, islandPhaseData);
        if (ensured != null)
            islandPhaseData = ensured;

        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = module.getOneBlockUnlocksHandler().getUnlocks(island);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks = islandPhaseData.getApiUnlocks();
        Map<String, List<CachedOneBlock>> byDimension = new HashMap<>();
        Map<DimensionBlockKey, CachedOneBlock> byBlock = new HashMap<>();
        List<CachedOneBlock> all = new ArrayList<>();
        Set<String> dimensionKeys = new HashSet<>();
        dimensionKeys.addAll(unlocks.keySet());
        dimensionKeys.addAll(apiUnlocks.keySet());

        for (String dimensionKey : dimensionKeys) {
            String normalizedKey = normalizeDimensionKey(dimensionKey);
            List<CachedOneBlock> cachedBlocks = new ArrayList<>();
            appendCachedBlocks(cachedBlocks, normalizedKey, unlocks.get(normalizedKey), false);
            appendCachedBlocks(cachedBlocks, normalizedKey, apiUnlocks.get(normalizedKey), true);
            if (cachedBlocks.isEmpty())
                continue;

            List<CachedOneBlock> immutableBlocks = Collections.unmodifiableList(new ArrayList<>(cachedBlocks));
            byDimension.put(normalizedKey, immutableBlocks);
            all.addAll(cachedBlocks);
            for (CachedOneBlock cachedOneBlock : cachedBlocks) {
                byBlock.put(cachedOneBlock.getKey(), cachedOneBlock);
            }
        }

        return new CachedOneBlocks(Collections.unmodifiableMap(byDimension),
                Collections.unmodifiableList(new ArrayList<>(all)),
                Collections.unmodifiableMap(byBlock));
    }

    private static void appendCachedBlocks(List<CachedOneBlock> target, String dimensionKey,
                                           @Nullable List<IslandPhaseData.OneBlockSlotData> slots, boolean visibleOnly) {
        if (slots == null || slots.isEmpty())
            return;

        for (IslandPhaseData.OneBlockSlotData slot : slots) {
            if (visibleOnly && !isVisibleApiSlot(slot))
                continue;
            if (slot == null || slot.getLocation() == null)
                continue;

            target.add(new CachedOneBlock(dimensionKey, slot.getLocation()));
        }
    }

    @Nullable
    private static World resolveWorld(Island island, String dimensionKey) {
        Dimension dimension = getDimensionByKey(dimensionKey);
        if (dimension == null)
            return null;

        Location islandCenter = island.getCenter(dimension);
        return islandCenter == null ? null : islandCenter.getWorld();
    }

    private static String normalizeDimensionKey(String dimensionKey) {
        return dimensionKey.toUpperCase(Locale.ENGLISH);
    }

    private static final class CachedOneBlocks {

        private final Map<String, List<CachedOneBlock>> byDimension;
        private final List<CachedOneBlock> all;
        private final Map<DimensionBlockKey, CachedOneBlock> byBlock;

        private CachedOneBlocks(Map<String, List<CachedOneBlock>> byDimension, List<CachedOneBlock> all,
                                Map<DimensionBlockKey, CachedOneBlock> byBlock) {
            this.byDimension = byDimension;
            this.all = all;
            this.byBlock = byBlock;
        }

        private List<CachedOneBlock> getByDimension(String dimensionKey) {
            return byDimension.getOrDefault(normalizeDimensionKey(dimensionKey), Collections.emptyList());
        }

        private List<CachedOneBlock> getAll() {
            return all;
        }

        @Nullable
        private CachedOneBlock getByBlock(DimensionBlockKey key) {
            return byBlock.get(key);
        }

    }

    private static final class CachedOneBlock {

        private final String dimensionKey;
        private final double x;
        private final double y;
        private final double z;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final DimensionBlockKey key;

        private CachedOneBlock(String dimensionKey, IslandPhaseData.OneBlockLocation location) {
            this.dimensionKey = normalizeDimensionKey(dimensionKey);
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.blockX = (int) Math.floor(location.getX());
            this.blockY = (int) Math.floor(location.getY());
            this.blockZ = (int) Math.floor(location.getZ());
            this.key = new DimensionBlockKey(this.dimensionKey, blockX, blockY, blockZ);
        }

        private int getBlockX() {
            return blockX;
        }

        private int getBlockZ() {
            return blockZ;
        }

        private DimensionBlockKey getKey() {
            return key;
        }

        private Location toLocation(World world) {
            return new Location(world, x, y, z);
        }

        @Nullable
        private Location toLocation(Island island) {
            World world = resolveWorld(island, dimensionKey);
            if (world == null)
                return null;
            return toLocation(world);
        }

    }

    private static final class DimensionBlockKey {

        private final String dimensionKey;
        private final int x;
        private final int y;
        private final int z;

        private DimensionBlockKey(String dimensionKey, int x, int y, int z) {
            this.dimensionKey = normalizeDimensionKey(dimensionKey);
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static DimensionBlockKey of(Location location) {
            return new DimensionBlockKey(getDimensionKey(location), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof DimensionBlockKey that))
                return false;
            return x == that.x && y == that.y && z == that.z && dimensionKey.equals(that.dimensionKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimensionKey, x, y, z);
        }

    }

}
