package com.riprog.launcher.logic.managers

import com.riprog.launcher.data.model.HomeItem
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class OverlapResolver(private val columns: Int, private val rows: Int) {

    fun resolve(movedItem: HomeItem, allItems: List<HomeItem>): Map<HomeItem, Pair<Int, Int>> {
        val pageItems = allItems.filter { it.page == movedItem.page && it !== movedItem }
        val overlappingItems = pageItems.filter { isOverlapping(movedItem, it) }

        if (overlappingItems.isEmpty()) return emptyMap()

        val occupied = Array(rows) { BooleanArray(columns) }

        // Mark moved item as occupied
        markOccupied(occupied, movedItem.row.roundToInt(), movedItem.col.roundToInt(), movedItem.spanX, movedItem.spanY)

        // Mark non-overlapping items as occupied
        for (item in pageItems) {
            if (!overlappingItems.contains(item)) {
                markOccupied(occupied, item.row.roundToInt(), item.col.roundToInt(), item.spanX, item.spanY)
            }
        }

        val repositioned = mutableMapOf<HomeItem, Pair<Int, Int>>()

        // Try to reposition overlapping items using BFS search for nearest valid cell
        for (item in overlappingItems) {
            val newPos = findNearestAvailable(occupied, item.row.roundToInt(), item.col.roundToInt(), item.spanX, item.spanY)
            if (newPos != null) {
                repositioned[item] = newPos
                markOccupied(occupied, newPos.first, newPos.second, item.spanX, item.spanY)
            }
        }

        return repositioned
    }

    private fun markOccupied(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int) {
        val rStart = max(0, r)
        val rEnd = min(rows - 1, r + spanY - 1)
        val cStart = max(0, c)
        val cEnd = min(columns - 1, c + spanX - 1)
        for (i in rStart..rEnd) {
            for (j in cStart..cEnd) {
                occupied[i][j] = true
            }
        }
    }

    private fun isOverlapping(a: HomeItem, b: HomeItem): Boolean {
        val al = a.col.roundToInt()
        val at = a.row.roundToInt()
        val ar = al + a.spanX
        val ab = at + a.spanY

        val bl = b.col.roundToInt()
        val bt = b.row.roundToInt()
        val br = bl + b.spanX
        val bb = bt + b.spanY

        return !(al >= br || ar <= bl || at >= bb || ab <= bt)
    }

    private fun findNearestAvailable(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val queue: Queue<Pair<Int, Int>> = LinkedList()
        queue.add(Pair(r, c))
        val visited = mutableSetOf<Pair<Int, Int>>()
        visited.add(Pair(r, c))

        while (queue.isNotEmpty()) {
            val curr = queue.poll()!!
            val currR = curr.first
            val currC = curr.second

            if (canPlace(occupied, currR, currC, spanX, spanY)) {
                return Pair(currR, currC)
            }

            // Neighbors: Up, Down, Left, Right
            val neighbors = listOf(
                Pair(currR - 1, currC),
                Pair(currR + 1, currC),
                Pair(currR, currC - 1),
                Pair(currR, currC + 1)
            )

            for (next in neighbors) {
                if (next.first in 0..rows - spanY && next.second in 0..columns - spanX && next !in visited) {
                    visited.add(next)
                    queue.add(next)
                }
            }
        }
        return null
    }

    private fun canPlace(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Boolean {
        if (r < 0 || r + spanY > rows || c < 0 || c + spanX > columns) return false
        for (i in r until r + spanY) {
            for (j in c until c + spanX) {
                if (occupied[i][j]) return false
            }
        }
        return true
    }
}
