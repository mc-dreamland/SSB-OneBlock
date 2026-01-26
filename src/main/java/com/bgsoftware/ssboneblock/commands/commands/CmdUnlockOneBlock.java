package com.bgsoftware.ssboneblock.commands.commands;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.commands.ICommand;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CmdUnlockOneBlock implements ICommand {

    @Override
    public String getLabel() {
        return "unlockoneblock";
    }

    @Override
    public String getUsage(java.util.Locale locale) {
        return "unlockoneblock [" + Message.COMMAND_ARGUMENT_DURATION.getMessage(locale) + "]";
    }

    @Override
    public String getPermission() {
        return "oneblock.unlockoneblock";
    }

    @Override
    public String getDescription(java.util.Locale locale) {
        return Message.COMMAND_DESCRIPTION_UNLOCK_ONEBLOCK.getMessage(locale);
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

        String dimensionKey = player.getWorld().getEnvironment().name();
        long expiresAt = 0L;
        if (args.length > 1) {
            Long duration = parseDuration(args[1]);
            if (duration == null) {
                Message.INVALID_DURATION.send(sender, args[1]);
                return;
            }
            expiresAt = System.currentTimeMillis() + duration;
        }

        module.getOneBlockUnlocksHandler().unlockOneBlock(island, dimensionKey, expiresAt);
        if (expiresAt > 0) {
            Message.ONEBLOCK_UNLOCK_SUCCESS_TIMED.send(sender, dimensionKey, args[1]);
        } else {
            Message.ONEBLOCK_UNLOCK_SUCCESS.send(sender, dimensionKey);
        }
    }

    @Override
    public java.util.List<String> tabComplete(OneBlockModule module, CommandSender sender, String[] args) {
        return java.util.Collections.emptyList();
    }

    private Long parseDuration(String raw) {
        if (raw == null || raw.isEmpty())
            return null;

        char lastChar = raw.charAt(raw.length() - 1);
        long multiplier;
        String numberPart;

        if (Character.isLetter(lastChar)) {
            numberPart = raw.substring(0, raw.length() - 1);
            switch (Character.toLowerCase(lastChar)) {
                case 's':
                    multiplier = 1000L;
                    break;
                case 'm':
                    multiplier = 60_000L;
                    break;
                case 'h':
                    multiplier = 3_600_000L;
                    break;
                case 'd':
                    multiplier = 86_400_000L;
                    break;
                default:
                    return null;
            }
        } else {
            numberPart = raw;
            multiplier = 1000L;
        }

        try {
            long value = Long.parseLong(numberPart);
            if (value <= 0)
                return null;
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
