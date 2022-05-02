package com.linuxgods.kreiger.swedish.personalidentitynumbers;

import com.intellij.openapi.util.TextRange;

import java.util.Objects;

public class TextRanged<V> {
    private final V value;
    private final TextRange range;

    public TextRanged(V value, TextRange range) {
        this.value = Objects.requireNonNull(value);
        this.range = Objects.requireNonNull(range);
    }

    public V getValue() {
        return value;
    }

    public TextRange getTextRange() {
        return range;
    }

    @Override
    public String toString() {
        return range.toString() + value;
    }
}
