package com.bgsoftware.ssboneblock.commands.commands;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.commands.ICommand;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CmdUnlockOneBlock implements ICommand {

    @Override
    public String getLabel() {
        return "unlockoneblock";
    }

    @Override
    public String getUsage(Locale locale) {
        return "unlockoneblock <playerName> [dimension] [duration]";
    }

    @Override
    public String getPermission() {
        return "oneblock.unlockoneblock";
    }

    @Override
    public String getDescription(Locale locale) {
        return Message.COMMAND_DESCRIPTION_UNLOCK_ONEBLOCK.getMessage(locale);
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 4;
    }

    @Override
    public void perform(OneBlockModule module, CommandSender sender, String[] args) {

        // 权限判断：控制台始终允许，玩家需 OP 或权限
        if (sender instanceof Player player) {
            if (!player.isOp() && !player.hasPermission(getPermission())) {
                Message.NO_PERMISSION.send(sender);
                return;
            }
        }

        // 玩家名
        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null) {
            Message.INVALID_PLAYER.send(sender, targetName);
            return;
        }

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(targetPlayer);
        Island island = superiorPlayer == null ? null : superiorPlayer.getIsland();

        if (island == null) {
            Message.INVALID_ISLAND.send(sender, targetName);
            return;
        }

        if (!module.getPhasesHandler().canHaveOneBlock(island)) {
            Message.ISLAND_MISSING_BLOCK.send(sender);
            return;
        }

        // 维度：默认主世界
        String dimensionKey = World.Environment.NORMAL.name();
        if (args.length >= 3 && !args[2].isEmpty()) {
            dimensionKey = args[2].toUpperCase();
        }

        if (!dimensionKey.equals("NORMAL") && !dimensionKey.equals("NETHER") && !dimensionKey.equals("THE_END")) {
            sender.sendMessage("§c维度不存在，仅支持: NORMAL, NETHER, THE_END");
            return;
        }

        // 时间：默认 0（永久）
        long expiresAt = 0L;
        if (args.length >= 4) {
            Long duration = parseDuration(args[3]);
            if (duration == null) {
                Message.INVALID_DURATION.send(sender, args[3]);
                return;
            }
            expiresAt = System.currentTimeMillis() + duration;
        }

        module.getOneBlockUnlocksHandler()
                .unlockOneBlock(island, dimensionKey, expiresAt);

        if (expiresAt > 0) {
            Message.ONEBLOCK_UNLOCK_SUCCESS_TIMED.send(sender, dimensionKey, args.length >= 4 ? args[3] : "0");
        } else {
            Message.ONEBLOCK_UNLOCK_SUCCESS.send(sender, dimensionKey);
        }
    }

    @Override
    public List<String> tabComplete(OneBlockModule module, CommandSender sender, String[] args) {
        return Collections.emptyList();
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
