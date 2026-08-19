package com.w0x7y.justtiers.gui.layout;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Tile-grid arithmetic for the gamemode picker: how many columns fit, where each tile
 * lands, which tile a point is inside, and where an arrow key moves the focus.
 *
 * <p>Coordinates are relative to the grid's own top-left origin, and the class knows
 * nothing about Minecraft — which is what lets "clicking the gap between two tiles
 * selects nothing" be a unit test rather than a manual click-around.
 */
public final class GridLayout {

    private final int itemCount;
    private final int columns;
    private final int rows;
    private final int tileWidth;
    private final int tileHeight;
    private final int gap;

    public enum Direction { LEFT, RIGHT, UP, DOWN }

    private GridLayout(int itemCount, int columns, int rows,
                       int tileWidth, int tileHeight, int gap) {
        this.itemCount = itemCount;
        this.columns = columns;
        this.rows = rows;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.gap = gap;
    }

    public static GridLayout of(int itemCount, int availableWidth,
                                int tileWidth, int tileHeight, int gap, int maxColumns) {
        int fitting = (availableWidth + gap) / (tileWidth + gap);
        int columns = Math.clamp(fitting, 1, Math.max(1, maxColumns));
        if (itemCount > 0) {
            // Eight tiles must not spread themselves across twelve columns.
            columns = Math.min(columns, itemCount);
        }
        int rows = itemCount <= 0 ? 0 : Math.ceilDiv(itemCount, columns);
        return new GridLayout(Math.max(0, itemCount), columns, rows, tileWidth, tileHeight, gap);
    }

    public int itemCount() {
        return itemCount;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int contentWidth() {
        return columns * tileWidth + (columns - 1) * gap;
    }

    public int contentHeight() {
        return rows == 0 ? 0 : rows * tileHeight + (rows - 1) * gap;
    }

    public int xOf(int index) {
        return (index % columns) * (tileWidth + gap);
    }

    public int yOf(int index) {
        return (index / columns) * (tileHeight + gap);
    }

    /** The tile containing this point, or empty for a gap, an edge miss or an empty cell. */
    public OptionalInt indexAt(int localX, int localY) {
        if (itemCount == 0 || localX < 0 || localY < 0) {
            return OptionalInt.empty();
        }
        int strideX = tileWidth + gap;
        int strideY = tileHeight + gap;
        if (localX % strideX >= tileWidth || localY % strideY >= tileHeight) {
            return OptionalInt.empty();   // landed in a gap
        }
        int column = localX / strideX;
        int row = localY / strideY;
        if (column >= columns || row >= rows) {
            return OptionalInt.empty();
        }
        int index = row * columns + column;
        return index < itemCount ? OptionalInt.of(index) : OptionalInt.empty();
    }

    /**
     * A grid is its six numbers and nothing else, so two built from the same
     * measurements are the same grid. {@link LookupLayout} is a record that holds these,
     * and a record is only a value if what it holds is.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof GridLayout grid
                && itemCount == grid.itemCount && columns == grid.columns
                && rows == grid.rows && tileWidth == grid.tileWidth
                && tileHeight == grid.tileHeight && gap == grid.gap;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemCount, columns, rows, tileWidth, tileHeight, gap);
    }

    /**
     * Moves the focus one cell, clamped at every edge and never onto one of the empty
     * trailing cells of a partial last row.
     */
    public int move(int index, Direction direction) {
        if (itemCount == 0) {
            return index;
        }
        int current = Math.clamp(index, 0, itemCount - 1);
        int column = current % columns;
        int row = current / columns;
        switch (direction) {
            case LEFT -> column--;
            case RIGHT -> column++;
            case UP -> row--;
            case DOWN -> row++;
        }
        column = Math.clamp(column, 0, columns - 1);
        row = Math.clamp(row, 0, rows - 1);
        return Math.min(row * columns + column, itemCount - 1);
    }
}
