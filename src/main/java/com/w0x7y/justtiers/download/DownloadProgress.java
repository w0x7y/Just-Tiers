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

    public void started() {
        bytesRead.set(0);
        failed = false;
        downloading = true;
    }

    public void advanced(long bytes) {
        bytesRead.addAndGet(bytes);
    }

    /**
     * Calibrates from the bytes just counted rather than from a figure passed in, so the
     * total and the counter can never disagree.
     */
    public void finished() {
        lastKnownTotal = bytesRead.get();
        downloading = false;
    }

    /** A failed download does not calibrate: a truncated body is not a size. */
    public void failed() {
        downloading = false;
        failed = true;
        failedAt = clock.getAsLong();
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
