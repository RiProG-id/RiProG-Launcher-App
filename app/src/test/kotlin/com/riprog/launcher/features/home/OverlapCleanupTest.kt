package com.riprog.launcher.features.home

import com.riprog.launcher.data.models.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sqrt

class OverlapCleanupTest {

    private val GRID_ROWS = 6
    private val columns = 4

    @Test
    fun testIntersects() {
        val item1 = HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0)
        val item2 = HomeItem.createApp("pkg2", "cls2", 0f, 0f, 0)
        assertTrue(intersects(item1, item2))

        val item3 = HomeItem.createApp("pkg3", "cls3", 1f, 1f, 0)
        assertTrue(intersects(item1, item3) == false)
    }

    @Test
    fun testFindNearestEmptyArea() {
        val large = HomeItem.createApp("large", "cls", 0f, 0f, 0)
        large.spanX = 2f
        large.spanY = 2f

        val nearest = findNearestEmptyArea(0, 1, 1, 1, 1, listOf(large))

        assertTrue(nearest != null)
        val (col, row) = nearest!!
        assertTrue(col >= 2 || row >= 2)
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
        val rows = GRID_ROWS
        val cols = columns
        val occupied = Array(rows) { BooleanArray(cols) }

        for (item in otherItems) {
            val rStart = max(0, item.row.roundToInt())
            val rEnd = min(rows - 1, (item.row + item.spanY).roundToInt() - 1)
            val cStart = max(0, item.col.roundToInt())
            val cEnd = min(cols - 1, (item.col + item.spanX).roundToInt() - 1)
            for (r in rStart..rEnd) {
                for (c in cStart..cEnd) {
                    occupied[r][c] = true
                }
            }
        }

        var minDest = Double.MAX_VALUE
        var bestPos: Pair<Int, Int>? = null

        for (i in 0..rows - spanY) {
            for (j in 0..cols - spanX) {
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
