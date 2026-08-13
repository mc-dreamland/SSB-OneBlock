package com.bgsoftware.ssboneblock.data;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.ssboneblock.utils.JsonUtils;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.persistence.PersistentDataContainer;
import com.bgsoftware.superiorskyblock.api.persistence.PersistentDataType;
import com.bgsoftware.superiorskyblock.api.persistence.PersistentDataTypeContext;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SqlDataStore implements DataStore {

    private static final PersistentDataType<IslandPhaseData> LEGACY_PHASE_DATA_TYPE = new PersistentDataType<>(
            IslandPhaseData.class, PhasePersistentDataTypeContext.getInstance());
    private static final String LEGACY_PHASE_DATA_KEY = "oneblock:phase_data";
    private static final String PHASE_DATA_KEY = "oneblock:phase_data_json";

    private final Map<UUID, IslandPhaseData> phaseCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyIslands = ConcurrentHashMap.newKeySet();

    private final OneBlockModule module;

    public SqlDataStore(OneBlockModule module) {
        this.module = module;
    }

    @Override
    public IslandPhaseData getPhaseData(Island island, boolean createNew) {
        UUID islandId = island.getUniqueId();
        IslandPhaseData cached = phaseCache.get(islandId);
        if (cached != null) {
            return ensureCachedDefault(island, cached, createNew);
        }


        PersistentDataContainer persistentDataContainer = island.getPersistentDataContainer();
        IslandPhaseData islandPhaseData = readPhaseData(persistentDataContainer);

        if (islandPhaseData != null) {
            phaseCache.put(islandId, islandPhaseData);
            return ensureCachedDefault(island, islandPhaseData, createNew);
        }

        islandPhaseData = readLegacyPhaseData(persistentDataContainer);
        if (islandPhaseData != null) {
            phaseCache.put(islandId, islandPhaseData);
            dirtyIslands.add(islandId);
            return ensureCachedDefault(island, islandPhaseData, createNew);
        }

        if (!createNew)
            return null;

        islandPhaseData = buildDefaultPhaseData(island);
        phaseCache.put(islandId, islandPhaseData);
        dirtyIslands.add(islandId);
        WorldUtils.invalidateOneBlockCache(islandId);
        return islandPhaseData;
    }

    @Override
    public void setPhaseData(Island island, IslandPhaseData phaseData) {
        UUID islandId = island.getUniqueId();
        phaseCache.put(islandId, phaseData);
        dirtyIslands.add(islandId);
        WorldUtils.invalidateOneBlockCache(islandId);
    }

    @Override
    public void setPhaseData(UUID islandUUID, IslandPhaseData phaseData) {
        Island island;

        SuperiorPlayer matchingPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(islandUUID);
        if (matchingPlayer != null) {
            island = matchingPlayer.getIsland();
        } else {
            island = SuperiorSkyblockAPI.getIslandByUUID(islandUUID);
        }

        if (island != null) {
            setPhaseData(island, phaseData);
        }
    }

    @Override
    public void removeIsland(Island island) {
        UUID islandId = island.getUniqueId();
        phaseCache.remove(islandId);
        dirtyIslands.remove(islandId);
        WorldUtils.invalidateOneBlockCache(islandId);
    }

    @Override
    public void load() {
        phaseCache.clear();
        dirtyIslands.clear();
        WorldUtils.clearOneBlockCache();
    }

    @Override
    public void save() {
        if (dirtyIslands.isEmpty())
            return;
        Runnable flushTask = this::flushDirtyIslands;
        if (Bukkit.isPrimaryThread()) {
            flushTask.run();
        } else {
            Bukkit.getScheduler().runTask(module, flushTask);
        }
    }

    private void flushDirtyIslands() {
        for (UUID islandId : new ArrayList<>(dirtyIslands)) {
            Island island = SuperiorSkyblockAPI.getIslandByUUID(islandId);
            if (island == null)
                continue;
            IslandPhaseData phaseData = phaseCache.get(islandId);
            if (phaseData == null)
                continue;
            PersistentDataContainer persistentDataContainer = island.getPersistentDataContainer();
            persistentDataContainer.put(PHASE_DATA_KEY, PersistentDataType.STRING, serializePhaseData(phaseData));
            dirtyIslands.remove(islandId);
        }
    }

    private static IslandPhaseData readPhaseData(PersistentDataContainer persistentDataContainer) {
        try {
            return deserializePhaseData(persistentDataContainer.get(PHASE_DATA_KEY, PersistentDataType.STRING));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IslandPhaseData readLegacyPhaseData(PersistentDataContainer persistentDataContainer) {
        try {
            IslandPhaseData phaseData = persistentDataContainer.get(LEGACY_PHASE_DATA_KEY, LEGACY_PHASE_DATA_TYPE);
            if (phaseData != null)
                return phaseData;
        } catch (Throwable ignored) {
        }

        try {
            Object rawValue = persistentDataContainer.get(LEGACY_PHASE_DATA_KEY);
            if (rawValue instanceof byte[] bytes)
                return PhasePersistentDataTypeContext.getInstance().deserialize(bytes);
            if (rawValue instanceof String stringValue)
                return deserializePhaseData(stringValue);

            com.google.gson.JsonElement json = JsonUtils.getGson().toJsonTree(rawValue);
            return json != null && json.isJsonObject() ? OneBlockDataCodec.fromJson(json.getAsJsonObject()) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String serializePhaseData(IslandPhaseData islandPhaseData) {
        IslandPhaseData cleaned = OneBlockDataCodec.cleanupExpiredUnlocks(islandPhaseData);
        return JsonUtils.getGson().toJson(OneBlockDataCodec.toJson(cleaned));
    }

    private static IslandPhaseData deserializePhaseData(String json) {
        if (json == null || json.trim().isEmpty())
            return null;
        com.google.gson.JsonObject parsed = JsonUtils.getGson().fromJson(json, com.google.gson.JsonObject.class);
        return OneBlockDataCodec.fromJson(parsed);
    }

    private static final class PhasePersistentDataTypeContext implements PersistentDataTypeContext<IslandPhaseData> {

        private static final PhasePersistentDataTypeContext INSTANCE = new PhasePersistentDataTypeContext();

        public static PhasePersistentDataTypeContext getInstance() {
            return INSTANCE;
        }

        @Override
        public byte[] serialize(IslandPhaseData islandPhaseData) {
            return serializePhaseData(islandPhaseData).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public IslandPhaseData deserialize(byte[] bytes) {
            try {
                String json = new String(bytes, StandardCharsets.UTF_8);
                IslandPhaseData data = deserializePhaseData(json);
                return data == null ? new IslandPhaseData(0, 0, 0) : data;
            } catch (Throwable ignored) {
                ByteArrayDataInput data = ByteStreams.newDataInput(bytes);
                int phaseLevel = data.readInt();
                int phaseBlock = data.readInt();
                int phaseLoop = data.readInt();
                return new IslandPhaseData(phaseLevel, phaseBlock, phaseLoop);
            }
        }

    }

    private IslandPhaseData buildDefaultPhaseData(Island island) {
        java.util.Map<String, java.util.List<IslandPhaseData.OneBlockSlotData>> unlocks = new java.util.HashMap<>();
        IslandPhaseData.OneBlockLocation defaultLocation = WorldUtils.getDefaultOneBlockLocation(island);
        String defaultKey = WorldUtils.getDefaultDimensionKey();
        if (defaultLocation != null && defaultKey != null && !defaultKey.isEmpty()) {
            java.util.List<IslandPhaseData.OneBlockSlotData> list = new java.util.ArrayList<>();
            list.add(new IslandPhaseData.OneBlockSlotData(defaultLocation, null));
            unlocks.put(defaultKey, list);
        }
        return new IslandPhaseData(0, 0, 0, unlocks, java.util.Collections.emptyMap());
    }

    private IslandPhaseData ensureDefaultLocation(Island island, IslandPhaseData phaseData) {
        if (phaseData == null)
            return null;
        String defaultKey = WorldUtils.getDefaultDimensionKey();
        if (defaultKey == null || defaultKey.isEmpty())
            return phaseData;
        java.util.List<IslandPhaseData.OneBlockSlotData> slots = phaseData.getUnlocks()
                .get(defaultKey.toUpperCase());
        boolean needsDefault = slots == null || slots.isEmpty();
        if (!needsDefault)
            return phaseData;

        IslandPhaseData.OneBlockLocation defaultLocation = WorldUtils.getDefaultOneBlockLocation(island);
        if (defaultLocation == null)
            return phaseData;

        return phaseData.withUnlockLocation(defaultKey, 0, defaultLocation, false);
    }

    private IslandPhaseData ensureCachedDefault(Island island, IslandPhaseData phaseData, boolean createNew) {
        if (!createNew)
            return phaseData;
        IslandPhaseData ensured = ensureDefaultLocation(island, phaseData);
        if (ensured != phaseData) {
            phaseCache.put(island.getUniqueId(), ensured);
            dirtyIslands.add(island.getUniqueId());
            WorldUtils.invalidateOneBlockCache(island.getUniqueId());
            return ensured;
        }
        return phaseData;
    }

}
