package com.bgsoftware.ssboneblock.utils;


import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class NMSAdapter {

    public boolean isLegacy() {
        return false;
    }

    public SimpleCommandMap getCommandMap() {
        return ((CraftServer) Bukkit.getServer()).getCommandMap();
    }


    public void setChestName(Location chest, String name) {
        World bukkitWorld = chest.getWorld();

        if (bukkitWorld == null)
            throw new IllegalArgumentException("Cannot set name of chest in null world.");

        ServerLevel serverLevel = ((CraftWorld) bukkitWorld).getHandle();
        BlockPos blockPos = new BlockPos(chest.getBlockX(), chest.getBlockY(), chest.getBlockZ());
        BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);

        if (blockEntity instanceof BaseContainerBlockEntity chestBlockEntity)
            chestBlockEntity.name = CraftChatMessage.fromString(name)[0];
    }

    public void setBlock(Location location, Material type, byte data, String nbt) {
        World bukkitWorld = location.getWorld();

        if (bukkitWorld == null)
            throw new IllegalArgumentException("Cannot set block in a null world.");

        ServerLevel serverLevel = ((CraftWorld) bukkitWorld).getHandle();
        BlockPos blockPos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        if (nbt == null) {
            serverLevel.removeBlockEntity(blockPos);
            location.getBlock().setType(type);
        } else try {
            BlockState blockState = setBlockWithNBT(serverLevel, blockPos, nbt);
            if (blockState != null)
                serverLevel.updateNeighborsAt(blockPos, blockState.getBlock());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void sendBlockUpdate(Player bukkitPlayer, Location location) {
        World bukkitWorld = location.getWorld();

        if (bukkitWorld == null || !bukkitWorld.equals(bukkitPlayer.getWorld()))
            return;

        ServerLevel serverLevel = ((CraftWorld) bukkitWorld).getHandle();
        BlockPos blockPos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ServerPlayer serverPlayer = ((CraftPlayer) bukkitPlayer).getHandle();

        serverPlayer.connection.send(new ClientboundBlockUpdatePacket(serverLevel, blockPos));
    }

    public BlockState setBlockWithNBT(ServerLevel serverLevel, BlockPos blockPos, String nbt) throws Exception {
        BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(
                serverLevel.holderLookup(Registries.BLOCK), new StringReader(nbt), true);
        BlockInput blockInput = new BlockInput(blockResult.blockState(), blockResult.properties().keySet(),
                blockResult.nbt());

        BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
        Clearable.tryClear(blockEntity);

        blockInput.place(serverLevel, blockPos, 2);

        return blockInput.getState();
    }

    public org.bukkit.entity.Entity spawnEntityFromNbt(org.bukkit.entity.EntityType entityType, Location location, String nbt) {
        try {
            CompoundTag compoundTag = CompoundTagArgument.compoundTag().parse(new StringReader(nbt));
            EntityType<?> nmsEntityType = convertBukkitEntityType(entityType);
            compoundTag.putString("id", EntityType.getKey(nmsEntityType).toString());

            ServerLevel serverLevel = ((CraftWorld) location.getWorld()).getHandle();

            Entity entity = loadEntity(compoundTag, serverLevel, location);

            if (entity != null) {
                addEntity(entity, serverLevel);
                return entity.getBukkitEntity();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }


    public EntityType<?> convertBukkitEntityType(org.bukkit.entity.EntityType entityType) {
        return CraftEntityType.bukkitToMinecraft(entityType);
    }

    public Entity loadEntity(CompoundTag compoundTag, ServerLevel serverLevel, Location location) {
        return EntityType.loadEntityRecursive(compoundTag, serverLevel, x -> {
            x.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            x.spawnReason = CreatureSpawnEvent.SpawnReason.CUSTOM;
            return x;
        });
    }

    public void addEntity(Entity entity, ServerLevel serverLevel) {
        serverLevel.tryAddFreshEntityWithPassengers(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
    }


    public org.bukkit.inventory.ItemStack applyNBTToItem(org.bukkit.inventory.ItemStack bukkitItem, String nbt) {
        try {
            ItemStack nmsItem = CraftItemStack.asNMSCopy(bukkitItem);
            applyNBTToItem(nmsItem, nbt);
            bukkitItem = CraftItemStack.asBukkitCopy(nmsItem);
        } catch (Exception error) {
            error.printStackTrace();
        }

        return bukkitItem;
    }

    public void applyNBTToItem(ItemStack itemStack, String nbt) throws Exception {
        ItemParser itemParser = new ItemParser(CraftRegistry.getMinecraftRegistry());
        ItemParser.ItemResult itemResult = itemParser.parse(new StringReader(nbt));
        DataComponentMap components = itemResult.components();
        itemStack.applyComponents(components);
    }

    public void simulateToolBreak(Player bukkitPlayer, org.bukkit.block.Block bukkitBlock) {
        ServerPlayer serverPlayer = ((CraftPlayer) bukkitPlayer).getHandle();

        ItemStack itemStack = serverPlayer.getMainHandItem();

        ServerLevel serverLevel = ((CraftWorld) bukkitBlock.getWorld()).getHandle();
        BlockPos blockPos = new BlockPos(bukkitBlock.getX(), bukkitBlock.getY(), bukkitBlock.getZ());
        BlockState blockState = serverLevel.getBlockState(blockPos);

        itemStack.mineBlock(serverLevel, blockState, blockPos, serverPlayer);
    }

}

