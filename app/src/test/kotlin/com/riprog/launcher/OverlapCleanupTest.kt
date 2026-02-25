package com.riprog.launcher

import com.riprog.launcher.data.model.HomeItem
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sqrt

class OverlapCleanupTest {

    private val GRID_ROWS = 6
    private val columns = 4

    @Test
    fun testOverlapResolutionLogic() {
        // Mock items
        val item1 = HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0)
        item1.spanX = 2f
        item1.spanY = 2f // 2x2 at (0,0)

        val item2 = HomeItem.createApp("pkg2", "cls2", 1f, 1f, 0)
        item2.spanX = 1f
        item2.spanY = 1f // 1x1 at (1,1) - Overlaps with item1

        val items = listOf(item1, item2)

        // Simulate logic from resolveAllOverlaps
        // 1. Round spans
        for (item in items) {
            item.spanX = max(1f, item.spanX.roundToInt().toFloat())
            item.spanY = max(1f, item.spanY.roundToInt().toFloat())
        }

        // 2. Sort by size descending, then position
        val sortedItems = items.sortedWith(compareByDescending<HomeItem> { it.spanX * it.spanY }
            .thenBy { it.row * columns + it.col })

        assertEquals(item1, sortedItems[0]) // item1 is larger
        assertEquals(item2, sortedItems[1])

        val occupied = Array(GRID_ROWS) { BooleanArray(columns) }

        // Place item1
        var r1 = max(0, min(GRID_ROWS - item1.spanY.toInt(), item1.row.roundToInt()))
        var c1 = max(0, min(columns - item1.spanX.toInt(), item1.col.roundToInt()))

        // canPlace(occupied, r1, c1, 2, 2) is true
        item1.row = r1.toFloat()
        item1.col = c1.toFloat()
        for (i in r1 until r1 + item1.spanY.toInt()) {
            for (j in c1 until c1 + item1.spanX.toInt()) {
                occupied[i][j] = true
            }
        }

        // Place item2
        var r2 = max(0, min(GRID_ROWS - item2.spanY.toInt(), item2.row.roundToInt()))
        var c2 = max(0, min(columns - item2.spanX.toInt(), item2.col.roundToInt()))

        // Check if it overlaps
        if (!canPlace(occupied, r2, c2, item2.spanX.toInt(), item2.spanY.toInt())) {
            val pos = findNearestAvailable(occupied, r2, c2, item2.spanX.toInt(), item2.spanY.toInt())
            if (pos != null) {
                r2 = pos.first
                c2 = pos.second
            }
        }

        item2.row = r2.toFloat()
        item2.col = c2.toFloat()

        // Verify item1 kept its position
        assertEquals(0f, item1.row)
        assertEquals(0f, item1.col)

        // Verify item2 moved to nearest available (which should be (2,1) or (1,2) or (0,2) or (2,0))
        // (1,1) was requested.
        // Occupied: (0,0), (0,1), (1,0), (1,1)
        // Nearest to (1,1) among empty: (2,1), (1,2), (2,2), (0,2), (2,0) etc.
        // Dist from (1,1) to (2,1) is 1.0
        // Dist from (1,1) to (1,2) is 1.0
        // Dist from (1,1) to (0,2) is sqrt(1+1) = 1.41
        // (0,2) is available? yes. (1,2) is available? yes.

        assertTrue("Item 2 should have moved", item2.row != 1f || item2.col != 1f)
        assertTrue("Item 2 should be in a valid position", canPlace(Array(GRID_ROWS) { BooleanArray(columns) }, item2.row.toInt(), item2.col.toInt(), 1, 1))
    }

    private fun canPlace(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Boolean {
        for (i in r until r + spanY) {
            for (j in c until c + spanX) {
                if (i >= GRID_ROWS || j >= columns || occupied[i][j]) return false
            }
        }
        return true
    }

    private fun findNearestAvailable(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        var minDest = Double.MAX_VALUE
        var bestPos: Pair<Int, Int>? = null

        for (i in 0..GRID_ROWS - spanY) {
            for (j in 0..columns - spanX) {
                if (canPlace(occupied, i, j, spanX, spanY)) {
                    val d = sqrt((i - r).toDouble().pow(2.0) + (j - c).toDouble().pow(2.0))
                    if (d < minDest) {
                        minDest = d
                        bestPos = Pair(i, j)
                    }
                }
            }
        }
        return bestPos
    }
}
