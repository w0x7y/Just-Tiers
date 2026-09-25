package com.w0x7y.justtiers.settings;

import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/** Publishes settings and their runtime effects under the caller's persistence policy. */
public final class SettingsApplication {
    private final Path path;
    private final TierCache cache;
    private final RefreshLifecycle refresh;
    private JustTiersConfig active;

    public SettingsApplication(JustTiersConfig initial, Path path, TierCache cache,
                               RefreshLifecycle refresh) {
        this.path = path;
        this.cache = cache;
        this.refresh = refresh;
        publish(initial.copy());
    }

    /** Live read access for rendering and diagnostics. Edits go through this application. */
    public JustTiersConfig active() {
        return active;
    }

    public JustTiersConfig draft() {
        return active.copy();
    }

    /** Failure leaves the active settings untouched and the caller's draft retryable. */
    public void commitDraft(JustTiersConfig draft) {
        JustTiersConfig candidate = draft.copy();
        candidate.save(path);
        publish(candidate);
    }

    /** Persistence failure is reported after the edit and its effects become live. */
    public void editSession(Consumer<JustTiersConfig> edit) {
        JustTiersConfig candidate = draft();
        edit.accept(candidate);
        publish(candidate);
        candidate.save(path);
    }

    private void publish(JustTiersConfig candidate) {
        active = candidate;
        cache.setTtl(Duration.ofMinutes(candidate.getTierCacheMinutes()));
        refresh.setInterval(candidate.getNovaRefreshMinutes());
    }
}
