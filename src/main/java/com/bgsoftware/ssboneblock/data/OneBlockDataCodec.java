package com.bgsoftware.ssboneblock.data;

import com.bgsoftware.ssboneblock.phases.IslandPhaseData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OneBlockDataCodec {

    private static final DateTimeFormatter EXPIRATION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OneBlockDataCodec() {
    }

    static JsonObject toJson(IslandPhaseData data) {
        JsonObject json = new JsonObject();
        json.addProperty("phaseLevel", data.getPhaseLevel());
        json.addProperty("phaseBlock", data.getPhaseBlock());
        json.addProperty("phaseLoopTimes", data.getPhaseLoopTimes());

        JsonObject unlocks = new JsonObject();
        for (Map.Entry<String, List<IslandPhaseData.OneBlockSlotData>> entry : data.getUnlocks().entrySet()) {
            unlocks.add(entry.getKey(), encodeSlots(entry.getValue(), true));
        }
        ensureDefaultDimensions(unlocks);
        json.add("unlocks", unlocks);

        JsonObject apiUnlocks = new JsonObject();
        for (Map.Entry<String, List<IslandPhaseData.OneBlockSlotData>> entry : data.getApiUnlocks().entrySet()) {
            apiUnlocks.add(entry.getKey(), encodeSlots(entry.getValue(), false));
        }
        ensureDefaultDimensions(apiUnlocks);
        json.add("apiUnlocks", apiUnlocks);

        return json;
    }

    static IslandPhaseData fromJson(JsonObject json) {
        if (json == null)
            return null;

        int phaseLevel = getInt(json, "phaseLevel", 0);
        int phaseBlock = getInt(json, "phaseBlock", 0);
        int phaseLoopTimes = getInt(json, "phaseLoopTimes", 0);

        Map<String, List<IslandPhaseData.OneBlockSlotData>> unlocks = new HashMap<>();
        Map<String, List<IslandPhaseData.OneBlockSlotData>> apiUnlocks = new HashMap<>();
        long now = System.currentTimeMillis();

        JsonObject unlocksObject = json.has("unlocks") && json.get("unlocks").isJsonObject()
                ? json.getAsJsonObject("unlocks") : null;
        if (unlocksObject != null) {
            for (Map.Entry<String, JsonElement> entry : unlocksObject.entrySet()) {
                List<IslandPhaseData.OneBlockSlotData> slots = decodeSlots(entry.getValue(), true, now);
                if (!slots.isEmpty()) {
                    unlocks.put(entry.getKey().toUpperCase(Locale.ENGLISH), slots);
                }
            }
        }

        JsonObject apiUnlocksObject = json.has("apiUnlocks") && json.get("apiUnlocks").isJsonObject()
                ? json.getAsJsonObject("apiUnlocks") : null;
        if (apiUnlocksObject != null) {
            for (Map.Entry<String, JsonElement> entry : apiUnlocksObject.entrySet()) {
                List<IslandPhaseData.OneBlockSlotData> slots = decodeSlots(entry.getValue(), false, now);
                if (!slots.isEmpty()) {
                    apiUnlocks.put(entry.getKey().toUpperCase(Locale.ENGLISH), slots);
                }
            }
        }

        return new IslandPhaseData(phaseLevel, phaseBlock, phaseLoopTimes, unlocks, apiUnlocks);
    }

    static IslandPhaseData cleanupExpiredUnlocks(IslandPhaseData data) {
        Map<String, List<IslandPhaseData.OneBlockSlotData>> cleanedUnlocks = new HashMap<>();
        long now = System.currentTimeMillis();
        boolean modified = false;

        for (Map.Entry<String, List<IslandPhaseData.OneBlockSlotData>> entry : data.getUnlocks().entrySet()) {
            List<IslandPhaseData.OneBlockSlotData> cleaned = new ArrayList<>();
            for (IslandPhaseData.OneBlockSlotData slot : entry.getValue()) {
                if (slot == null) {
                    cleaned.add(null);
                    continue;
                }
                Long expiresAt = slot.getExpiresAt();
                if (expiresAt == null || expiresAt <= 0 || expiresAt > now) {
                    cleaned.add(slot);
                } else {
                    modified = true;
                }
            }
            if (!cleaned.isEmpty()) {
                cleanedUnlocks.put(entry.getKey().toUpperCase(Locale.ENGLISH), cleaned);
            } else if (!entry.getValue().isEmpty()) {
                modified = true;
            }
        }

        if (!modified)
            return data;

        return new IslandPhaseData(data.getPhaseLevel(), data.getPhaseBlock(), data.getPhaseLoopTimes(),
                cleanedUnlocks, data.getApiUnlocks());
    }

    static String formatExpiration(Long expiresAt) {
        if (expiresAt == null || expiresAt <= 0)
            return null;

        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAt), ZoneId.systemDefault());
        return EXPIRATION_FORMAT.format(time);
    }

    static Long parseExpiration(String value) {
        if (value == null || value.trim().isEmpty())
            return null;
        try {
            LocalDateTime time = LocalDateTime.parse(value.trim(), EXPIRATION_FORMAT);
            return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static String formatLocation(IslandPhaseData.OneBlockLocation location) {
        if (location == null)
            return null;
        return formatDouble(location.getX()) + ", " + formatDouble(location.getY()) + ", " + formatDouble(location.getZ());
    }

    static IslandPhaseData.OneBlockLocation parseLocation(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty())
            return null;
        String[] parts = trimmed.split(",");
        if (parts.length < 3)
            return null;
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            return new IslandPhaseData.OneBlockLocation(x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static JsonArray encodeSlots(List<IslandPhaseData.OneBlockSlotData> slots, boolean includeExpiration) {
        JsonArray array = new JsonArray();
        for (IslandPhaseData.OneBlockSlotData slot : slots) {
            JsonObject slotObject = new JsonObject();
            if (slot == null || slot.getLocation() == null) {
                slotObject.add("location", JsonNull.INSTANCE);
            } else {
                slotObject.addProperty("location", formatLocation(slot.getLocation()));
            }
            if (includeExpiration && slot != null) {
                String expiration = formatExpiration(slot.getExpiresAt());
                if (expiration != null) {
                    slotObject.addProperty("expiration", expiration);
                }
            }
            array.add(slotObject);
        }
        return array;
    }

    private static void ensureDefaultDimensions(JsonObject json) {
        if (!json.has("NORMAL"))
            json.add("NORMAL", new JsonArray());
        if (!json.has("NETHER"))
            json.add("NETHER", new JsonArray());
        if (!json.has("THE_END"))
            json.add("THE_END", new JsonArray());
    }

    private static List<IslandPhaseData.OneBlockSlotData> decodeSlots(JsonElement element, boolean readExpiration, long now) {
        List<IslandPhaseData.OneBlockSlotData> slots = new ArrayList<>();
        if (element == null || !element.isJsonArray())
            return slots;

        JsonArray array = element.getAsJsonArray();
        for (JsonElement slotElement : array) {
            if (slotElement == null || !slotElement.isJsonObject()) {
                slots.add(new IslandPhaseData.OneBlockSlotData(null, null));
                continue;
            }
            JsonObject slotObject = slotElement.getAsJsonObject();
            IslandPhaseData.OneBlockLocation location = null;
            if (slotObject.has("location")) {
                JsonElement locationElement = slotObject.get("location");
                if (locationElement != null && locationElement.isJsonPrimitive()) {
                    location = parseLocation(locationElement.getAsString());
                }
            }
            Long expiresAt = null;
            if (readExpiration && slotObject.has("expiration")) {
                JsonElement expirationElement = slotObject.get("expiration");
                if (expirationElement != null && expirationElement.isJsonPrimitive()) {
                    expiresAt = parseExpiration(expirationElement.getAsString());
                }
            }
            if (readExpiration && expiresAt != null && expiresAt > 0 && expiresAt <= now) {
                continue;
            }
            slots.add(new IslandPhaseData.OneBlockSlotData(location, expiresAt));
        }
        return slots;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            return json.get(key).getAsInt();
        }
        return fallback;
    }

    private static String formatDouble(double value) {
        String text = Double.toString(value);
        if (text.endsWith(".0")) {
            return text;
        }
        return text;
    }

}
