package com.bgsoftware.ssboneblock.utils;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.actions.Action;
import com.bgsoftware.ssboneblock.actions.ActionType;
import com.bgsoftware.ssboneblock.actions.CommandAction;
import com.bgsoftware.ssboneblock.actions.MultiAction;
import com.bgsoftware.ssboneblock.actions.RandomAction;
import com.bgsoftware.ssboneblock.actions.SetBlockAction;
import com.bgsoftware.ssboneblock.actions.SpawnEntityAction;
import com.bgsoftware.ssboneblock.actions.container.ContainerPoll;
import com.bgsoftware.ssboneblock.error.ParsingException;
import com.bgsoftware.ssboneblock.handler.PhasesHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class JsonUtils {

    private static final Gson gson = new GsonBuilder().create();

    private JsonUtils() {

    }

    public static Gson getGson() {
        return gson;
    }

    private static Pair<Integer, Integer> getFixed(JsonElement jsonElement) {

        String s = jsonElement.getAsString();
        String[] split = s.replace(" ", "").split(",");
        if (split.length == 1) {
            return new Pair<>(jsonElement.getAsInt(), jsonElement.getAsInt());
        }
        return new Pair<>(Integer.parseInt(split[0]), Integer.parseInt(split[1]));

    }

    public static Optional<Action> getAction(JsonObject actionObject, PhasesHandler phasesHandler, String fileName) throws ParsingException {
        JsonElement actionElement = actionObject.get("action");

        if (!(actionElement instanceof JsonPrimitive))
            throw new ParsingException("Missing \"action\" section.");

        int weight = actionObject.has("weight") ? actionObject.get("weight").getAsInt() : -1;
        Pair<Integer, Integer> fixedCount = actionObject.has("fixed") ? getFixed(actionObject.get("fixed")) : new Pair<>(-1, -1);

        String action = actionElement.getAsString();
        ActionType actionType;

        try {
            actionType = ActionType.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ParsingException("Invalid action-type \"" + action + "\".");
        }
        Optional<Action> actionOptional;
        switch (actionType) {
            case SET_BLOCK:
                actionOptional = SetBlockAction.fromJson(actionObject, phasesHandler, fileName);
                break;
            case RANDOM:
                actionOptional = RandomAction.fromJson(actionObject, phasesHandler, fileName);
                break;
            case COMMAND:
                actionOptional = CommandAction.fromJson(actionObject);
                break;
            case SPAWN_ENTITY:
                actionOptional = SpawnEntityAction.fromJson(actionObject);
                break;
            default:
                throw new ParsingException("Invalid action-type \"" + action + "\".");
        }
        actionOptional.ifPresent(value -> {
            value.setWeight(weight);
            value.setFixedCount(fixedCount);
        });
        return actionOptional;
    }

    public static Action[] getActionsArray(JsonArray jsonArray, PhasesHandler phasesManager, String fileName) {
        List<Action> actionList = new ArrayList<>();

        for (JsonElement actionElement : jsonArray) {
            JsonObject actionObject = actionElement.getAsJsonObject();
            Action action;

            if (actionObject.has("actions")) {
                List<Action> multipleActions = new ArrayList<>();

                JsonElement actionsElement = actionObject.get("actions");

                if (!(actionsElement instanceof JsonArray))
                    throw new IllegalArgumentException("Section \"actions\" must be a list.");

                for (JsonElement _actionElement : (JsonArray) actionsElement) {
                    getActionSafely(_actionElement.getAsJsonObject(), phasesManager, fileName)
                            .ifPresent(multipleActions::add);
                }
                action = new MultiAction(multipleActions.toArray(new Action[0]));
                int weight = actionObject.has("weight") ? actionObject.get("weight").getAsInt() : -1;
                Pair<Integer, Integer> fixedCount = actionObject.has("fixed") ? getFixed(actionObject.get("fixed")) : new Pair<>(-1, -1);
                action.setWeight(weight);
                action.setFixedCount(fixedCount);
            } else {
                action = getActionSafely(actionObject, phasesManager, fileName).orElse(null);
            }

            if (action == null)
                continue;

            int amountOfActions = actionObject.has("amount") ? actionObject.get("amount").getAsInt() : 1;

            for (int i = 0; i < amountOfActions; i++)
                actionList.add(action);
        }

        return actionList.toArray(new Action[0]);
    }

    public static ContainerPoll[] getContainerItems(JsonArray jsonArray, String fileName) {
        List<ContainerPoll> polls = new ArrayList<>();

        for (JsonElement jsonElement : jsonArray) {
            polls.add(ContainerPoll.fromJson((JsonObject) jsonElement, fileName));
        }

        return polls.toArray(new ContainerPoll[0]);
    }

    public static <T> T parseFile(File file, Class<T> classOf) throws IOException {
        StringBuilder data = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null)
                data.append(line).append("\n");
        }

        return gson.fromJson(data.toString(), classOf);
    }

    private static Optional<Action> getActionSafely(JsonObject jsonObject, PhasesHandler phasesManager, String fileName) {
        try {
            return JsonUtils.getAction(jsonObject, phasesManager, fileName);
        } catch (ParsingException error) {
            OneBlockModule.log("[" + fileName + "] " + error.getMessage());
            error.printStackTrace();
            return Optional.empty();
        }
    }

}
