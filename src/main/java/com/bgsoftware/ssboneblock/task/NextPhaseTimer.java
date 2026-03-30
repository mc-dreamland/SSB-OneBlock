package com.bgsoftware.ssboneblock.task;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.factory.HologramFactory;
import com.bgsoftware.ssboneblock.utils.WorldUtils;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.service.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NextPhaseTimer extends BukkitRunnable {

    private static final Map<UUID, NextPhaseTimer> timers = new ConcurrentHashMap<>();
    private static final OneBlockModule module = OneBlockModule.getModule();

    private final List<Hologram> holograms = new LinkedList<>();
    private final Island island;
    private final Location oneBlockLocation;
    private final Set<Location> trackedLocations = new LinkedHashSet<>();
    private Runnable onFinish = () -> {};
    private short time;
    private boolean runFinishCallback = true;

    public NextPhaseTimer(Island island, short time, Location oneBlockLocation) {
        NextPhaseTimer oldTimer = timers.put(island.getUniqueId(), this);
        if (oldTimer != null)
            oldTimer.cancel();

        this.island = island;
        this.time = time;
        this.oneBlockLocation = oneBlockLocation == null ? null : oneBlockLocation.clone();
        trackLocation(oneBlockLocation);

        Location baseLocation = this.oneBlockLocation == null ? WorldUtils.getOneBlock(island) : this.oneBlockLocation;

        for (String name : module.getSettings().timerFormat) {
            Hologram hologram = createHologram(baseLocation, this.holograms.size());
            if (hologram != null) {
                hologram.setHologramName(name.replace("{0}", time + ""));
                this.holograms.add(hologram);
            }
        }

        runTaskTimer(module.getPlugin(), 20L, 20L);
    }

    public void setOnFinish(Runnable onFinish) {
        this.onFinish = onFinish == null ? () -> {} : onFinish;
    }

    public void setRunFinishCallback(boolean runFinishCallback) {
        this.runFinishCallback = runFinishCallback;
    }

    public void trackLocation(Location location) {
        if (location == null)
            return;
        trackedLocations.add(location.clone());
    }

    public Set<Location> getTrackedLocations() {
        Set<Location> copied = new LinkedHashSet<>();
        for (Location trackedLocation : trackedLocations) {
            copied.add(trackedLocation.clone());
        }
        return copied;
    }

    @Override
    public void run() {
        if (time == 0) {
            cancel();
            return;
        }

        int hologramCounter = 0;

        time--;

        Location baseLocation = this.oneBlockLocation;

        ListIterator<Hologram> iterator = this.holograms.listIterator();
        while (iterator.hasNext()) {
            Hologram hologram = iterator.next();

            if (!hologram.getHandle().isValid()) {
                if (baseLocation == null) {
                    baseLocation = WorldUtils.getOneBlock(island);
                }

                hologram = createHologram(baseLocation, hologramCounter);
                iterator.set(hologram);
            }

            String name = module.getSettings().timerFormat.get(hologramCounter);
            hologram.setHologramName(name.replace("{0}", time + ""));

            ++hologramCounter;
        }
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        for (Hologram hologram : holograms)
            hologram.removeHologram();

        timers.remove(island.getUniqueId());

        if (this.runFinishCallback)
            onFinish.run();

        super.cancel();
    }

    public static NextPhaseTimer getTimer(Island island) {
        return timers.get(island.getUniqueId());
    }

    public static void cancelTimers() {
        new HashMap<>(timers).values().forEach(BukkitRunnable::cancel);
    }

    private static Hologram createHologram(Location firstLocation, int index) {
        if (firstLocation == null) {
            return null;
        }
        Location hologramLocation = firstLocation.clone().add(0.5, 2 + (index * 0.3), 0.5);
        return HologramFactory.createHologram(hologramLocation);
    }

}
