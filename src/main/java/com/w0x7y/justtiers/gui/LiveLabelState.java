package com.w0x7y.justtiers.gui;

import dev.isxander.yacl3.api.StateManager;

import java.util.Objects;
import java.util.function.Supplier;

/** Read-only labels sampled on every draw, including on YACL 3.8 without xmap. */
final class LiveLabelState<T> implements StateManager<T> {
    private final Supplier<T> value;

    LiveLabelState(Supplier<T> value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public T get() {
        return value.get();
    }

    @Override
    public void set(T ignored) {
    }

    @Override
    public void apply() {
    }

    @Override
    public void resetToDefault(ResetAction action) {
    }

    @Override
    public void sync() {
    }

    @Override
    public boolean isSynced() {
        return true;
    }

    @Override
    public boolean isAlwaysSynced() {
        return true;
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public void addListener(StateListener<T> listener) {
        // Labels are sampled directly and never publish pending edits.
    }
}
