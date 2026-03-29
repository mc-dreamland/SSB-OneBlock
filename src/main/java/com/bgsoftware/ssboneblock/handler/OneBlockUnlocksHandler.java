package com.bgsoftware.ssboneblock.handler;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.api.OneBlockSlot;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.superiorskyblock.api.island.Island;

import java.util.*;
public final class OneBlockUnlocksHandler {

    private final OneBlockModule module;

    public OneBlockUnlocksHandler(OneBlockModule module) {
        this.module = module;
    }

    public void setUnlockedOneBlocks(Island island, Collection<OneBlockSlot> slots) {
        if (island == null)
            return;
        setUnlockedOneBlocks(island.getUniqueId(), slots);
    }

    public void setUnlockedOneBlocks(UUID islandUUID, Collection<OneBlockSlot> slots) {
        if (islandUUID == null)
            return;

        Island island = com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getIslandByUUID(islandUUID);
        if (island == null) {
            com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer player = com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI
                    .getPlayers().getSuperiorPlayer(islandUUID);
            if (player != null) {
                island = player.getIsland();
            }
        }
        if (island == null)
            return;

        Map<String, Integer> normalized = new HashMap<>();
        if (slots != null) {
            for (OneBlockSlot slot : slots) {
                if (slot == null)
                    continue;
                String key = slot.getDimension().toUpperCase(Locale.ENGLISH);
                normalized.put(key, normalized.getOrDefault(key, 0) + 1);
            }
        }

        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        IslandPhaseData updated = phaseData;
        Map<String, java.util.List<IslandPhaseData.OneBlockSlotData>> existing = phaseData.getApiUnlocks();
        for (String key : existing.keySet()) {
            int count = normalized.getOrDefault(key.toUpperCase(Locale.ENGLISH), 0);
            updated = updated.withApiUnlockCount(key, count);
        }
        for (Map.Entry<String, Integer> entry : normalized.entrySet()) {
            updated = updated.withApiUnlockCount(entry.getKey(), entry.getValue());
        }
        module.getPhasesHandler().getDataStore().setPhaseData(island, updated);
    }

    public boolean isUnlocked(Island island, OneBlockSlot slot) {
        if (island == null || slot == null)
            return false;

        return isUnlocked(island, slot.getDimension());
    }

    public boolean isUnlocked(Island island, String dimensionKey) {
        if (island == null || dimensionKey == null)
            return false;

        if (!module.getPhasesHandler().canHaveOneBlock(island))
            return false;

        Map<String, Integer> counts = getUnlockedCounts(island);
        return counts.getOrDefault(dimensionKey.toUpperCase(Locale.ENGLISH), 0) > 0;
    }

    public boolean unlockOneBlock(Island island, String dimensionKey, long expiresAt) {
        if (island == null || dimensionKey == null)
            return false;

        if (!module.getPhasesHandler().canHaveOneBlock(island))
            return false;

        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        IslandPhaseData updated = phaseData.addUnlock(dimensionKey, expiresAt <= 0 ? null : expiresAt);
        module.getPhasesHandler().getDataStore().setPhaseData(island, updated);
        return true;
    }

    public boolean setApiUnlockCount(Island island, String dimensionKey, int amount) {
        if (island == null || dimensionKey == null)
            return false;

        if (!module.getPhasesHandler().canHaveOneBlock(island))
            return false;

        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        IslandPhaseData updated = phaseData.withApiUnlockCount(dimensionKey, amount);
        module.getPhasesHandler().getDataStore().setPhaseData(island, updated);
        return true;
    }

    public Map<String, Integer> getUnlockedCounts(Island island) {
        if (island == null || !module.getPhasesHandler().canHaveOneBlock(island))
            return Collections.emptyMap();

        Map<String, Integer> counts = new HashMap<>();
        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = getUnlocks(island);
        for (Map.Entry<String, List<IslandPhaseData.OneBlockSlotData>> entry : unlocks.entrySet()) {
            counts.put(entry.getKey().toUpperCase(Locale.ENGLISH),
                    counts.getOrDefault(entry.getKey().toUpperCase(Locale.ENGLISH), 0) + entry.getValue().size());
        }
        for (Map.Entry<String, List<IslandPhaseData.OneBlockSlotData>> entry : phaseData.getApiUnlocks().entrySet()) {
            counts.put(entry.getKey().toUpperCase(Locale.ENGLISH),
                    counts.getOrDefault(entry.getKey().toUpperCase(Locale.ENGLISH), 0) + countActiveApiSlots(entry.getValue()));
        }

        return counts;
    }

    public Map<String, Integer> getApiCounts(Island island) {
        if (island == null || !module.getPhasesHandler().canHaveOneBlock(island))
            return Collections.emptyMap();

        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, java.util.List<IslandPhaseData.OneBlockSlotData>> entry : phaseData.getApiUnlocks().entrySet()) {
            counts.put(entry.getKey().toUpperCase(Locale.ENGLISH), countActiveApiSlots(entry.getValue()));
        }
        return counts;
    }

    public int getUnlockedCount(Island island) {
        int total = 0;
        for (int count : getUnlockedCounts(island).values()) {
            total += count;
        }
        return total;
    }

    public Map<String, List<IslandPhaseData.OneBlockSlotData>> getUnlocks(Island island) {
        if (island == null || !module.getPhasesHandler().canHaveOneBlock(island))
            return java.util.Collections.emptyMap();

        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = phaseData.getUnlocks();
        Map<String, List<IslandPhaseData.OneBlockSlotData>> cleaned = new HashMap<>();
        boolean modified = false;
        long now = System.currentTimeMillis();

        for (Map.Entry<String, java.util.List<IslandPhaseData.OneBlockSlotData>> entry : unlocks.entrySet()) {
            java.util.List<IslandPhaseData.OneBlockSlotData> valid = new java.util.ArrayList<>();
            for (IslandPhaseData.OneBlockSlotData slot : entry.getValue()) {
                if (slot == null) {
                    valid.add(null);
                    continue;
                }
                Long expiresAt = slot.getExpiresAt();
                if (expiresAt == null || expiresAt <= 0 || expiresAt > now) {
                    valid.add(slot);
                } else {
                    modified = true;
                }
            }
            if (!valid.isEmpty()) {
                cleaned.put(entry.getKey().toUpperCase(Locale.ENGLISH), valid);
            } else if (!entry.getValue().isEmpty()) {
                modified = true;
            }
        }

        if (modified) {
            IslandPhaseData updated = new IslandPhaseData(phaseData.getPhaseLevel(), phaseData.getPhaseBlock(),
                    phaseData.getPhaseLoopTimes(), cleaned, phaseData.getApiUnlocks());
            module.getPhasesHandler().getDataStore().setPhaseData(island, updated);
        }

        return cleaned;
    }

    private static int countActiveApiSlots(List<IslandPhaseData.OneBlockSlotData> slots) {
        int count = 0;
        for (IslandPhaseData.OneBlockSlotData slot : slots) {
            if (slot == null || slot.isActive())
                count++;
        }
        return count;
    }

}
