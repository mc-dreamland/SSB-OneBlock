package com.bgsoftware.ssboneblock.api;

import java.util.Locale;
import java.util.Objects;

public final class OneBlockSlot {

    private final String dimension;

    public OneBlockSlot(String dimension) {
        if (dimension == null)
            throw new IllegalArgumentException("dimension cannot be null");
        this.dimension = dimension.toUpperCase(Locale.ENGLISH);
    }

    public String getDimension() {
        return dimension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof OneBlockSlot))
            return false;
        OneBlockSlot that = (OneBlockSlot) o;
        return dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension);
    }

}
