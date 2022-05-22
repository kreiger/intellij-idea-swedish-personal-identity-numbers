package com.linuxgods.kreiger.swedish.personalidentitynumbers.model;

import java.util.Locale;

public enum Requirement {
    REQUIRED,
    ALLOWED,
    REJECTED;

    public <T> T fold(T required, T optional, T rejected) {
        switch (this) {
            case REQUIRED: return required;
            case ALLOWED: return optional;
            case REJECTED: return rejected;
        };
        throw new IllegalStateException();
    }


    @Override public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
