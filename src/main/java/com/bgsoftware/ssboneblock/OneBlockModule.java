package com.bgsoftware.ssboneblock;

import com.bgsoftware.ssboneblock.commands.CommandsHandler;
import com.bgsoftware.ssboneblock.data.DataType;
import com.bgsoftware.ssboneblock.data.FlatDataStore;
import com.bgsoftware.ssboneblock.data.SqlDataStore;
import com.bgsoftware.ssboneblock.handler.OneBlockUnlocksHandler;
import com.bgsoftware.ssboneblock.handler.PhasesHandler;
import com.bgsoftware.ssboneblock.handler.SettingsHandler;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.ssboneblock.listeners.BlocksListener;
import com.bgsoftware.ssboneblock.listeners.IslandsListener;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.ssboneblock.phases.PhaseData;
import com.bgsoftware.ssboneblock.task.NextPhaseTimer;
import com.bgsoftware.ssboneblock.task.SaveTimer;
import com.bgsoftware.ssboneblock.utils.NMSAdapter;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.service.placeholders.PlaceholdersService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class OneBlockModule extends JavaPlugin {

    private static final int SUPPORTED_API_VERSION = 12;

    private static OneBlockModule instance;

    private SuperiorSkyblock plugin;

    private PhasesHandler phasesHandler;
    private SettingsHandler settingsHandler;
    private final OneBlockUnlocksHandler oneBlockUnlocksHandler = new OneBlockUnlocksHandler(this);
    private NMSAdapter nmsAdapter;

    public OneBlockModule() {
        instance = this;
    }

    @Override
    public void onEnable() {
        this.plugin = SuperiorSkyblockPlugin.getPlugin();
        nmsAdapter = new NMSAdapter();


        onReload();

        String label = "oneblock";

        CommandsHandler commandsHandler = new CommandsHandler(this, label);
        CommandMap commandMap = getServer().getCommandMap();
        commandMap.register("ssboneblock", commandsHandler);

        try {
            registerPlaceholders();
        } catch (Throwable ignored) {
            // API methods doesn't exist yet.
        }

        SaveTimer.startTimer(this);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> phasesHandler.getDataStore().load(), 1L);

        onReload();
        loadData();
        initListeners();
    }

    public void onReload() {
        if (this.phasesHandler != null)
            this.phasesHandler.getDataStore().save();

        WorldUtils.clearOneBlockCache();

        this.settingsHandler = new SettingsHandler(this);
        this.phasesHandler = new PhasesHandler(this, this.phasesHandler != null ? this.phasesHandler.getDataStore() :
                (this.settingsHandler.dataType == DataType.FLAT ? new FlatDataStore(this) : new SqlDataStore(this)));

        Message.reload();
    }

    @Override
    public void onDisable() {
        NextPhaseTimer.cancelTimers();
        SaveTimer.stopTimer();
        if (this.phasesHandler != null)
            this.phasesHandler.getDataStore().save();
        WorldUtils.clearOneBlockCache();
    }

    public void loadData() {
        this.phasesHandler.getDataStore().load();
    }

    public void initListeners() {
        IslandsListener islandsListener = new IslandsListener(this);
        BlocksListener blocksListener = new BlocksListener(this);
        getServer().getPluginManager().registerEvents(islandsListener, this);
        getServer().getPluginManager().registerEvents(blocksListener, this);

    }


    public PhasesHandler getPhasesHandler() {
        return phasesHandler;
    }

    public SettingsHandler getSettings() {
        return settingsHandler;
    }

    public NMSAdapter getNMSAdapter() {
        return nmsAdapter;
    }

    public OneBlockUnlocksHandler getOneBlockUnlocksHandler() {
        return oneBlockUnlocksHandler;
    }

    public SuperiorSkyblock getPlugin() {
        return plugin;
    }

    public SuperiorSkyblock getSPlugin() {
        return plugin;
    }

    public static void log(String message) {
        instance.getLogger().info(message);
    }

    public static OneBlockModule getModule() {
        return instance;
    }

    private void registerPlaceholders() {
        RegisteredServiceProvider<PlaceholdersService> registeredServiceProvider = Bukkit.getServicesManager().getRegistration(PlaceholdersService.class);

        if (registeredServiceProvider == null)
            return;

        PlaceholdersService placeholdersService = registeredServiceProvider.getProvider();

        if (placeholdersService == null)
            return;

        placeholdersService.registerPlaceholder("oneblock_phase_level", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "0";

            return String.valueOf(islandPhaseData.getPhaseLevel() + 1);
        });

        placeholdersService.registerPlaceholder("oneblock_phase_block", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "0";

            return String.valueOf(islandPhaseData.getPhaseBlock());
        });

        placeholdersService.registerPlaceholder("oneblock_progress", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "0";

            PhaseData phaseData = phasesHandler.getPhaseData(islandPhaseData);

            if (phaseData == null)
                return "0";

            return String.valueOf(islandPhaseData.getPhaseBlock() * 100 / phaseData.getActionsSize());
        });

        placeholdersService.registerPlaceholder("oneblock_loops", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);


            return String.valueOf(islandPhaseData.getPhaseLoopTimes());
        });

        placeholdersService.registerPlaceholder("oneblock_total_blocks", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "0";

            PhaseData phaseData = phasesHandler.getPhaseData(islandPhaseData);

            if (phaseData == null)
                return "0";

            int phaseLevel = islandPhaseData.getPhaseLevel();
            int phaseBlock = islandPhaseData.getPhaseBlock();
            double phasesLoopMultiple = getSettings().phasesLoopMultiple;
            int totalBefore = phasesHandler.getPhaseData(phaseLevel).getStart() - 1;

            PhaseData maxPhaseData = phasesHandler.getMaxPhaseData();
            if (maxPhaseData == null) {
                return "0";
            }
            int end = maxPhaseData.getEnd();
            double currentMultiplier = 1.0; // 初始倍率为1
            int phaseLoopTimes = islandPhaseData.getPhaseLoopTimes();
            double loopedProgress = 0;
            for (int i = 0; i < phaseLoopTimes; i++) {
                if (i == 0) {
                    // 第一次 loop，不乘倍率
                    loopedProgress += end;
                } else {
                    // 后续 loop：先更新倍率，再累加
                    currentMultiplier *= phasesLoopMultiple;
                    loopedProgress += end * currentMultiplier;
                }
            }

            double totalProgress = totalBefore * currentMultiplier + loopedProgress + phaseBlock;
            return String.valueOf(totalProgress);
        });

        placeholdersService.registerPlaceholder("oneblock_blocks_in_phase", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "0";

            PhaseData phaseData = phasesHandler.getPhaseData(islandPhaseData);

            if (phaseData == null)
                return "0";

            return String.valueOf(phaseData.getActionsSize());
        });

        placeholdersService.registerPlaceholder("oneblock_phase_name", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "";

            PhaseData phaseData = phasesHandler.getPhaseData(islandPhaseData);

            if (phaseData == null)
                return "";

            return phaseData.getName();
        });

        placeholdersService.registerPlaceholder("oneblock_next_phase_name", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            IslandPhaseData islandPhaseData = phasesHandler.getDataStore().getPhaseData(island, false);

            if (islandPhaseData == null)
                return "";

            PhaseData phaseData = phasesHandler.getPhaseData(islandPhaseData.getPhaseLevel() + 1);

            if (phaseData == null)
                return "";

            return phaseData.getName();
        });

        placeholdersService.registerPlaceholder("oneblock_unlocked_blocks", (island, superiorPlayer) -> {
            if (island == null)
                return null;

            return String.valueOf(oneBlockUnlocksHandler.getUnlockedCount(island));
        });

        placeholdersService.registerPlaceholder("oneblock_locations_x", (island, superiorPlayer) -> {
            if (island == null || superiorPlayer == null)
                return null;

            if (superiorPlayer.getWorld() == null)
                return "";

            String dimensionKey = superiorPlayer.getWorld().getEnvironment().name();
            List<Location> locations = WorldUtils.getOneBlockLocations(island, dimensionKey);
            if (locations.isEmpty())
                return "";

            StringBuilder builder = new StringBuilder();
            for (Location location : locations) {
                if (!builder.isEmpty())
                    builder.append(',');
                builder.append(location.getBlockX());
            }
            return builder.toString();
        });

        placeholdersService.registerPlaceholder("oneblock_locations_y", (island, superiorPlayer) -> {
            if (island == null || superiorPlayer == null)
                return null;

            if (superiorPlayer.getWorld() == null)
                return "";

            String dimensionKey = superiorPlayer.getWorld().getEnvironment().name();
            List<Location> locations = WorldUtils.getOneBlockLocations(island, dimensionKey);
            if (locations.isEmpty())
                return "";

            StringBuilder builder = new StringBuilder();
            for (Location location : locations) {
                if (!builder.isEmpty())
                    builder.append(',');
                builder.append(location.getBlockY());
            }
            return builder.toString();
        });

        placeholdersService.registerPlaceholder("oneblock_locations_z", (island, superiorPlayer) -> {
            if (island == null || superiorPlayer == null)
                return null;

            if (superiorPlayer.getWorld() == null)
                return "";

            String dimensionKey = superiorPlayer.getWorld().getEnvironment().name();
            List<Location> locations = WorldUtils.getOneBlockLocations(island, dimensionKey);
            if (locations.isEmpty())
                return "";

            StringBuilder builder = new StringBuilder();
            for (Location location : locations) {
                if (!builder.isEmpty())
                    builder.append(',');
                builder.append(location.getBlockZ());
            }
            return builder.toString();
        });

    }


}
