package com.riprog.launcher;

import android.content.Context;

public class GridManager {
    private final int columns;
    private final int rows = 6;

    public GridManager(int columns) {
        this.columns = columns;
    }

    public int getCellWidth(int availableWidth) {
        return availableWidth / Math.max(1, columns);
    }

    public int getCellHeight(int availableHeight) {
        return availableHeight / Math.max(1, rows);
    }

    public float calculateSpanX(float widthPx, int cellWidth) {
        if (cellWidth <= 0) return 1f;
        return widthPx / cellWidth;
    }

    public float calculateSpanY(float heightPx, int cellHeight) {
        if (cellHeight <= 0) return 1f;
        return heightPx / cellHeight;
    }
}
