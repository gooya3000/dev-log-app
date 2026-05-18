package com.example.devlogapp.domain;

import java.util.Objects;

/**
 * 사용자 식별자 value object.
 */
public final class UserId {

    private final String value;

    public UserId(String value) {
        Objects.requireNonNull(value, "UserId value must not be null");
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId)) return false;
        UserId that = (UserId) o;
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
