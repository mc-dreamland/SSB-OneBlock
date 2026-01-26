package com.bgsoftware.ssboneblock.commands.commands;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.commands.ICommand;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CmdSetOneBlock implements ICommand {

    @Override
    public String getLabel() {
        return "setoneblock";
    }

    @Override
    public String getUsage(java.util.Locale locale) {
        return "setoneblock [" + Message.COMMAND_ARGUMENT_ONEBLOCK_ID.getMessage(locale) + "]";
    }

    @Override
    public String getPermission() {
        return "oneblock.setoneblock";
    }

    @Override
    public String getDescription(java.util.Locale locale) {
        return Message.COMMAND_DESCRIPTION_SET_ONEBLOCK.getMessage(locale);
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 2;
    }

    @Override
    public void perform(OneBlockModule module, CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            Message.NO_PERMISSION.send(sender);
            return;
        }

        Player player = (Player) sender;
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        Island island = superiorPlayer == null ? null : superiorPlayer.getIsland();

        if (island == null) {
            Message.INVALID_ISLAND.send(sender, player.getName());
            return;
        }

        if (!module.getPhasesHandler().canHaveOneBlock(island)) {
            Message.ISLAND_MISSING_BLOCK.send(sender);
            return;
        }

        if (island.getOwner() == null ||
                !island.getOwner().getUniqueId().equals(superiorPlayer.getUniqueId())) {
            Message.NO_PERMISSION.send(sender);
            return;
        }

        int id = 0;
        if (args.length > 1) {
            try {
                id = Integer.parseInt(args[1]);
            } catch (Exception ex) {
                Message.INVALID_NUMBER.send(sender, args[1]);
                return;
            }
        }

        if (id < 0) {
            Message.INVALID_NUMBER.send(sender, id);
            return;
        }

        String dimensionKey = WorldUtils.getDimensionKey(player.getLocation());
        if (!module.getOneBlockUnlocksHandler().isUnlocked(island, dimensionKey, id)) {
            Message.ONEBLOCK_NOT_UNLOCKED.send(sender, id);
            return;
        }

        Location targetLocation = player.getLocation().clone().subtract(0, 1, 0).getBlock().getLocation();
        String key = WorldUtils.buildOneBlockKey(dimensionKey, id);
        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        IslandPhaseData.OneBlockLocation oneBlockLocation = new IslandPhaseData.OneBlockLocation(
                targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
        module.getPhasesHandler().getDataStore().setPhaseData(island, islandPhaseData.withOneBlockLocation(key, oneBlockLocation));

        targetLocation.getBlock().setType(Material.STONE);

        Message.SET_ONEBLOCK_SUCCESS.send(sender, id, dimensionKey,
                targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
    }

    @Override
    public List<String> tabComplete(OneBlockModule module, CommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 2) {
            list.add("0");
        }

        return list;
    }

}
