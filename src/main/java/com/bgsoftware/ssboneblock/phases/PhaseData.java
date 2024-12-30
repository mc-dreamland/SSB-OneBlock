package com.bgsoftware.ssboneblock.phases;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.actions.Action;
import com.bgsoftware.ssboneblock.handler.PhasesHandler;
import com.bgsoftware.ssboneblock.utils.JsonUtils;
import com.bgsoftware.ssboneblock.utils.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class PhaseData {

    private final String name;
    private final Action[] actions;
    private final short nextPhaseCooldown;
    private final int start;
    private final int end;
    private final List<Action> fixedActions; // 用于缓存 fixedCount 不为 -1 的 actions
    private final int totalWeight; // 用于缓存 actions 的总权重

    private static final Random RANDOM = new Random();  // 用于随机数生成

    private PhaseData(String name, Action[] actions, short nextPhaseCooldown, int start, int end) {
        this.name = name;
        this.actions = actions;
        this.nextPhaseCooldown = nextPhaseCooldown;
        this.start = start;
        this.end = end;

        // 初始化缓存
        this.fixedActions = new ArrayList<>();
        int weightSum = 0;
        for (Action action : actions) {
            // 缓存 fixedCount 不为 -1 的 action
            if (action.getFixedCount().first != -1) {
                fixedActions.add(action);
            }
            // 累计权重
            if (action.getWeight()> 0) {
                weightSum += action.getWeight();
            }
        }
        this.totalWeight = weightSum; // 缓存总权重
    }

    public String getName() {
        return name;
    }


    // 根据权重或 fixedCount 返回 Action
    public Action getAction(int block, int loopTimes) {

        // 先检查是否在合法范围内
        if (block < 0 || block > (this.end - this.start) * Math.pow(OneBlockModule.getPlugin().getSettings().phasesLoopMultiple, loopTimes)) {
            return null;
        }

        for (Action action : fixedActions) {
            double expectedBlockStart = action.getFixedCount().first;
            double expectedBlockEnd = action.getFixedCount().second;
            if (block >= expectedBlockStart && block <= expectedBlockEnd) {
                return action;  // 匹配时直接返回
            }
        }

        // 如果没有匹配 fixedCount，则根据权重选择一个 Action
        if (totalWeight > 0) {
            int randomWeight = RANDOM.nextInt(totalWeight) + 1;
            int currentWeightSum = 0;

            for (Action action : actions) {
                if (action.getWeight() == -1) {
                    continue;
                }
                currentWeightSum += action.getWeight();
                if (randomWeight <= currentWeightSum) {
                    return action; // 根据随机权重返回
                }
            }
        }

        return null; // 理论上不应该到这里
    }

    public int getActionsSize() {
        return actions.length;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public short getNextPhaseCooldown() {
        return nextPhaseCooldown;
    }

    public static Optional<PhaseData> fromJson(JsonObject jsonObject, PhasesHandler phasesManager, String fileName) {
        JsonArray actionsArray = jsonObject.getAsJsonArray("actions");

        if (actionsArray == null)
            throw new IllegalArgumentException("File is missing the key \"actions\"!");

        Action[] actions = JsonUtils.getActionsArray(actionsArray, phasesManager, fileName);

        String name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : fileName.split("\\.")[0];
        int start = 0;
        int end = 0;
        for (Pair<String, Integer> phase : OneBlockModule.getPlugin().getSettings().phases) {
            if (!fileName.equals(phase.first)) {
                start = phase.second + 1;
                continue;
            }
            end = phase.second;
            break;
        }


        return actions.length == 0 ? Optional.empty() :
                Optional.of(new PhaseData(name, actions, jsonObject.get("next-upgrade-cooldown").getAsShort(), start, end));
    }

}
