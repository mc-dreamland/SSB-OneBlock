package com.bgsoftware.ssboneblock.phases;

public final class IslandPhaseData {

    private final int phaseLevel;
    private final int phaseBlock;
    private final int phaseLoopTimes;

    public IslandPhaseData(int phaseLevel, int phaseBlock, int phaseLoopTimes) {
        this.phaseLevel = phaseLevel;
        this.phaseBlock = phaseBlock;
        this.phaseLoopTimes = phaseLoopTimes;
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

    public IslandPhaseData nextBlock() {
        return new IslandPhaseData(phaseLevel, phaseBlock + 1, phaseLoopTimes);
    }

    public IslandPhaseData nextPhase() {
        return new IslandPhaseData(phaseLevel + 1, 0, phaseLoopTimes);
    }

    public IslandPhaseData nextPhaseLoop() {
        return new IslandPhaseData(0, 0, phaseLoopTimes + 1);
    }

}
