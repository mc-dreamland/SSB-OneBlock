package com.bgsoftware.ssboneblock.api;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class OneBlockAPI {

    private OneBlockAPI() {
    }

    public static void setUnlockedOneBlocks(Island island, Collection<OneBlockSlot> slots) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null || island == null)
            return;

        Map<String, Integer> beforeCounts = new HashMap<>(module.getOneBlockUnlocksHandler().getApiCounts(island));
        Map<String, Integer> requestedCounts = summarizeSlots(slots);
        module.getOneBlockUnlocksHandler().setUnlockedOneBlocks(island, slots);
        Map<String, Integer> afterCounts = new HashMap<>(module.getOneBlockUnlocksHandler().getApiCounts(island));

        if (!beforeCounts.equals(afterCounts) || !beforeCounts.equals(requestedCounts)) {
            logApiDebug(module, island, "setUnlockedOneBlocks",
                    "requested=" + requestedCounts +
                            ", before=" + beforeCounts +
                            ", after=" + afterCounts +
                            ", slots=" + summarizeAllApiSlots(module, island));
        }
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
            setUnlockedOneBlocks(island, slots);
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

    public static int getUnlockedApiOneBlocksCount(Island island, String dimensionKey) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null)
            return 0;

        return module.getOneBlockUnlocksHandler().getApiCounts(island)
                .getOrDefault(dimensionKey.toUpperCase(Locale.ENGLISH), 0);
    }

    public static void setApiUnlockedCount(Island island, String dimensionKey, int count) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null || island == null)
            return;

        String normalizedKey = dimensionKey.toUpperCase(Locale.ENGLISH);
        int before = module.getOneBlockUnlocksHandler().getApiCounts(island)
                .getOrDefault(normalizedKey, 0);
        module.getOneBlockUnlocksHandler().setApiUnlockCount(island, dimensionKey, count);
        int after = module.getOneBlockUnlocksHandler().getApiCounts(island)
                .getOrDefault(normalizedKey, 0);

        if (before != count || before != after) {
            logApiDebug(module, island, "setApiUnlockedCount",
                    "dimension=" + normalizedKey +
                            ", requested=" + count +
                            ", before=" + before +
                            ", after=" + after +
                            ", slots=" + summarizeApiSlots(module, island, normalizedKey));
        }
    }

    public static boolean unlockOneBlock(Island island, String dimensionKey) {
        OneBlockModule module = OneBlockModule.getModule();
        if (module == null)
            return false;

        int current = module.getOneBlockUnlocksHandler().getApiCounts(island)
                .getOrDefault(dimensionKey.toUpperCase(Locale.ENGLISH), 0);
        return module.getOneBlockUnlocksHandler().setApiUnlockCount(island, dimensionKey, current + 1);
    }

    private static Map<String, Integer> summarizeSlots(Collection<OneBlockSlot> slots) {
        Map<String, Integer> counts = new HashMap<>();
        if (slots == null)
            return counts;

        for (OneBlockSlot slot : slots) {
            if (slot == null)
                continue;
            String key = slot.getDimension().toUpperCase(Locale.ENGLISH);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }

        return counts;
    }

    private static void logApiDebug(OneBlockModule module, Island island, String action, String details) {
        String owner = island.getOwner() == null ? "null" : island.getOwner().getUniqueId().toString();
        OneBlockModule.log("[debug][api] " + action +
                " island=" + island.getUniqueId() +
                ", owner=" + owner +
                ", caller=" + findExternalCaller() +
                ", " + details);
    }

    private static String summarizeAllApiSlots(OneBlockModule module, Island island) {
        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        if (phaseData == null || phaseData.getApiUnlocks().isEmpty())
            return "{}";

        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (String dimensionKey : phaseData.getApiUnlocks().keySet()) {
            if (!first)
                builder.append(", ");
            first = false;
            builder.append(dimensionKey.toUpperCase(Locale.ENGLISH))
                    .append('=')
                    .append(summarizeApiSlots(module, island, dimensionKey));
        }
        builder.append('}');
        return builder.toString();
    }

    private static String summarizeApiSlots(OneBlockModule module, Island island, String dimensionKey) {
        IslandPhaseData phaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        if (phaseData == null)
            return "[]";

        List<IslandPhaseData.OneBlockSlotData> slots = phaseData.getApiUnlocks()
                .get(dimensionKey.toUpperCase(Locale.ENGLISH));
        if (slots == null || slots.isEmpty())
            return "[]";

        List<String> parts = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            IslandPhaseData.OneBlockSlotData slot = slots.get(index);
            if (slot == null) {
                parts.add("#" + index + "(null)");
                continue;
            }

            IslandPhaseData.OneBlockLocation location = slot.getLocation();
            String locationText = location == null ? "unset" :
                    ((int) Math.floor(location.getX())) + "," +
                            ((int) Math.floor(location.getY())) + "," +
                            ((int) Math.floor(location.getZ()));
            parts.add("#" + index + "(active=" + slot.isActive() + ",loc=" + locationText + ")");
        }

        return parts.toString();
    }

    private static String findExternalCaller() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className.equals(Thread.class.getName()))
                continue;
            if (className.equals(OneBlockAPI.class.getName()))
                continue;
            if (className.startsWith("com.bgsoftware.ssboneblock."))
                continue;
            if (className.startsWith("java.") || className.startsWith("javax.") ||
                    className.startsWith("sun.") || className.startsWith("jdk."))
                continue;
            return className + "#" + element.getMethodName() + ':' + element.getLineNumber();
        }

        return "unknown";
    }

}
