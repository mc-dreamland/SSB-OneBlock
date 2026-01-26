package com.bgsoftware.ssboneblock.phases;

public final class IslandPhaseData {

    private final int phaseLevel;
    private final int phaseBlock;
    private final int phaseLoopTimes;
    private final java.util.Map<String, OneBlockLocation> oneBlockLocations;

    public IslandPhaseData(int phaseLevel, int phaseBlock, int phaseLoopTimes) {
        this(phaseLevel, phaseBlock, phaseLoopTimes, java.util.Collections.emptyMap());
    }

    public IslandPhaseData(int phaseLevel, int phaseBlock, int phaseLoopTimes,
                           java.util.Map<String, OneBlockLocation> oneBlockLocations) {
        this.phaseLevel = phaseLevel;
        this.phaseBlock = phaseBlock;
        this.phaseLoopTimes = phaseLoopTimes;
        this.oneBlockLocations = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(oneBlockLocations));
    }

    public int getPhaseLevel() {
        return phaseLevel;
    }

    public int getPhaseLoopTimes() {
        return phaseLoopTimes;
    }

    public int getPhaseBlock() {
        return phaseBlock;
    }

    public java.util.Map<String, OneBlockLocation> getOneBlockLocations() {
        return oneBlockLocations;
    }

    public IslandPhaseData withOneBlockLocation(String key, OneBlockLocation location) {
        java.util.Map<String, OneBlockLocation> updated = new java.util.HashMap<>(oneBlockLocations);
        if (location == null) {
            updated.remove(key);
        } else {
            updated.put(key, location);
        }
        return new IslandPhaseData(phaseLevel, phaseBlock, phaseLoopTimes, updated);
    }

    public IslandPhaseData nextBlock() {
        return new IslandPhaseData(phaseLevel, phaseBlock + 1, phaseLoopTimes, oneBlockLocations);
    }

    public IslandPhaseData nextPhase() {
        return new IslandPhaseData(phaseLevel + 1, 0, phaseLoopTimes, oneBlockLocations);
    }

    public IslandPhaseData nextPhaseLoop() {
        return new IslandPhaseData(0, 0, phaseLoopTimes + 1, oneBlockLocations);
    }

    public static final class OneBlockLocation {

        private final int x;
        private final int y;
        private final int z;

        public OneBlockLocation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

    }

}
