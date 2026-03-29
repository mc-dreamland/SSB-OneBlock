package com.bgsoftware.ssboneblock.handler;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.actions.Action;
import com.bgsoftware.ssboneblock.data.DataStore;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.ssboneblock.phases.PhaseData;
import com.bgsoftware.ssboneblock.task.NextPhaseTimer;
import com.bgsoftware.ssboneblock.utils.JsonUtils;
import com.bgsoftware.ssboneblock.utils.Pair;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.database.bridge.IslandsDatabaseBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PhasesHandler {

    private final Map<String, JsonArray> possibilities = new ConcurrentHashMap<>();

    private final OneBlockModule module;
    private final DataStore dataStore;
    private final PhaseData[] phaseData;

    public PhasesHandler(OneBlockModule module, DataStore dataStore) {
        this.module = module;
        this.dataStore = dataStore;
        phaseData = loadData();
        this.times = new HashMap<>();
    }

    public JsonArray getPossibilities(String possibilities) {
        return this.possibilities.getOrDefault(possibilities.toLowerCase(), new JsonArray());
    }

    @Nullable
    public PhaseData getPhaseData(IslandPhaseData islandPhaseData) {
        return getPhaseData(islandPhaseData.getPhaseLevel());
    }

    @Nullable
    public PhaseData getPhaseData(int phaseLevel) {
        return phaseLevel >= phaseData.length ? null : phaseData[phaseLevel];
    }

    public PhaseData getMaxPhaseData() {
        if (phaseData == null || phaseData.length == 0) {
            return null;
        }
        return phaseData[phaseData.length - 1];
    }

    public HashMap<UUID, Integer> times;

    public void runNextAction(Island island, @Nullable SuperiorPlayer superiorPlayer) {
        runNextAction(island, superiorPlayer, null);
    }

    public void runNextAction(Island island, @Nullable SuperiorPlayer superiorPlayer, @Nullable Location oneBlockLocation) {
        if (!canHaveOneBlock(island)) {
            return;
        }
        int i = 0;
        if (times.containsKey(island.getUniqueId())) {
            i = times.get(island.getUniqueId());
            i++;
            if (i >= 100) {
                IslandsDatabaseBridge.savePersistentDataContainer(island);
                i = 0;
            }
        } else {
            times.put(island.getUniqueId(), 0);
        }
        times.put(island.getUniqueId(), i);


        IslandPhaseData islandPhaseData = this.dataStore.getPhaseData(island, true);

        if (islandPhaseData.getPhaseLevel() >= phaseData.length) {
            if (superiorPlayer != null)
                Message.NO_MORE_PHASES.send(superiorPlayer);
            return;
        }

        PhaseData phaseData = this.phaseData[islandPhaseData.getPhaseLevel()];
        Action action = phaseData.getAction(islandPhaseData.getPhaseBlock(), islandPhaseData.getPhaseLoopTimes());

        Location targetLocation = oneBlockLocation == null ? WorldUtils.getOneBlock(island) : oneBlockLocation.clone();
        if (targetLocation == null) {
            return;
        }

        if (action == null) {
            int nextPhaseLevel = islandPhaseData.getPhaseLevel() + 1 < this.phaseData.length ?
                    islandPhaseData.getPhaseLevel() + 1 : (module.getSettings().phasesLoop ? 0 : -1);
            int loopTimes = islandPhaseData.getPhaseLevel() + 1 < this.phaseData.length ?
                    islandPhaseData.getPhaseLoopTimes() :( module.getSettings().phasesLoop ? islandPhaseData.getPhaseLoopTimes() + 1 : 0);

            runNextActionTimer(island, superiorPlayer, targetLocation, phaseData, nextPhaseLevel, loopTimes);
            return;
        }

        Optional.ofNullable(NextPhaseTimer.getTimer(island)).ifPresent(nextPhaseTimer -> {
            nextPhaseTimer.setRunFinishCallback(false);
            nextPhaseTimer.cancel();
        });

        action.run(targetLocation, island, superiorPlayer);

        IslandPhaseData newPhaseData = this.dataStore.getPhaseData(island, false);

        if (newPhaseData.equals(islandPhaseData))
            this.dataStore.setPhaseData(island, islandPhaseData.nextBlock());

        Message.PHASE_PROGRESS.send(superiorPlayer,
                String.format("%.1f", islandPhaseData.getPhaseBlock() * 100.0D / ((phaseData.getEnd() - phaseData.getStart()) * Math.pow(OneBlockModule.getModule().getSettings().phasesLoopMultiple, islandPhaseData.getPhaseLoopTimes()))),
                islandPhaseData.getPhaseBlock(),
                phaseData.getActionsSize());

        // We check for last phase here as well.
        if (module.getSettings().phasesLoop && islandPhaseData.getPhaseBlock() + 1 == phaseData.getActionsSize() &&
                islandPhaseData.getPhaseLevel() + 1 == this.phaseData.length)
            runNextActionTimer(island, superiorPlayer, targetLocation, phaseData, 0, islandPhaseData.getPhaseLoopTimes());
    }

    private void runNextActionTimer(Island island, @Nullable SuperiorPlayer superiorPlayer, Location oneBlockLocation,
                                    PhaseData phaseData, int nextPhaseLevel, int loopTimes) {
        NextPhaseTimer activeTimer = NextPhaseTimer.getTimer(island);

        oneBlockLocation.getBlock().setType(Material.BEDROCK);

        if (activeTimer == null && nextPhaseLevel >= 0) {
            new NextPhaseTimer(island, phaseData.getNextPhaseCooldown(), oneBlockLocation,
                    () -> setPhaseLevel(island, nextPhaseLevel, superiorPlayer, loopTimes, oneBlockLocation));
        }
    }

    public boolean setPhaseLevel(Island island, int phaseLevel, @Nullable SuperiorPlayer superiorPlayer, int loopTimes) {
        return setPhaseLevel(island, phaseLevel, superiorPlayer, loopTimes, null);
    }

    public boolean setPhaseLevel(Island island, int phaseLevel, @Nullable SuperiorPlayer superiorPlayer, int loopTimes,
                                 @Nullable Location oneBlockLocation) {
        if (phaseLevel >= phaseData.length)
            return false;

        IslandPhaseData existingPhaseData = this.dataStore.getPhaseData(island, true);
        IslandPhaseData islandPhaseData = new IslandPhaseData(phaseLevel, 0, loopTimes,
                existingPhaseData.getUnlocks(), existingPhaseData.getApiUnlocks());
        this.dataStore.setPhaseData(island, islandPhaseData);

        runNextAction(island, superiorPlayer, oneBlockLocation);

        return true;
    }

    public boolean setPhaseBlock(Island island, int phaseBlock, @Nullable SuperiorPlayer superiorPlayer) {
        IslandPhaseData islandPhaseData = this.dataStore.getPhaseData(island, true);
        PhaseData phaseData = this.phaseData[islandPhaseData.getPhaseLevel()];

        if (phaseData.getAction(phaseBlock, islandPhaseData.getPhaseLoopTimes()) == null)
            return false;

        this.dataStore.setPhaseData(island, new IslandPhaseData(islandPhaseData.getPhaseLevel(), phaseBlock,
                islandPhaseData.getPhaseLoopTimes(), islandPhaseData.getUnlocks(), islandPhaseData.getApiUnlocks()));
        runNextAction(island, superiorPlayer, null);

        return true;
    }

    public boolean canHaveOneBlock(Island island) {
        return !island.isSpawn() && (module.getSettings().whitelistedSchematics.isEmpty() ||
                module.getSettings().whitelistedSchematics.contains(island.getSchematicName().toUpperCase()));
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private PhaseData[] loadData() {
        File phasesFolder = new File(module.getDataFolder(), "phases");
        File possibilitiesFolder = new File(module.getDataFolder(), "possibilities");

        if (!phasesFolder.exists()) {
            phasesFolder.mkdirs();
            module.saveResource("phases/plains-phase.json", false);
            module.saveResource("phases/underground-phase.json", false);
            module.saveResource("phases/snow-phase.json", false);
            module.saveResource("phases/ocean-phase.json", false);
            module.saveResource("phases/jungle-phase.json", false);
            module.saveResource("phases/red-desert-phase.json", false);
            module.saveResource("phases/nether-phase.json", false);
            module.saveResource("phases/idyll-phase.json", false);
            module.saveResource("phases/desolate-phase.json", false);
            module.saveResource("phases/end-phase.json", false);
        }

        if (!possibilitiesFolder.exists()) {
            possibilitiesFolder.mkdirs();
            module.saveResource("possibilities/plains-blocks.json", false);
            module.saveResource("possibilities/plains-chests.json", false);
            module.saveResource("possibilities/plains-mobs.json", false);
            module.saveResource("possibilities/underground-blocks.json", false);
            module.saveResource("possibilities/underground-chests.json", false);
            module.saveResource("possibilities/underground-mobs.json", false);
            module.saveResource("possibilities/snow-blocks.json", false);
            module.saveResource("possibilities/snow-chests.json", false);
            module.saveResource("possibilities/snow-mobs.json", false);
            module.saveResource("possibilities/ocean-blocks.json", false);
            module.saveResource("possibilities/ocean-chests.json", false);
            module.saveResource("possibilities/ocean-mobs.json", false);
            module.saveResource("possibilities/jungle-blocks.json", false);
            module.saveResource("possibilities/jungle-chests.json", false);
            module.saveResource("possibilities/jungle-mobs.json", false);
            module.saveResource("possibilities/red-desert-blocks.json", false);
            module.saveResource("possibilities/red-desert-chests.json", false);
            module.saveResource("possibilities/red-desert-mobs.json", false);
            module.saveResource("possibilities/nether-blocks.json", false);
            module.saveResource("possibilities/nether-chests.json", false);
            module.saveResource("possibilities/nether-mobs.json", false);
            module.saveResource("possibilities/idyll-blocks.json", false);
            module.saveResource("possibilities/idyll-chests.json", false);
            module.saveResource("possibilities/idyll-mobs.json", false);
            module.saveResource("possibilities/desolate-blocks.json", false);
            module.saveResource("possibilities/desolate-chests.json", false);
            module.saveResource("possibilities/desolate-mobs.json", false);
            module.saveResource("possibilities/end-blocks.json", false);
            module.saveResource("possibilities/end-chests.json", false);
            module.saveResource("possibilities/end-mobs.json", false);
            module.saveResource("possibilities/superchest.json", false);
            module.saveResource("possibilities/rarechest.json", false);
        }

        File[] possibilityFiles = possibilitiesFolder.listFiles();

        assert possibilityFiles != null;

        for (File possibilityFile : possibilityFiles) {
            try {
                JsonArray jsonArray = JsonUtils.parseFile(possibilityFile, JsonArray.class);
                possibilities.put(possibilityFile.getName().toLowerCase(), jsonArray);
            } catch (Exception ex) {
                OneBlockModule.log("Failed to parse possibilities " + possibilityFile.getName() + ":");
                ex.printStackTrace();
            }
        }

        List<PhaseData> phaseDataList = new ArrayList<>();

        for (Pair<String, Integer> phaseFileName : module.getSettings().phases) {
            File phaseFile = new File(module.getDataFolder() + "/phases", phaseFileName.first);

            if (!phaseFile.exists()) {
                OneBlockModule.log("Failed find the phase file " + phaseFileName.first + "..." + phaseFile.getPath());
                continue;
            }

            OneBlockModule.log("Checking " + phaseFileName.first);

            try {
                JsonObject jsonObject = JsonUtils.parseFile(phaseFile, JsonObject.class);
                PhaseData.fromJson(jsonObject, this, phaseFileName.first).ifPresent(phaseDataList::add);
            } catch (Exception ex) {
                OneBlockModule.log("Failed to parse phase " + phaseFile.getName() + ":");
                ex.printStackTrace();
            }
        }

        return phaseDataList.toArray(new PhaseData[0]);
    }

}
