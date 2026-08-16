package com.w0x7y.justtiers.scan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The order a scan is read in: most dangerous first.
 *
 * <p>Rows are re-sorted every time an answer lands rather than once at the end, so the
 * list is always correct for what is known. It visibly moves for the first seconds of a
 * scan; a stable list would only be buying calm with a wrong order.
 */
public final class ScanReport {

    private static final Comparator<ScanRow> ORDER =
            Comparator.comparingInt(ScanRow::points).reversed()
                    .thenComparing(row -> row.player().name(),
                            String.CASE_INSENSITIVE_ORDER);

    public static List<ScanRow> sorted(Collection<ScanRow> rows) {
        List<ScanRow> ordered = new ArrayList<>(rows);
        ordered.sort(ORDER);
        return List.copyOf(ordered);
    }

    private ScanReport() {
    }
}
