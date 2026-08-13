package com.bgsoftware.ssboneblock.task;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.factory.HologramFactory;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.service.hologram.Hologram;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DroppedItemsCooldownTimer extends BukkitRunnable {

    private static final int BASE_COOLDOWN_SECONDS = 5;
    private static final long PENALTY_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(30);

    private static final Map<UUID, DroppedItemsCooldownTimer> timers = new ConcurrentHashMap<>();
    private static final Map<UUID, PenaltyState> penaltyStates = new ConcurrentHashMap<>();
    private static final OneBlockModule module = OneBlockModule.getModule();

    private final List<HologramLine> holograms = new ArrayList<>();
    private final List<String> hologramFormat;
    private final Island island;
    private final Location oneBlockLocation;
    private final Runnable onFinish;
    private int time;
    private boolean runFinishCallback = true;
    private boolean cancelled;

    private DroppedItemsCooldownTimer(Island island, int time, Location oneBlockLocation,
                                      @Nullable SuperiorPlayer superiorPlayer) {
        DroppedItemsCooldownTimer oldTimer = timers.put(island.getUniqueId(), this);
        if (oldTimer != null) {
            oldTimer.setRunFinishCallback(false);
            oldTimer.cancel();
        }

        this.island = island;
        this.time = time;
        this.oneBlockLocation = oneBlockLocation.clone();
        this.hologramFormat = new ArrayList<>(module.getSettings().droppedItemCooldownFormat);
        this.onFinish = () -> module.getPhasesHandler().runNextAction(island, superiorPlayer, this.oneBlockLocation);

        this.oneBlockLocation.getBlock().setType(Material.STONE);
        createHolograms();
        runTaskTimer(module.getPlugin(), 20L, 20L);
    }

    public static void start(Island island, Location oneBlockLocation, @Nullable SuperiorPlayer superiorPlayer) {
        if (getTimer(island) != null)
            return;

        new DroppedItemsCooldownTimer(island, getNextCooldown(island), oneBlockLocation, superiorPlayer);
    }

    @Nullable
    public static DroppedItemsCooldownTimer getTimer(Island island) {
        return timers.get(island.getUniqueId());
    }

    public static void removeIsland(Island island) {
        DroppedItemsCooldownTimer timer = timers.get(island.getUniqueId());
        if (timer != null) {
            timer.setRunFinishCallback(false);
            timer.cancel();
        }
        penaltyStates.remove(island.getUniqueId());
    }

    public static void cancelTimers() {
        new HashMap<>(timers).values().forEach(BukkitRunnable::cancel);
        penaltyStates.clear();
    }

    @Override
    public void run() {
        --time;
        if (time <= 0) {
            cancel();
            return;
        }

        updateHolograms();
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        if (cancelled)
            return;

        cancelled = true;
        for (HologramLine hologramLine : holograms)
            hologramLine.hologram.removeHologram();
        holograms.clear();

        timers.remove(island.getUniqueId(), this);

        boolean shouldRunFinishCallback = this.runFinishCallback;
        this.runFinishCallback = false;
        super.cancel();

        if (shouldRunFinishCallback)
            onFinish.run();
    }

    private void setRunFinishCallback(boolean runFinishCallback) {
        this.runFinishCallback = runFinishCallback;
    }

    private void createHolograms() {
        for (int index = 0; index < hologramFormat.size(); index++) {
            Hologram hologram = createHologram(index);
            if (hologram != null) {
                hologram.setHologramName(hologramFormat.get(index).replace("{0}", String.valueOf(time)));
                holograms.add(new HologramLine(index, hologram));
            }
        }
    }

    private void updateHolograms() {
        for (HologramLine hologramLine : holograms) {
            if (!hologramLine.hologram.getHandle().isValid()) {
                Hologram replacement = createHologram(hologramLine.index);
                if (replacement != null)
                    hologramLine.hologram = replacement;
            }

            if (hologramLine.hologram.getHandle().isValid()) {
                hologramLine.hologram.setHologramName(hologramFormat.get(hologramLine.index)
                        .replace("{0}", String.valueOf(time)));
            }
        }
    }

    @Nullable
    private Hologram createHologram(int index) {
        Location hologramLocation = oneBlockLocation.clone().add(0.5, 2 + (index * 0.3), 0.5);
        return HologramFactory.createHologram(hologramLocation);
    }

    private static int getNextCooldown(Island island) {
        long now = System.nanoTime();
        PenaltyState previousState = penaltyStates.get(island.getUniqueId());
        int cooldown = BASE_COOLDOWN_SECONDS;

        if (previousState != null && now - previousState.triggeredAt < PENALTY_WINDOW_NANOS)
            cooldown = previousState.cooldownSeconds == Integer.MAX_VALUE ?
                    Integer.MAX_VALUE : previousState.cooldownSeconds + 1;

        penaltyStates.put(island.getUniqueId(), new PenaltyState(now, cooldown));
        return cooldown;
    }

    private static final class PenaltyState {

        private final long triggeredAt;
        private final int cooldownSeconds;

        private PenaltyState(long triggeredAt, int cooldownSeconds) {
            this.triggeredAt = triggeredAt;
            this.cooldownSeconds = cooldownSeconds;
        }

    }

    private static final class HologramLine {

        private final int index;
        private Hologram hologram;

        private HologramLine(int index, Hologram hologram) {
            this.index = index;
            this.hologram = hologram;
        }

    }

}
