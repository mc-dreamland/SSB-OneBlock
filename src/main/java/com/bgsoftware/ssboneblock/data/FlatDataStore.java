package com.bgsoftware.ssboneblock.data;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.ssboneblock.utils.JsonUtils;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlatDataStore implements DataStore {

    private final Map<UUID, IslandPhaseData> islandPhaseData = new ConcurrentHashMap<>();

    private final OneBlockModule module;

    public FlatDataStore(OneBlockModule module) {
        this.module = module;
    }

    @Override
    public IslandPhaseData getPhaseData(Island island, boolean createNew) {
        if (!createNew)
            return this.islandPhaseData.get(island.getUniqueId());

        IslandPhaseData phaseData = this.islandPhaseData.computeIfAbsent(island.getUniqueId(), v -> buildDefaultPhaseData(island));
        IslandPhaseData ensured = ensureDefaultUnlock(island, phaseData);
        if (ensured != phaseData) {
            setPhaseData(island, ensured);
            return ensured;
        }
        return phaseData;
    }

    @Override
    public void setPhaseData(Island island, IslandPhaseData phaseData) {
        this.setPhaseData(island.getUniqueId(), phaseData);
    }

    @Override
    public void setPhaseData(UUID islandUUID, IslandPhaseData phaseData) {
        this.islandPhaseData.put(islandUUID, phaseData);
    }

    @Override
    public void removeIsland(Island island) {
        this.islandPhaseData.remove(island.getUniqueId());
    }

    @Override
    public void load() {
        File file = new File(module.getDataStoreFolder(), "database.json");

        convertOldDatabase(file);

        if (file.isDirectory())
            file.delete();

        if (!file.exists())
            return;

        try {
            JsonArray jsonArray = JsonUtils.parseFile(file, JsonArray.class);
            if (jsonArray != null) {
                for (JsonElement islandDataElement : jsonArray) {
                    try {
                        JsonObject islandData = (JsonObject) islandDataElement;
                        UUID islandUUID = UUID.fromString(islandData.get("island").getAsString());
                        IslandPhaseData parsed = OneBlockDataCodec.fromJson(islandData);
                        if (parsed != null) {
                            IslandPhaseData ensured = ensureDefaultUnlock(SuperiorSkyblockAPI.getIslandByUUID(islandUUID), parsed);
                            setPhaseData(islandUUID, ensured);
                        }
                    } catch (Throwable error) {
                        OneBlockModule.log("Failed to parse data for element: " + islandDataElement);
                        error.printStackTrace();
                    }
                }
            }
        } catch (Throwable error) {
            error.printStackTrace();
        }
    }

    @Override
    public void save() {
        JsonArray islandData = new JsonArray();

        for (Island island : SuperiorSkyblockAPI.getGrid().getIslands()) {
            IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, false);
            if (islandPhaseData != null) {
                IslandPhaseData cleaned = OneBlockDataCodec.cleanupExpiredUnlocks(islandPhaseData);
                if (cleaned != islandPhaseData) {
                    module.getPhasesHandler().getDataStore().setPhaseData(island, cleaned);
                    islandPhaseData = cleaned;
                }
            }
            if (islandPhaseData != null && (islandPhaseData.getPhaseBlock() > 0 ||
                    islandPhaseData.getPhaseLevel() > 0 ||
                    !islandPhaseData.getUnlocks().isEmpty() ||
                    !islandPhaseData.getApiUnlocks().isEmpty())) {
                JsonObject jsonObject = OneBlockDataCodec.toJson(islandPhaseData);
                jsonObject.addProperty("island", island.getUniqueId() + "");
                islandData.add(jsonObject);
            }
        }

        File file = new File(module.getDataStoreFolder(), "database.json");

        if (file.isDirectory())
            file.delete();

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(JsonUtils.getGson().toJson(islandData));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void convertOldDatabase(File newFile) {
        File oldFile = new File(module.getModuleFolder(), "database.json");
        if (oldFile.exists()) {
            newFile.getParentFile().mkdirs();
            oldFile.renameTo(newFile);
        }
    }

    private IslandPhaseData buildDefaultPhaseData(Island island) {
        Map<String, java.util.List<IslandPhaseData.OneBlockSlotData>> unlocks = new ConcurrentHashMap<>();
        IslandPhaseData.OneBlockLocation defaultLocation = WorldUtils.getDefaultOneBlockLocation(island);
        String defaultKey = WorldUtils.getDefaultDimensionKey();
        if (defaultLocation != null && defaultKey != null && !defaultKey.isEmpty()) {
            java.util.List<IslandPhaseData.OneBlockSlotData> list = new java.util.ArrayList<>();
            list.add(new IslandPhaseData.OneBlockSlotData(defaultLocation, null));
            unlocks.put(defaultKey, list);
        }
        return new IslandPhaseData(0, 0, 0, unlocks, java.util.Collections.emptyMap());
    }

    private IslandPhaseData ensureDefaultUnlock(Island island, IslandPhaseData phaseData) {
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

}
