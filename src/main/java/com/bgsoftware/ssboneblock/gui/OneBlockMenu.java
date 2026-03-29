package com.bgsoftware.ssboneblock.gui;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.lang.Message;
import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import redempt.redlib.inventorygui.InventoryGUI;
import redempt.redlib.inventorygui.ItemButton;
import redempt.redlib.inventorygui.PaginationPanel;
import redempt.redlib.itemutils.ItemBuilder;

import java.util.*;

public final class OneBlockMenu {

    private OneBlockMenu() {
    }

    public static void open(OneBlockModule module, Player player, Island island) {
        InventoryGUI gui = new InventoryGUI(54, color("&6&l单方块管理"));
        gui.setDestroyOnClose(true);
        PaginationPanel panel = new PaginationPanel(gui, InventoryGUI.FILLER);
        panel.addSlots(0, 45);

        List<SlotEntry> slots = buildSlots(module, island);

        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = module.getOneBlockUnlocksHandler().getUnlocks(island);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks = islandPhaseData == null ?
                Collections.emptyMap() : islandPhaseData.getApiUnlocks();

        for (SlotEntry slot : slots) {
            panel.addPagedButton(createSlotButton(module, player, island, slot, unlocks, apiUnlocks));
        }

        ItemButton prev = ItemButton.create(new ItemBuilder(Material.ARROW)
                .setName(color("&e上一页"))
                .toItemStack(), event -> panel.prevPage());
        ItemButton next = ItemButton.create(new ItemBuilder(Material.ARROW)
                .setName(color("&e下一页"))
                .toItemStack(), event -> panel.nextPage());

        gui.addButton(prev, 45);
        gui.addButton(next, 53);

        gui.open(player);
    }

    private static ItemButton createSlotButton(OneBlockModule module, Player player, Island island, SlotEntry slot,
                                               Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks,
                                               Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks) {
        String dimensionKey = slot.dimension;
        IslandPhaseData.OneBlockLocation resolvedLocation = resolveLocation(dimensionKey, slot.index, slot.api, unlocks, apiUnlocks);

        ItemStack item = buildSlotItem(dimensionKey, resolvedLocation, slot.expiresAt,
                canEdit(player, dimensionKey));

        return ItemButton.create(item, event -> {
            event.setCancelled(true);

            if (!canEdit(player, dimensionKey))
                return;

            org.bukkit.block.Block block = player.getLocation().clone().subtract(0, 1, 0).getBlock();
            Location target = block.getLocation().add(0.5, 0.5, 0.5);
            if (isDuplicateLocation(unlocks, apiUnlocks, dimensionKey, slot.index, slot.api, target)) {
                Message.ONEBLOCK_DUPLICATE_LOCATION.send(player, block.getX(), block.getY(), block.getZ());
                return;
            }

            IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
            IslandPhaseData.OneBlockLocation updated = new IslandPhaseData.OneBlockLocation(
                    target.getX(), target.getY(), target.getZ());
            module.getPhasesHandler().getDataStore().setPhaseData(island,
                    islandPhaseData.withUnlockLocation(dimensionKey, slot.index, updated, slot.api));


            Message.SET_ONEBLOCK_SUCCESS.send(player, dimensionKey, target.getX(), target.getY(), target.getZ());
            open(module, player, island);
        });
    }

    private static List<SlotEntry> buildSlots(OneBlockModule module, Island island) {
        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = module.getOneBlockUnlocksHandler().getUnlocks(island);
        IslandPhaseData islandPhaseData = module.getPhasesHandler().getDataStore().getPhaseData(island, true);
        Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks = islandPhaseData == null ?
                Collections.emptyMap() : islandPhaseData.getApiUnlocks();
        List<SlotEntry> slots = new ArrayList<>();

        Set<String> dimensionsSet = new HashSet<>();
        dimensionsSet.addAll(unlocks.keySet());
        dimensionsSet.addAll(apiUnlocks.keySet());
        List<String> dimensions = new ArrayList<>(dimensionsSet);
        dimensions.sort(String::compareToIgnoreCase);

        for (String dimensionKey : dimensions) {
            List<IslandPhaseData.OneBlockSlotData> unlockList = unlocks.getOrDefault(dimensionKey.toUpperCase(Locale.ENGLISH),
                    Collections.emptyList());
            for (int i = 0; i < unlockList.size(); i++) {
                IslandPhaseData.OneBlockSlotData slot = unlockList.get(i);
                long expiresAt = slot == null || slot.getExpiresAt() == null ? 0L : slot.getExpiresAt();
                slots.add(new SlotEntry(dimensionKey, i, expiresAt, false));
            }

            List<IslandPhaseData.OneBlockSlotData> apiList = apiUnlocks.getOrDefault(dimensionKey.toUpperCase(Locale.ENGLISH),
                    Collections.emptyList());
            for (int i = 0; i < apiList.size(); i++) {
                if (!isVisibleApiSlot(apiList.get(i)))
                    continue;
                slots.add(new SlotEntry(dimensionKey, i, 0L, true));
            }
        }

        return slots;
    }

