package com.bgsoftware.ssboneblock.handler;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.api.OneBlockSlot;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OneBlockUnlocksHandler {

    private final ConcurrentMap<UUID, Set<OneBlockSlot>> unlockedOneBlocks = new ConcurrentHashMap<>();
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

        if (slots == null || slots.isEmpty()) {
            unlockedOneBlocks.remove(islandUUID);
            return;
        }

        Set<OneBlockSlot> normalized = new HashSet<>();
        for (OneBlockSlot slot : slots) {
            if (slot != null)
                normalized.add(new OneBlockSlot(slot.getDimension(), slot.getId()));
        }
        unlockedOneBlocks.put(islandUUID, normalized);
    }

    public boolean isUnlocked(Island island, OneBlockSlot slot) {
        if (island == null || slot == null)
            return false;

        if (!module.getPhasesHandler().canHaveOneBlock(island))
            return false;

        if (isDefaultSlot(slot))
            return true;

        Set<OneBlockSlot> slots = unlockedOneBlocks.get(island.getUniqueId());
        return slots != null && slots.contains(slot);
    }

    public boolean isUnlocked(Island island, String dimensionKey, int id) {
        if (dimensionKey == null)
            return false;
        return isUnlocked(island, new OneBlockSlot(dimensionKey, id));
    }

    public int getUnlockedCount(Island island) {
        if (island == null || !module.getPhasesHandler().canHaveOneBlock(island))
            return 0;

        int count = 1;
        Set<OneBlockSlot> slots = unlockedOneBlocks.get(island.getUniqueId());
        if (slots == null || slots.isEmpty())
            return count;

        for (OneBlockSlot slot : slots) {
            if (!isDefaultSlot(slot)) {
                count++;
            }
        }

        return count;
    }

    private boolean isDefaultSlot(OneBlockSlot slot) {
        if (slot.getId() != 0)
            return false;

        Dimension dimension = module.getPlugin().getSettings().getWorlds().getDefaultWorldDimension();
        if (dimension == null)
            return false;

        String defaultKey = dimension.getEnvironment().name().toUpperCase(Locale.ENGLISH);
        return defaultKey.equals(slot.getDimension());
    }

}
