package com.bgsoftware.ssboneblock.listeners;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.task.DroppedItemsCooldownTimer;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class BlocksListener implements Listener {

    private final OneBlockModule module;
    private final Map<DelayedBlockUpdateKey, Long> delayedBlockUpdates = new ConcurrentHashMap<>();
    private final AtomicLong delayedBlockUpdateSequence = new AtomicLong();

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

            if (NextPhaseTimer.getTimer(island) != null || DroppedItemsCooldownTimer.getTimer(island) != null) {
                return;
            }

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
            if (hasTooManyDroppedItems(blockWorld)) {
                DroppedItemsCooldownTimer.start(island, oneBlockLocation, superiorPlayer);
            } else {
                module.getPhasesHandler().runNextAction(island, superiorPlayer, oneBlockLocation);
            }
            scheduleDelayedBlockUpdates(player, oneBlockLocation);


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
            NextPhaseTimer activeTimer = NextPhaseTimer.getTimer(island);
            DroppedItemsCooldownTimer droppedItemsTimer = DroppedItemsCooldownTimer.getTimer(island);
            Player sourcePlayer = null;
            if (e.getEntity() instanceof TNTPrimed) {
                Entity sourceEntity = ((TNTPrimed) e.getEntity()).getSource();
                if (sourceEntity instanceof Player)
                    sourcePlayer = (Player) sourceEntity;
            }

            SuperiorPlayer superiorPlayer = sourcePlayer == null ? null :
                    module.getPlugin().getPlayers().getSuperiorPlayer(sourcePlayer);

            Iterator<Block> iterator = e.blockList().iterator();
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getLocation().equals(oneBlockLocation)) {
                    if (activeTimer == null && droppedItemsTimer == null) {
                        Bukkit.getScheduler().runTaskLater(module.getPlugin(), () ->
                                module.getPhasesHandler().runNextAction(island, superiorPlayer, oneBlockLocation), 1L);
                    } else {
                        iterator.remove();
                    }
                    break;
                }
            }
        });
    }

    private void onPistonMoveInternal(Block pistonBlock, List<Block> blockList, Cancellable event) {
        WorldUtils.lookupOneBlockInIsland(pistonBlock.getLocation(), (oneBlockLocation, island) -> {
            if (module.getSettings().pistonsInteraction && NextPhaseTimer.getTimer(island) == null &&
                    DroppedItemsCooldownTimer.getTimer(island) == null)
                return;

            for (Block block : blockList) {
                if (block.getLocation().equals(oneBlockLocation)) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    private void scheduleDelayedBlockUpdates(Player player, Location oneBlockLocation) {
        DelayedBlockUpdateKey key = DelayedBlockUpdateKey.of(player, oneBlockLocation);
        if (key == null)
            return;

        long sequence = delayedBlockUpdateSequence.incrementAndGet();
        delayedBlockUpdates.put(key, sequence);

        scheduleDelayedBlockUpdate(player.getUniqueId(), key, sequence, 40L);
        scheduleDelayedBlockUpdate(player.getUniqueId(), key, sequence, 100L);
    }

    private void scheduleDelayedBlockUpdate(UUID playerId, DelayedBlockUpdateKey key, long sequence, long delay) {
        Bukkit.getScheduler().runTaskLater(module.getPlugin(), () -> {
            Long latestSequence = delayedBlockUpdates.get(key);
            if (latestSequence == null || latestSequence != sequence)
                return;

            Player player = Bukkit.getPlayer(playerId);
            World world = Bukkit.getWorld(key.worldId);
            if (player != null && world != null)
                module.getNMSAdapter().sendBlockUpdate(player, key.toLocation(world));

            if (delay >= 100L)
                delayedBlockUpdates.remove(key, sequence);
        }, delay);
    }

    private void normalizeLoadedOneBlock(Location oneBlockLocation, Island island) {
        if (NextPhaseTimer.getTimer(island) != null || DroppedItemsCooldownTimer.getTimer(island) != null)
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
                ", cooldown=" + (NextPhaseTimer.getTimer(island) != null ||
                        DroppedItemsCooldownTimer.getTimer(island) != null) +
                ", configured=" + formatLocations(configuredLocations));
    }

    private boolean hasTooManyDroppedItems(World world) {
        int limit = module.getSettings().droppedItemEntityLimit;
        return limit > 0 && world.getEntitiesByClass(Item.class).size() > limit;
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

    private static final class DelayedBlockUpdateKey {

        private final UUID playerId;
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private DelayedBlockUpdateKey(UUID playerId, UUID worldId, int x, int y, int z) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static DelayedBlockUpdateKey of(Player player, Location location) {
            World world = location.getWorld();
            if (world == null)
                return null;

            return new DelayedBlockUpdateKey(player.getUniqueId(), world.getUID(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private Location toLocation(World world) {
            return new Location(world, x, y, z);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;

            if (!(object instanceof DelayedBlockUpdateKey))
                return false;

            DelayedBlockUpdateKey other = (DelayedBlockUpdateKey) object;
            return x == other.x && y == other.y && z == other.z &&
                    playerId.equals(other.playerId) && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, worldId, x, y, z);
        }

    }

    private static class FakeBlockBreakEvent extends BlockBreakEvent {

        FakeBlockBreakEvent(Block block, Player player) {
            super(block, player);
        }

    }

}
