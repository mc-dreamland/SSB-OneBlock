package com.bgsoftware.ssboneblock.actions;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.utils.Pair;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.BlockOffset;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Location;

import javax.annotation.Nullable;

public abstract class Action {

    protected static final OneBlockModule plugin = OneBlockModule.getPlugin();

    @Nullable
    protected final BlockOffset offsetPosition;
    protected int weight;
    protected Pair<Integer, Integer> fixedCount;

    protected Action(@Nullable BlockOffset offsetPosition){
        this.offsetPosition = offsetPosition;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public Pair<Integer, Integer> getFixedCount() {
        return fixedCount;
    }

    public void setFixedCount(Pair<Integer, Integer> fixedCount) {
        this.fixedCount = fixedCount;
    }

    public abstract void run(Location location, Island island, @Nullable SuperiorPlayer superiorPlayer);

}
