package com.bgsoftware.ssboneblock.listeners;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.task.NextPhaseTimer;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class BlocksListener implements Listener {

    private final OneBlockModule module;

    public BlocksListener(OneBlockModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOneBlockBreak(BlockBreakEvent e) {
        if (e.getClass().equals(FakeBlockBreakEvent.class))
            return;

        Player player = e.getPlayer();
        Block block = e.getBlock();
        Location blockLocation = block.getLocation();
        boolean[] matchedOneBlock = {false};

        WorldUtils.lookupOneBlock(blockLocation, (oneBlockLocation, island) -> {
            matchedOneBlock[0] = true;
            e.setCancelled(true);

            FakeBlockBreakEvent fakeEvent = new FakeBlockBreakEvent(e.getBlock(), e.getPlayer());
            Bukkit.getPluginManager().callEvent(fakeEvent);

            if (fakeEvent.isCancelled())
                return;

            boolean shouldDropItems;
            try {
                shouldDropItems = fakeEvent.isDropItems();
            } catch (Throwable error) {
                shouldDropItems = false;
            }

            ItemStack inHandItem = e.getPlayer().getItemInHand();
            blockLocation.add(0, 0.75, 0);
            World blockWorld = block.getWorld();

            if (shouldDropItems) {
                Collection<ItemStack> drops = block.getDrops(inHandItem);
                BlockState blockState = block.getState();
                boolean dropNaturally = module.getSettings().dropNaturally;

                if (blockState instanceof InventoryHolder &&
                        WorldUtils.shouldDropInventory((InventoryHolder) blockState)) {
                    Inventory inventory = ((InventoryHolder) blockState).getInventory();
                    Collections.addAll(drops, inventory.getContents());
                    inventory.clear();
                }

                drops.forEach(itemStack -> {
                    if (itemStack != null && itemStack.getType() != Material.AIR && itemStack.getAmount() > 0)
                        if (dropNaturally) {
                            blockWorld.dropItemNaturally(blockLocation, itemStack);
                        } else {
                            Item item = blockWorld.spawn(blockLocation, Item.class);
                            item.setItemStack(itemStack);
                            item.setVelocity(new Vector(0, 0, 0));
                        }
                });
            }

            if (e.getExpToDrop() > 0) {
                ExperienceOrb orb = blockWorld.spawn(blockLocation, ExperienceOrb.class);
                orb.setExperience(e.getExpToDrop());
            }

            if (inHandItem != null && inHandItem.getType() != Material.AIR)
                module.getNMSAdapter().simulateToolBreak(e.getPlayer(), e.getBlock());

            SuperiorPlayer superiorPlayer = module.getPlugin().getPlayers().getSuperiorPlayer(e.getPlayer());
            block.setType(Material.AIR);
            module.getPhasesHandler().runNextAction(island, superiorPlayer, oneBlockLocation);


            if (player.getLocation().getBlock().equals(block)) {
                double playerY = player.getLocation().getY();
                double blockTopY = block.getY() + 1;

                if (playerY < blockTopY) {
                    double y = 1 - (playerY - block.getY());
                    player.teleport(player.getLocation().add(0, y, 0));
                    player.setVelocity(new Vector(0, 0, 0));
                }
            }
        });

        if (!matchedOneBlock[0])
            logNonOneBlockBreak(player, blockLocation, block.getType());

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOneBlockChange(EntityChangeBlockEvent e) {
        if (module.getSettings().gravity)
            return;

        Location blockLocation = e.getBlock().getLocation();

        WorldUtils.lookupOneBlock(blockLocation, (oneBlockLocation, island) ->
                e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewFallingBlock(EntitySpawnEvent e) {
        if (e.getEntityType() != EntityType.FALLING_BLOCK)
            return;

        Location blockLocation = new Location(e.getLocation().getWorld(), e.getLocation().getBlockX(),
                e.getLocation().getBlockY(), e.getLocation().getBlockZ());

        WorldUtils.lookupOneBlock(blockLocation, (oneBlockLocation, island) -> {
            if (module.getSettings().gravity)
                Bukkit.getScheduler().runTaskLater(module.getPlugin(), () ->
                        module.getPhasesHandler().runNextAction(island, null, oneBlockLocation), 20L);
            else
                e.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOneBlockBurn(BlockBurnEvent e) {
        WorldUtils.lookupOneBlock(e.getBlock().getLocation(), (oneBlockLocation, island) ->
                Bukkit.getScheduler().runTaskLater(module.getPlugin(), () ->
                        module.getPhasesHandler().runNextAction(island, null, oneBlockLocation), 20L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        WorldUtils.lookupOneBlock(event.getChunk(), this::normalizeLoadedOneBlock);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        onPistonMoveInternal(event.getBlock(), event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        onPistonMoveInternal(event.getBlock(), event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        WorldUtils.lookupOneBlockInIsland(e.getEntity().getLocation(), (oneBlockLocation, island) -> {
            Player sourcePlayer = null;
            if (e.getEntity() instanceof TNTPrimed) {
                Entity sourceEntity = ((TNTPrimed) e.getEntity()).getSource();
                if (sourceEntity instanceof Player)
                    sourcePlayer = (Player) sourceEntity;
            }

            SuperiorPlayer superiorPlayer = sourcePlayer == null ? null :
                    module.getPlugin().getPlayers().getSuperiorPlayer(sourcePlayer);

            for (Block block : e.blockList()) {
                if (block.getLocation().equals(oneBlockLocation)) {
                    Bukkit.getScheduler().runTaskLater(module.getPlugin(), () ->
                            module.getPhasesHandler().runNextAction(island, superiorPlayer, oneBlockLocation), 1L);
                    break;
                }
            }
        });
    }

    private void onPistonMoveInternal(Block pistonBlock, List<Block> blockList, Cancellable event) {
        if (module.getSettings().pistonsInteraction)
            return;

        WorldUtils.lookupOneBlockInIsland(pistonBlock.getLocation(), (oneBlockLocation, island) -> {
            for (Block block : blockList) {
                if (block.getLocation().equals(oneBlockLocation)) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    private void normalizeLoadedOneBlock(Location oneBlockLocation, Island island) {
        if (NextPhaseTimer.getTimer(island) != null)
            return;

        if (oneBlockLocation.getBlock().getType() == Material.BEDROCK) {
            oneBlockLocation.getBlock().setType(Material.COBBLESTONE);
        }
    }

    private void logNonOneBlockBreak(Player player, Location blockLocation, Material blockType) {
        if (!player.isOp())
            return;

        Island island = module.getPlugin().getGrid().getIslandAt(blockLocation);
        if (island == null || !module.getPhasesHandler().canHaveOneBlock(island))
            return;

        String dimensionKey = WorldUtils.getDimensionKey(blockLocation);
        List<Location> configuredLocations = WorldUtils.getOneBlockLocations(island, dimensionKey);

        OneBlockModule.log("[debug] Non-oneblock break by OP " + player.getName() +
                " at " + formatLocation(blockLocation) +
                ", type=" + blockType +
                ", island=" + island.getUniqueId() +
                ", dimension=" + dimensionKey +
                ", cooldown=" + (NextPhaseTimer.getTimer(island) != null) +
                ", configured=" + formatLocations(configuredLocations));
    }

    private static String formatLocations(List<Location> locations) {
        if (locations.isEmpty())
            return "[]";

        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < locations.size(); index++) {
            if (index > 0)
                builder.append(", ");
            builder.append(formatLocation(locations.get(index)));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String formatLocation(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "null" : world.getName();
        return worldName + '(' +
                location.getBlockX() + ", " +
                location.getBlockY() + ", " +
                location.getBlockZ() + ')';
    }

    private static class FakeBlockBreakEvent extends BlockBreakEvent {

        FakeBlockBreakEvent(Block block, Player player) {
            super(block, player);
        }

    }

}
