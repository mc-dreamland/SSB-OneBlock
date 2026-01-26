package com.bgsoftware.ssboneblock.data;

import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.ssboneblock.utils.JsonUtils;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.persistence.PersistentDataType;
import com.bgsoftware.superiorskyblock.api.persistence.PersistentDataTypeContext;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SqlDataStore implements DataStore {

    private static final PersistentDataType<IslandPhaseData> PHASE_DATA_TYPE = new PersistentDataType<>(
            IslandPhaseData.class, PhasePersistentDataTypeContext.getInstance());
    private static final String PHASE_DATA_KEY = "oneblock:phase_data";

    @Override
    public IslandPhaseData getPhaseData(Island island, boolean createNew) {
        IslandPhaseData islandPhaseData = island.getPersistentDataContainer().get(PHASE_DATA_KEY, PHASE_DATA_TYPE);

        if (islandPhaseData != null || !createNew) {
            if (createNew) {
                IslandPhaseData ensured = ensureDefaultLocation(island, islandPhaseData);
                if (ensured != islandPhaseData) {
                    setPhaseData(island, ensured);
                    return ensured;
                }
            }
            return islandPhaseData;
        }

        islandPhaseData = buildDefaultPhaseData(island);
        setPhaseData(island, islandPhaseData);
        return islandPhaseData;
    }

    @Override
    public void setPhaseData(Island island, IslandPhaseData phaseData) {
        island.getPersistentDataContainer().put(PHASE_DATA_KEY, PHASE_DATA_TYPE, phaseData);
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

        if (island != null)
            setPhaseData(island, phaseData);
    }

    @Override
    public void removeIsland(Island island) {
        // Do nothing - data is handled by SSB.
    }

    @Override
    public void load() {
        // Do nothing - data is handled by SSB.
    }

    @Override
    public void save() {
        // Do nothing - data is handled by SSB.
    }

    private static final class PhasePersistentDataTypeContext implements PersistentDataTypeContext<IslandPhaseData> {

        private static final PhasePersistentDataTypeContext INSTANCE = new PhasePersistentDataTypeContext();

        public static PhasePersistentDataTypeContext getInstance() {
            return INSTANCE;
        }

        @Override
        public byte[] serialize(IslandPhaseData islandPhaseData) {
            IslandPhaseData cleaned = OneBlockDataCodec.cleanupExpiredUnlocks(islandPhaseData);
            String json = JsonUtils.getGson().toJson(OneBlockDataCodec.toJson(cleaned));
            return json.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public IslandPhaseData deserialize(byte[] bytes) {
            try {
                String json = new String(bytes, StandardCharsets.UTF_8);
                com.google.gson.JsonObject parsed = JsonUtils.getGson().fromJson(json, com.google.gson.JsonObject.class);
                IslandPhaseData data = OneBlockDataCodec.fromJson(parsed);
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

}
