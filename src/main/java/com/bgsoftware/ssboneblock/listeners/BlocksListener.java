package com.bgsoftware.ssboneblock.listeners;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.task.NextPhaseTimer;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class BlocksListener implements Listener {

    private final OneBlockModule plugin;

    private EventFlags eventFlags = EventFlags.DEFAULT;

    public BlocksListener(OneBlockModule plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTask(plugin.getJavaPlugin(), () -> {
            if (Bukkit.getPluginManager().isPluginEnabled("AdvancedEnchantments")) {
                for (RegisteredListener listener : BlockBreakEvent.getHandlerList().getRegisteredListeners()) {
                    if (listener.getPlugin().getName().equals("AdvancedEnchantments")) {
//                        Bukkit.getLogger().info("Hook into " + listener.getListener() + ", " + listener.getPriority());
                        BlockBreakEvent.getHandlerList().unregister(listener);
                        BlockBreakEvent.getHandlerList().register(new RegisteredListener(listener.getListener(),
                                (li, event) -> {
                                    switch (eventFlags) {
                                        case DEFAULT:
                                        case FAKE_TRIGGERING: {
                                            listener.callEvent(event);
//                                            Bukkit.getLogger().info("Call " + li.getClass().getName());
                                        }
                                    }
                                },
                                listener.getPriority(),
                                listener.getPlugin(),
                                listener.isIgnoringCancelled()
                        ));
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOneBlockBreak(BlockBreakEvent e) {
        if (eventFlags == EventFlags.FAKE_TRIGGERING)
            return;
//        Bukkit.getLogger().info("Call SSBOneBlock BlockBreak");
        Block block = e.getBlock();
        Location blockLocation = block.getLocation();
        Island island = getOneBlockIsland(blockLocation);

        if (island == null) {
            eventFlags = EventFlags.DEFAULT;
            return;
        }

        e.setCancelled(true);

        boolean shouldDropItems;
        try {
            eventFlags = EventFlags.FAKE_TRIGGERING;
//            Bukkit.getLogger().info("Call fake event");
            BlockBreakEvent fakeEvent = new BlockBreakEvent(e.getBlock(), e.getPlayer());
            Bukkit.getPluginManager().callEvent(fakeEvent);

            if (fakeEvent.isCancelled())
                return;

            try {
                shouldDropItems = fakeEvent.isDropItems();
            } catch (Throwable error) {
                shouldDropItems = false;
            }
        } finally {
            eventFlags = EventFlags.POST_FAKE;
//            if (block.isEmpty()) {
//                block.setType(type);
//            }
        }

        Block underBlock = block.getRelative(BlockFace.DOWN);
        boolean barrierPlacement = underBlock.getType() == Material.AIR;

        if (barrierPlacement)
            underBlock.setType(Material.BARRIER);

        ItemStack inHandItem = e.getPlayer().getItemInHand();
        blockLocation.add(0, 1, 0);
        World blockWorld = block.getWorld();

        if (shouldDropItems && !block.isEmpty()) {
            Collection<ItemStack> drops = block.getDrops(inHandItem);
            BlockState blockState = block.getState();
            if (blockState instanceof InventoryHolder && WorldUtils.shouldDropInventory((InventoryHolder) blockState)) {
                Inventory inventory = ((InventoryHolder) blockState).getInventory();
                Collections.addAll(drops, inventory.getContents());
                inventory.clear();
            }

            drops.stream().filter(itemStack -> itemStack != null && itemStack.getType() != Material.AIR)
                    .forEach(itemStack -> blockWorld.dropItemNaturally(blockLocation, itemStack));
        }

        if (e.getExpToDrop() > 0) {
            ExperienceOrb orb = blockWorld.spawn(blockLocation, ExperienceOrb.class);
            orb.setExperience(e.getExpToDrop());
        }

        if (inHandItem != null && inHandItem.getType() != Material.AIR)
            plugin.getNMSAdapter().simulateToolBreak(e.getPlayer(), e.getBlock());

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());
        plugin.getPhasesHandler().runNextAction(island, superiorPlayer);

        if (barrierPlacement)
            underBlock.setType(Material.AIR);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewFallingBlock(EntitySpawnEvent e) {
        if (e.getEntityType() != EntityType.FALLING_BLOCK)
            return;

        Location blockLocation = new Location(e.getLocation().getWorld(), e.getLocation().getBlockX(),
                e.getLocation().getBlockY(), e.getLocation().getBlockZ());

        Island island = getOneBlockIsland(blockLocation);

        if (island == null)
            return;

        Bukkit.getScheduler().runTaskLater(plugin.getJavaPlugin(), () ->
                plugin.getPhasesHandler().runNextAction(island, null), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        Island island = SuperiorSkyblockAPI.getGrid().getIslandAt(chunk);

        if (island == null || NextPhaseTimer.getTimer(island) != null)
            return;

        Location oneBlockLocation = plugin.getSettings().blockOffset.applyToLocation(
                island.getCenter(World.Environment.NORMAL).subtract(0.5, 0, 0.5));

        if (oneBlockLocation.getBlockX() >> 4 != chunk.getX() || oneBlockLocation.getBlockZ() >> 4 != chunk.getZ())
            return;

        if (oneBlockLocation.getBlock().getType() == Material.BEDROCK)
            plugin.getPhasesHandler().runNextAction(island, null);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        onPistonMoveInternal(event.getBlock(), event.getBlocks(), event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        onPistonMoveInternal(event.getBlock(), event.getBlocks(), event);
    }

    private void onPistonMoveInternal(Block pistonBlock, List<Block> blockList, Cancellable event) {
        if (plugin.getSettings().pistonsInteraction)
            return;

        Island island = SuperiorSkyblockAPI.getIslandAt(pistonBlock.getLocation());

        if (island == null || !plugin.getPhasesHandler().canHaveOneBlock(island))
            return;

        Location oneBlockLocation = plugin.getSettings().blockOffset.applyToLocation(
                island.getCenter(World.Environment.NORMAL).subtract(0.5, 0, 0.5));

        for (Block block : blockList) {
            if (block.getLocation().equals(oneBlockLocation)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        Island island = SuperiorSkyblockAPI.getIslandAt(e.getEntity().getLocation());

        if (island == null || !plugin.getPhasesHandler().canHaveOneBlock(island))
            return;

        Location oneBlockLocation = plugin.getSettings().blockOffset.applyToLocation(
                island.getCenter(World.Environment.NORMAL).subtract(0.5, 0, 0.5));

        Player sourcePlayer = null;
        if (e.getEntity() instanceof TNTPrimed) {
            Entity sourceEntity = ((TNTPrimed) e.getEntity()).getSource();
            if (sourceEntity instanceof Player)
                sourcePlayer = (Player) sourceEntity;
        }

        SuperiorPlayer superiorPlayer = sourcePlayer == null ? null : SuperiorSkyblockAPI.getPlayer(sourcePlayer);

        for (Block block : e.blockList()) {
            if (block.getLocation().equals(oneBlockLocation)) {
                Bukkit.getScheduler().runTaskLater(plugin.getJavaPlugin(), () ->
                        plugin.getPhasesHandler().runNextAction(island, superiorPlayer), 1L);
                break;
            }
        }
    }

    private Island getOneBlockIsland(Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);

        if (island == null || !plugin.getPhasesHandler().canHaveOneBlock(island))
            return null;

        Location oneBlockLocation = plugin.getSettings().blockOffset.applyToLocation(
                island.getCenter(World.Environment.NORMAL).subtract(0.5, 0, 0.5));

        return oneBlockLocation.equals(location) ? island : null;
    }

}
