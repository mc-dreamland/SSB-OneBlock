package com.bgsoftware.ssboneblock.commands.commands;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.commands.ICommand;
import com.bgsoftware.ssboneblock.gui.OneBlockMenu;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CmdSetOneBlock implements ICommand {

    @Override
    public String getLabel() {
        return "setoneblock";
    }

    @Override
    public String getUsage(Locale locale) {
        return "setoneblock <playerName>";
    }

    @Override
    public String getPermission() {
        return "oneblock.setoneblock";
    }

    @Override
    public String getDescription(Locale locale) {
        return Message.COMMAND_DESCRIPTION_SET_ONEBLOCK.getMessage(locale);
    }

    @Override
    public int getMinArgs() {
        return 2;
    }

    @Override
    public int getMaxArgs() {
        return 2;
    }

    @Override
    public void perform(OneBlockModule module, CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            if (!sender.isOp()) {
                Message.NO_PERMISSION.send(sender);
                return;
            }
        }
        Player targetPlayer = Bukkit.getPlayerExact(args[1]);
        if (targetPlayer == null) {
            Message.INVALID_PLAYER.send(sender, args[1]);
            return;
        }

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(targetPlayer);
        Island island = superiorPlayer == null ? null : superiorPlayer.getIsland();

        if (island == null) {
            Message.INVALID_ISLAND.send(sender, targetPlayer.getName());
            return;
        }

        if (!module.getPhasesHandler().canHaveOneBlock(island)) {
            Message.ISLAND_MISSING_BLOCK.send(sender);
            return;
        }

        module.getPlugin().getServer().getScheduler().runTask(module.getPlugin(),
                () -> OneBlockMenu.open(module, targetPlayer, island));
    }

    @Override
    public List<String> tabComplete(OneBlockModule module, CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> players = new ArrayList<>();
            String input = args[1].toLowerCase(Locale.ENGLISH);

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase(Locale.ENGLISH).startsWith(input)) {
                    players.add(onlinePlayer.getName());
                }
            }

            return players;
        }

        return Collections.emptyList();
    }

}
