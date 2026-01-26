package com.bgsoftware.ssboneblock.api;

import java.util.Locale;
import java.util.Objects;

public final class OneBlockSlot {

    private final String dimension;
    private final int id;

    public OneBlockSlot(String dimension, int id) {
        if (dimension == null)
            throw new IllegalArgumentException("dimension cannot be null");
        this.dimension = dimension.toUpperCase(Locale.ENGLISH);
        this.id = id;
    }

    public String getDimension() {
        return dimension;
    }

    public int getId() {
        return id;
    }

    public String toKey() {
        return dimension + "-" + id;
    }

    public static OneBlockSlot fromKey(String key) {
        if (key == null || key.isEmpty())
            return null;

        int separatorIndex = key.lastIndexOf('-');
        if (separatorIndex <= 0 || separatorIndex == key.length() - 1)
            return null;

        String dimensionPart = key.substring(0, separatorIndex);
        String idPart = key.substring(separatorIndex + 1);

        try {
            int parsedId = Integer.parseInt(idPart);
            return new OneBlockSlot(dimensionPart, parsedId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof OneBlockSlot))
            return false;
        OneBlockSlot that = (OneBlockSlot) o;
        return id == that.id && dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, id);
    }

}
