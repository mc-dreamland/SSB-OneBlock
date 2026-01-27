package com.bgsoftware.ssboneblock.api;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public final class OneBlockAPI {

    private OneBlockAPI() {
    }

    public static void setUnlockedOneBlocks(Island island, Collection<OneBlockSlot> slots) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null || island == null)
            return;

        module.getOneBlockUnlocksHandler().setUnlockedOneBlocks(island, slots);
    }

    public static void setUnlockedOneBlocks(UUID islandUUID, Collection<OneBlockSlot> slots) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null || islandUUID == null)
            return;

        Island island = SuperiorSkyblockAPI.getIslandByUUID(islandUUID);
        if (island == null) {
            SuperiorPlayer player = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(islandUUID);
            if (player != null)
                island = player.getIsland();
        }

        if (island != null)
            module.getOneBlockUnlocksHandler().setUnlockedOneBlocks(island, slots);
    }

    public static boolean isOneBlockUnlocked(Island island, OneBlockSlot slot) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null)
            return false;

        return module.getOneBlockUnlocksHandler().isUnlocked(island, slot);
    }

    public static boolean isOneBlockUnlocked(Island island, String dimensionKey) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null)
            return false;

        return module.getOneBlockUnlocksHandler().isUnlocked(island, dimensionKey);
    }

    public static int getUnlockedOneBlocksCount(Island island) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null)
            return 0;

        return module.getOneBlockUnlocksHandler().getUnlockedCount(island);
    }

    public static void setApiUnlockedCount(Island island, String dimensionKey, int count) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null || island == null)
            return;

        module.getOneBlockUnlocksHandler().setApiUnlockCount(island, dimensionKey, count);
    }

    public static boolean unlockOneBlock(Island island, String dimensionKey) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null)
            return false;

        int current = module.getOneBlockUnlocksHandler().getApiCounts(island)
                .getOrDefault(dimensionKey.toUpperCase(Locale.ENGLISH), 0);
        return module.getOneBlockUnlocksHandler().setApiUnlockCount(island, dimensionKey, current + 1);
    }

}
