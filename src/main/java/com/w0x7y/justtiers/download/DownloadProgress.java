package com.w0x7y.justtiers.download;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Progress of the NovaTiers bulk download, written by the HTTP thread and read by the
 * render thread every frame.
 *
 * <p>novatiers.com sends no {@code content-length}, so the total size is unknown until a
 * download has finished once. The first download of a session is therefore indeterminate;
 * afterwards {@link Snapshot#total()} carries the previous download's size and the bar can
 * show a real percentage. Nothing here is persisted, so every session starts uncalibrated.
 */
public final class DownloadProgress {

    public enum State { IDLE, DOWNLOADING, FAILED }

    /** How long a failed download is reported before the indicator gives up on it. */
    static final long FAILURE_DISPLAY_NANOS = Duration.ofSeconds(4).toNanos();

    /**
     * @param total the previous download's size, or {@code 0} when none has completed yet.
     */
    public record Snapshot(State state, long bytesRead, long total) {
        /** Whether a percentage can be shown, or only a byte count. */
        public boolean determinate() {
            return total > 0;
        }
    }

    private final LongSupplier clock;
    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong generation = new AtomicLong();
    private volatile long lastKnownTotal;
    private volatile boolean downloading;
    private volatile boolean failed;
    private volatile long failedAt;

    public DownloadProgress() {
        this(System::nanoTime);
    }

    DownloadProgress(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Begins a download and returns the token that its own later updates must carry.
     * Downloads can overlap - a refresh does not wait for a load already in flight - and
     * only the newest one owns the displayed state, so every update is checked against the
     * token here and a stale one is dropped rather than reporting an older download's
     * bytes, completion or failure over a newer download's.
     */
    public long started() {
        long token = generation.incrementAndGet();
        bytesRead.set(0);
        failed = false;
        downloading = true;
        return token;
    }

    public void advanced(long token, long bytes) {
        if (stale(token)) {
            return;
        }
        bytesRead.addAndGet(bytes);
    }

    /**
     * Calibrates from the bytes just counted rather than from a figure passed in, so the
     * total and the counter can never disagree.
     */
    public void finished(long token) {
        if (stale(token)) {
            return;
        }
        lastKnownTotal = bytesRead.get();
        downloading = false;
    }

    /** A failed download does not calibrate: a truncated body is not a size. */
    public void failed(long token) {
        if (stale(token)) {
            return;
        }
        downloading = false;
        failed = true;
        failedAt = clock.getAsLong();
    }

    private boolean stale(long token) {
        return token != generation.get();
    }

    public Snapshot snapshot() {
        if (downloading) {
            return new Snapshot(State.DOWNLOADING, bytesRead.get(), lastKnownTotal);
        }
        if (failed && clock.getAsLong() - failedAt < FAILURE_DISPLAY_NANOS) {
            return new Snapshot(State.FAILED, bytesRead.get(), lastKnownTotal);
        }
        return new Snapshot(State.IDLE, 0, lastKnownTotal);
    }
}
