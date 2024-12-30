package com.bgsoftware.ssboneblock.handler;

import com.bgsoftware.common.config.CommentedConfiguration;
import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.commands.commands.SSBCheckCmd;
import com.bgsoftware.ssboneblock.data.DataType;
import com.bgsoftware.ssboneblock.error.ParsingException;
import com.bgsoftware.ssboneblock.factory.BlockOffsetFactory;
import com.bgsoftware.ssboneblock.utils.Pair;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.BlockOffset;
import com.google.gson.JsonPrimitive;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public final class SettingsHandler {

    public final BlockOffset blockOffset;
    public final List<String> timerFormat;
    public final List<Pair<String, Integer>> phases;
    public final List<String> whitelistedSchematics;
    public final DataType dataType;
    public final double phasesLoopMultiple;
    public final boolean phasesLoop;
    public final boolean pistonsInteraction;

    public SettingsHandler(OneBlockModule plugin) {
        File file = new File(plugin.getModuleFolder(), "config.yml");

        if (!file.exists())
            plugin.saveResource("config.yml");

        CommentedConfiguration cfg = CommentedConfiguration.loadConfiguration(file);

        try {
            cfg.syncWithConfig(file, plugin.getResource("config.yml"), "config.yml");
        } catch (IOException error) {
            throw new RuntimeException(error);
        }

        BlockOffset blockOffset;
        try {
            blockOffset = BlockOffsetFactory.createOffset(new JsonPrimitive(cfg.getString("block-offset")));
        } catch (ParsingException error) {
            blockOffset = BlockOffsetFactory.createOffset(0, -1, 0);
        }
        this.blockOffset = blockOffset;

        Object timerFormat = cfg.get("timer-format");
        if (timerFormat instanceof String) {
            this.timerFormat = Arrays.asList(ChatColor.translateAlternateColorCodes('&', (String) timerFormat).split("\n"));
        } else {
            // noinspection unchecked
            this.timerFormat = ((List<String>) timerFormat).stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
        }
        Collections.reverse(this.timerFormat);

        List<String> ph = cfg.getStringList("phases");
        phases = new ArrayList<>();
        for (String s : ph) {
            s = s.replace(" ", "");
            String[] split = s.split("@");
            phases.add(new Pair<>(split[1], Integer.valueOf(split[0])));
        }

        if (cfg.getBoolean("inject-island-command", true)) {
            SuperiorSkyblockAPI.registerCommand(new SSBCheckCmd());
        }

        whitelistedSchematics = cfg.getStringList("whitelisted-schematics")
                .stream().map(String::toUpperCase).collect(Collectors.toList());

        DataType dataType;
        try {
            dataType = DataType.valueOf(cfg.getString("data-type").toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning("Invalid data-type `" + cfg.getString("data-type") + "`, using FLAT.");
            dataType = DataType.FLAT;
        }
        this.dataType = dataType;

        this.phasesLoop = cfg.getBoolean("phases-loop", false);
        this.phasesLoopMultiple = cfg.getDouble("phases-loop-multiple", 1.0d);
        this.pistonsInteraction = cfg.getBoolean("piston-interaction", true);
    }

}
