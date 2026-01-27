package com.bgsoftware.ssboneblock.phases;

import lombok.Data;

import java.util.*;

@Data
public final class IslandPhaseData {

    private final int phaseLevel;
    private final int phaseBlock;
    private final int phaseLoopTimes;
    private final Map<String, List<OneBlockSlotData>> unlocks;
    private final Map<String, List<OneBlockSlotData>> apiUnlocks;

    public IslandPhaseData(int phaseLevel, int phaseBlock, int phaseLoopTimes) {
        this(phaseLevel, phaseBlock, phaseLoopTimes, Collections.emptyMap(), Collections.emptyMap());
    }

    public IslandPhaseData(int phaseLevel, int phaseBlock, int phaseLoopTimes,
                           Map<String, List<OneBlockSlotData>> unlocks,
                           Map<String, List<OneBlockSlotData>> apiUnlocks) {
        this.phaseLevel = phaseLevel;
        this.phaseBlock = phaseBlock;
        this.phaseLoopTimes = phaseLoopTimes;
        this.unlocks = copySlotsMap(unlocks);
        this.apiUnlocks = copySlotsMap(apiUnlocks);
    }

    public IslandPhaseData withUnlockLocation(String dimensionKey, int index, OneBlockLocation location, boolean api) {
        Map<String, List<OneBlockSlotData>> updatedUnlocks = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : unlocks.entrySet()) {
            updatedUnlocks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        Map<String, List<OneBlockSlotData>> updatedApiUnlocks = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : apiUnlocks.entrySet()) {
            updatedApiUnlocks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        Map<String, List<OneBlockSlotData>> targetMap = api ? updatedApiUnlocks : updatedUnlocks;
        List<OneBlockSlotData> slots = targetMap.computeIfAbsent(dimensionKey.toUpperCase(Locale.ENGLISH),
                k -> new ArrayList<>());
        while (slots.size() <= index) {
            slots.add(null);
        }
        OneBlockSlotData existing = slots.get(index);
        Long expiresAt = existing == null ? null : existing.getExpiresAt();
        slots.set(index, new OneBlockSlotData(location, expiresAt));

        return new IslandPhaseData(phaseLevel, phaseBlock, phaseLoopTimes, updatedUnlocks, updatedApiUnlocks);
    }

    public IslandPhaseData addUnlock(String dimensionKey, Long expiresAt) {
        Map<String, List<OneBlockSlotData>> updatedUnlocks = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : unlocks.entrySet()) {
            updatedUnlocks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        Map<String, List<OneBlockSlotData>> updatedApiUnlocks = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : apiUnlocks.entrySet()) {
            updatedApiUnlocks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        List<OneBlockSlotData> slots = updatedUnlocks.computeIfAbsent(dimensionKey.toUpperCase(Locale.ENGLISH),
                k -> new ArrayList<>());
        slots.add(new OneBlockSlotData(null, expiresAt));

        return new IslandPhaseData(phaseLevel, phaseBlock, phaseLoopTimes, updatedUnlocks, updatedApiUnlocks);
    }

    public IslandPhaseData withApiUnlockCount(String dimensionKey, int count) {
        if (count < 0)
            count = 0;

        Map<String, List<OneBlockSlotData>> updatedUnlocks = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : unlocks.entrySet()) {
            updatedUnlocks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        Map<String, List<OneBlockSlotData>> updatedApiUnlocks = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : apiUnlocks.entrySet()) {
            updatedApiUnlocks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        String key = dimensionKey.toUpperCase(Locale.ENGLISH);
        List<OneBlockSlotData> slots = updatedApiUnlocks.computeIfAbsent(key, k -> new ArrayList<>());
        while (slots.size() > count) {
            slots.remove(slots.size() - 1);
        }
        while (slots.size() < count) {
            slots.add(new OneBlockSlotData(null, null));
        }

        if (slots.isEmpty()) {
            updatedApiUnlocks.remove(key);
        }

        return new IslandPhaseData(phaseLevel, phaseBlock, phaseLoopTimes, updatedUnlocks, updatedApiUnlocks);
    }

    public IslandPhaseData nextBlock() {
        return new IslandPhaseData(phaseLevel, phaseBlock + 1, phaseLoopTimes, unlocks, apiUnlocks);
    }

    public IslandPhaseData nextPhase() {
        return new IslandPhaseData(phaseLevel + 1, 0, phaseLoopTimes, unlocks, apiUnlocks);
    }

    public IslandPhaseData nextPhaseLoop() {
        return new IslandPhaseData(0, 0, phaseLoopTimes + 1, unlocks, apiUnlocks);
    }

    private static Map<String, List<OneBlockSlotData>> copySlotsMap(
            Map<String, List<OneBlockSlotData>> source) {
        if (source == null || source.isEmpty())
            return Collections.emptyMap();
        Map<String, List<OneBlockSlotData>> copy = new HashMap<>();
        for (Map.Entry<String, List<OneBlockSlotData>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    @Data
    public static final class OneBlockLocation {

        private final double x;
        private final double y;
        private final double z;

        public OneBlockLocation(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

    }

    @Data
    public static final class OneBlockSlotData {

        private final OneBlockLocation location;
        private final Long expiresAt;

        public OneBlockSlotData(OneBlockLocation location, Long expiresAt) {
            this.location = location;
            this.expiresAt = expiresAt;
        }

    }

}
