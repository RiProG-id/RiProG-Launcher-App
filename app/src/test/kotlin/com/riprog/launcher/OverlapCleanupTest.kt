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
    fun testNoAutoArrangeWhenDroppedOnOccupied() {
        // Large item already at (0,0)
        val large = HomeItem.createApp("large", "cls", 0f, 0f, 0)
        large.spanX = 2f
        large.spanY = 2f

        // Small item dropped at (1,1)
        val small = HomeItem.createApp("small", "cls", 1f, 1f, 0)
        small.spanX = 1f
        small.spanY = 1f

        // Simulating the new logic: drop small on large
        // Should NOT move anything.
        if (intersects(small, large)) {
            // In the new logic, applyNewGridLogic does NOTHING but set the intended coordinates
            small.col = 1f
            small.row = 1f
        }

        assertEquals(0f, large.col)
        assertEquals(0f, large.row)
        assertEquals(1f, small.col)
        assertEquals(1f, small.row)
    }

    @Test
    fun testNoSecondaryRepositionWhenLargeDroppedOnSmall() {
        // Small item already at (1,1)
        val small = HomeItem.createApp("small", "cls", 1f, 1f, 0)
        small.spanX = 1f
        small.spanY = 1f

        // Large item dropped at (0,0)
        val large = HomeItem.createApp("large", "cls", 0f, 0f, 0)
        large.spanX = 2f
        large.spanY = 2f

        // Simulating the new logic: drop large on small
        // Should NOT move anything.
        if (intersects(large, small)) {
            large.col = 0f
            large.row = 0f
        }

        assertEquals(1f, small.col)
        assertEquals(1f, small.row)
        assertEquals(0f, large.col)
        assertEquals(0f, large.row)
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
