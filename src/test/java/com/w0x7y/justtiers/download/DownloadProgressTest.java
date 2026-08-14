package com.w0x7y.justtiers.download;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class DownloadProgressTest {

    /** A clock the test drives by hand, so the failure timeout needs no sleeping. */
    private final AtomicLong now = new AtomicLong();
    private final DownloadProgress progress = new DownloadProgress(now::get);

    @Test
    void startsIdle() {
        assertEquals(DownloadProgress.State.IDLE, progress.snapshot().state());
    }

    @Test
    void reportsBytesWhileDownloading() {
        long token = progress.started();
        progress.advanced(token, 100);
        progress.advanced(token, 50);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.DOWNLOADING, snapshot.state());
        assertEquals(150, snapshot.bytesRead());
    }

    @Test
    void firstDownloadIsIndeterminate() {
        long token = progress.started();
        progress.advanced(token, 100);
        assertFalse(progress.snapshot().determinate());
        assertEquals(0, progress.snapshot().total());
    }

    @Test
    void successCalibratesTheNextDownload() {
        long first = progress.started();
        progress.advanced(first, 1_000);
        progress.finished(first);

        long second = progress.started();
        progress.advanced(second, 400);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertTrue(snapshot.determinate());
        assertEquals(1_000, snapshot.total());
        assertEquals(400, snapshot.bytesRead());
    }

    @Test
    void startedResetsTheByteCount() {
        long token = progress.started();
        progress.advanced(token, 1_000);
        progress.finished(token);

        progress.started();
        assertEquals(0, progress.snapshot().bytesRead());
    }

    @Test
    void failureDoesNotCalibrate() {
        long token = progress.started();
        progress.advanced(token, 1_000);
        progress.failed(token);

        progress.started();
        assertFalse(progress.snapshot().determinate());
    }

    @Test
    void failureIsShownThenExpires() {
        long token = progress.started();
        progress.failed(token);
        assertEquals(DownloadProgress.State.FAILED, progress.snapshot().state());

        now.addAndGet(DownloadProgress.FAILURE_DISPLAY_NANOS + 1);
        assertEquals(DownloadProgress.State.IDLE, progress.snapshot().state());
    }

    @Test
    void aNewDownloadClearsAStandingFailure() {
        long token = progress.started();
        progress.failed(token);
        progress.started();
        assertEquals(DownloadProgress.State.DOWNLOADING, progress.snapshot().state());
    }

    // --- overlapping downloads ---
    // A refresh does not wait for a load already in flight, so the older download can
    // report long after the newer one has taken over the indicator.

    @Test
    void aStaleCompletionDoesNotEndTheCurrentDownload() {
        long stale = progress.started();
        progress.advanced(stale, 1_000);

        long current = progress.started();
        progress.advanced(current, 400);
        progress.finished(stale);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.DOWNLOADING, snapshot.state());
        assertEquals(400, snapshot.bytesRead());
        // The stale download must not calibrate either: its byte count was never the total.
        assertFalse(snapshot.determinate());
    }

    @Test
    void aStaleFailureDoesNotFailTheCurrentDownload() {
        long stale = progress.started();
        long current = progress.started();

        progress.failed(stale);

        assertEquals(DownloadProgress.State.DOWNLOADING, progress.snapshot().state());

        progress.advanced(current, 250);
        assertEquals(250, progress.snapshot().bytesRead());
    }

    @Test
    void aStaleDownloadDoesNotAddToTheCurrentByteCount() {
        long stale = progress.started();
        long current = progress.started();

        progress.advanced(stale, 1_000);
        progress.advanced(current, 100);

        assertEquals(100, progress.snapshot().bytesRead());
    }
}
