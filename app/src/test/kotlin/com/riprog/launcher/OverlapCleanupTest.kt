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
    fun testSmallerMovesWhenDroppedOnLarge() {
        // Large item already at (0,0)
        val large = HomeItem.createApp("large", "cls", 0f, 0f, 0)
        large.spanX = 2f
        large.spanY = 2f

        // Small item dropped at (1,1). Overlaps large.
        val small = HomeItem.createApp("small", "cls", 1f, 1f, 0)
        small.spanX = 1f
        small.spanY = 1f

        // Simulating Case 1: small is smaller -> small moves
        if (intersects(small, large)) {
            val nearest = findNearestEmptyArea(0, 1, 1, 1, 1, listOf(large))
            if (nearest != null) {
                small.col = nearest.first.toFloat()
                small.row = nearest.second.toFloat()
            }
        }

        assertEquals(0f, large.col)
        assertEquals(0f, large.row)
        assertTrue("Small item should have moved from (1,1)", small.col != 1f || small.row != 1f)
    }

    @Test
    fun testSmallerMovesWhenLargeDroppedOnSmall() {
        // Small item already at (1,1)
        val small = HomeItem.createApp("small", "cls", 1f, 1f, 0)
        small.spanX = 1f
        small.spanY = 1f

        // Large item dropped at (0,0). Overlaps small.
        val large = HomeItem.createApp("large", "cls", 0f, 0f, 0)
        large.spanX = 2f
        large.spanY = 2f

        // Simulating Case 1: small is smaller -> small moves
        if (intersects(large, small)) {
            val nearest = findNearestEmptyArea(0, 1, 1, 1, 1, listOf(large))
            if (nearest != null) {
                small.col = nearest.first.toFloat()
                small.row = nearest.second.toFloat()
            }
        }

        assertEquals(0f, large.col)
        assertEquals(0f, large.row)
        assertTrue("Small item should have moved from (1,1)", small.col != 1f || small.row != 1f)
    }

    @Test
    fun testSwapWhenSameSize() {
        // Item A at (0,0)
        val itemA = HomeItem.createApp("A", "cls", 0f, 0f, 0)
        itemA.spanX = 1f
        itemA.spanY = 1f

        // Item B dropped at (0,0) from elsewhere
        // was at (2,2)
        val itemB = HomeItem.createApp("B", "cls", 2f, 2f, 0)
        itemB.spanX = 1f
        itemB.spanY = 1f
        val targetBCol = 0f
        val targetBRow = 0f

        // Simulating Case 2: same size -> swap
        if (targetBCol == itemA.col && targetBRow == itemA.row) {
            val oldBCol = itemB.col
            val oldBRow = itemB.row
            itemB.col = targetBCol
            itemB.row = targetBRow
            itemA.col = oldBCol
            itemA.row = oldBRow
        }

        assertEquals(0f, itemB.col)
        assertEquals(0f, itemB.row)
        assertEquals(2f, itemA.col)
        assertEquals(2f, itemA.row)
    }

    private fun intersects(item1: HomeItem, item2: HomeItem): Boolean {
        val r1 = item1.row.roundToInt()
        val c1 = item1.col.roundToInt()
        val sX1 = item1.spanX.roundToInt()
        val sY1 = item1.spanY.roundToInt()
        val r2 = item2.row.roundToInt()
        val c2 = item2.col.roundToInt()
        val sX2 = item2.spanX.roundToInt()
        val sY2 = item2.spanY.roundToInt()
        return c1 < c2 + sX2 && c1 + sX1 > c2 && r1 < r2 + sY2 && r1 + sY1 > r2
    }

    private fun findNearestEmptyArea(pageIndex: Int, spanX: Int, spanY: Int, prefCol: Int, prefRow: Int, otherItems: List<HomeItem>): Pair<Int, Int>? {
        val occupied = Array(GRID_ROWS) { BooleanArray(columns) }
        for (item in otherItems) {
            val rStart = max(0, item.row.roundToInt())
            val rEnd = min(GRID_ROWS - 1, (item.row + item.spanY).roundToInt() - 1)
            val cStart = max(0, item.col.roundToInt())
            val cEnd = min(columns - 1, (item.col + item.spanX).roundToInt() - 1)
            for (r in rStart..rEnd) {
                for (c in cStart..cEnd) {
                    occupied[r][c] = true
                }
            }
        }
        var minDest = Double.MAX_VALUE
        var bestPos: Pair<Int, Int>? = null
        for (i in 0..GRID_ROWS - spanY) {
            for (j in 0..columns - spanX) {
                var canPlace = true
                for (ri in i until i + spanY) {
                    for (ci in j until j + spanX) {
                        if (occupied[ri][ci]) {
                            canPlace = false
                            break
                        }
                    }
                    if (!canPlace) break
                }
                if (canPlace) {
                    val d = sqrt((i - prefRow).toDouble().pow(2.0) + (j - prefCol).toDouble().pow(2.0))
                    if (d < minDest) {
                        minDest = d
                        bestPos = Pair(j, i)
                    }
                }
            }
        }
        return bestPos
    }
}
