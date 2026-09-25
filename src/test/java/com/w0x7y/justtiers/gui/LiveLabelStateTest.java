package com.w0x7y.justtiers.gui;

import dev.isxander.yacl3.api.StateManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiveLabelStateTest {
    @Test
    void readsCurrentValueWithoutCreatingPendingEdits() {
        var value = new AtomicInteger(1);
        var state = new LiveLabelState<>(value::get);
        assertEquals(1, state.get());
        value.set(2);
        assertEquals(2, state.get());
        state.set(99);
        state.apply();
        state.sync();
        state.resetToDefault(StateManager.ResetAction.BY_GLOBAL);
        assertEquals(2, state.get());
        assertEquals(2, value.get());
        assertTrue(state.isSynced());
        assertTrue(state.isAlwaysSynced());
        assertTrue(state.isDefault());
    }
}
