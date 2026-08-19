package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;

import java.util.Map;

/**
 * The settings the preview draws from. Always built out of YACL's <em>pending</em>
 * values, never the saved config, so the preview agrees with what Save would write
 * and Cancel discards it along with everything else.
 */
public record PreviewState(boolean enabled,
                           DisplayMode displayMode,
                           Map<Source, String> selectedGamemodes,
                           boolean showRetired,
                           NametagStyle style) {

    public PreviewState {
        selectedGamemodes = Map.copyOf(selectedGamemodes);
        style = style == null ? NametagStyle.DEFAULT : style;
    }

    /**
     * The badge these settings would draw. {@code timeMillis} drives the preview's
     * active/retired cycle, so a widget that redraws every frame gets an animated tag
     * out of a state record that holds no clock of its own.
     */
    public Badge badge(long timeMillis) {
        return Badge.preview(displayMode, selectedGamemodes, showRetired, timeMillis, style);
    }
}