    private static boolean canEdit(Player player, String dimensionKey) {
        World world = player.getWorld();
        return world != null && world.getEnvironment().name().equalsIgnoreCase(dimensionKey);
    }

    private static boolean isDuplicateLocation(Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks,
                                               Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks,
                                               String dimensionKey, int currentIndex, boolean currentApi, Location target) {
        List<IslandPhaseData.OneBlockSlotData> unlockList = unlocks.get(dimensionKey.toUpperCase(Locale.ENGLISH));
        if (unlockList != null) {
            for (int i = 0; i < unlockList.size(); i++) {
                if (!currentApi && i == currentIndex)
                    continue;
                IslandPhaseData.OneBlockSlotData slot = unlockList.get(i);
                if (slot == null || slot.getLocation() == null)
                    continue;
                if (isSameBlock(slot.getLocation(), target))
                    return true;
            }
        }

        List<IslandPhaseData.OneBlockSlotData> apiList = apiUnlocks.get(dimensionKey.toUpperCase(Locale.ENGLISH));
        if (apiList != null) {
            for (int i = 0; i < apiList.size(); i++) {
                if (currentApi && i == currentIndex)
                    continue;
                IslandPhaseData.OneBlockSlotData slot = apiList.get(i);
                if (slot == null || slot.getLocation() == null)
                    continue;
                if (isSameBlock(slot.getLocation(), target))
                    return true;
            }
        }

        return false;
    }

    private static IslandPhaseData.OneBlockLocation resolveLocation(String dimensionKey, int index, boolean api,
                                                                    Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks,
                                                                    Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks) {
        List<IslandPhaseData.OneBlockSlotData> slots = api ? apiUnlocks.get(dimensionKey.toUpperCase(Locale.ENGLISH))
                : unlocks.get(dimensionKey.toUpperCase(Locale.ENGLISH));
        if (slots != null && index >= 0 && index < slots.size()) {
            IslandPhaseData.OneBlockSlotData slot = slots.get(index);
            return slot == null ? null : slot.getLocation();
        }
        return null;
    }

    private static ItemStack buildSlotItem(String dimensionKey,
                                           IslandPhaseData.OneBlockLocation location, long expiresAt,
                                           boolean editable) {
        Material material = getDisplayMaterial(dimensionKey);
        ItemBuilder builder = new ItemBuilder(material).setName(color("&a单方块"));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7维度: &f" + dimensionKey));
        lore.add(color("&7有效期: &f" + formatRemaining(expiresAt)));
        if (location == null) {
            lore.add(color("&7坐标: &c未设置"));
        } else {
            lore.add(color("&7坐标: &f" + formatDouble(location.getX()) + ", " + formatDouble(location.getY()) + ", " + formatDouble(location.getZ())));
        }
        if (editable) {
            lore.add(color("&e点击设置到脚下位置"));
        } else {
            lore.add(color("&7仅可查看（不在当前维度）"));
        }

        builder.addLore(lore);
        return builder.toItemStack();
    }

    private static Material getDisplayMaterial(String dimensionKey) {
        if ("NETHER".equalsIgnoreCase(dimensionKey)) {
            return Material.NETHERRACK;
        }
        if ("THE_END".equalsIgnoreCase(dimensionKey) || "END".equalsIgnoreCase(dimensionKey)) {
            Material endStone = Material.getMaterial("END_STONE");
            return Objects.requireNonNullElse(endStone, Material.STONE);
        }
        return Material.GRASS_BLOCK;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String formatRemaining(long expiresAt) {
        if (expiresAt <= 0)
            return "永久";

        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0)
            return "0s";

        long seconds = remaining / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0)
            builder.append(days).append('d');
        if (hours > 0)
            builder.append(hours).append('h');
        if (minutes > 0)
            builder.append(minutes).append('m');
        if (seconds > 0 || builder.length() == 0)
            builder.append(seconds).append('s');

        return builder.toString();
    }

    private static boolean isSameBlock(IslandPhaseData.OneBlockLocation location, Location target) {
        return (int) Math.floor(location.getX()) == target.getBlockX()
                && (int) Math.floor(location.getY()) == target.getBlockY()
                && (int) Math.floor(location.getZ()) == target.getBlockZ();
    }

    private static String formatDouble(double value) {
        String text = Double.toString(value);
        return text.endsWith(".0") ? text : text;
    }

    private static boolean isVisibleApiSlot(IslandPhaseData.OneBlockSlotData slot) {
        return slot == null || slot.isActive();
    }

    private static final class SlotEntry {
        private final String dimension;
        private final int index;
        private final long expiresAt;
        private final boolean api;

        private SlotEntry(String dimension, int index, long expiresAt, boolean api) {
            this.dimension = dimension.toUpperCase(Locale.ENGLISH);
            this.index = index;
            this.expiresAt = expiresAt;
            this.api = api;
        }
    }

}
