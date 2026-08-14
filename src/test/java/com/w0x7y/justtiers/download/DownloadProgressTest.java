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
        progress.started();
        progress.advanced(100);
        progress.advanced(50);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.DOWNLOADING, snapshot.state());
        assertEquals(150, snapshot.bytesRead());
    }

    @Test
    void firstDownloadIsIndeterminate() {
        progress.started();
        progress.advanced(100);
        assertFalse(progress.snapshot().determinate());
        assertEquals(0, progress.snapshot().total());
    }

    @Test
    void successCalibratesTheNextDownload() {
        progress.started();
        progress.advanced(1_000);
        progress.finished();

        progress.started();
        progress.advanced(400);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertTrue(snapshot.determinate());
        assertEquals(1_000, snapshot.total());
        assertEquals(400, snapshot.bytesRead());
    }

    @Test
    void startedResetsTheByteCount() {
        progress.started();
        progress.advanced(1_000);
        progress.finished();

        progress.started();
        assertEquals(0, progress.snapshot().bytesRead());
    }

    @Test
    void failureDoesNotCalibrate() {
        progress.started();
        progress.advanced(1_000);
        progress.failed();

        progress.started();
        assertFalse(progress.snapshot().determinate());
    }

    @Test
    void failureIsShownThenExpires() {
        progress.started();
        progress.failed();
        assertEquals(DownloadProgress.State.FAILED, progress.snapshot().state());

        now.addAndGet(DownloadProgress.FAILURE_DISPLAY_NANOS + 1);
        assertEquals(DownloadProgress.State.IDLE, progress.snapshot().state());
    }

    @Test
    void aNewDownloadClearsAStandingFailure() {
        progress.started();
        progress.failed();
        progress.started();
        assertEquals(DownloadProgress.State.DOWNLOADING, progress.snapshot().state());
    }
}
