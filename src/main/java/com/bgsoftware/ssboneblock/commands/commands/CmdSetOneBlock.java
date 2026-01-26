package com.bgsoftware.ssboneblock.commands.commands;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.commands.ICommand;
import com.bgsoftware.ssboneblock.gui.OneBlockMenu;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CmdSetOneBlock implements ICommand {

    @Override
    public String getLabel() {
        return "setoneblock";
    }

    @Override
    public String getUsage(java.util.Locale locale) {
        return "setoneblock";
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
        return 1;
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

        OneBlockMenu.open(module, player, island);
    }

    @Override
    public java.util.List<String> tabComplete(OneBlockModule module, CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }

}
