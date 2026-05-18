package com.example.devlogapp.domain;

import java.util.Objects;

/**
 * 회고 식별자 value object.
 */
public final class DevLogId {

    private final String value;

    public DevLogId(String value) {
        Objects.requireNonNull(value, "DevLogId value must not be null");
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DevLogId)) return false;
        DevLogId that = (DevLogId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
