package com.riprog.launcher
import java.util.ArrayList
class HomeItem {
    enum class Type { APP, WIDGET, CLOCK, FOLDER }
    @JvmField var type: Type? = null
    @JvmField var packageName: String? = null
    @JvmField var className: String? = null
    @JvmField var row: Float = 0f
    @JvmField var col: Float = 0f
    @JvmField var spanX: Float = 1f
    @JvmField var spanY: Float = 1f
    @JvmField var page: Int = 0
    @JvmField var widgetId: Int = -1
    @JvmField var folderName: String? = null
    @JvmField var folderItems: MutableList<HomeItem>? = null
    @JvmField var rotation: Float = 0f
    @JvmField var scaleX: Float = 1.0f
    @JvmField var scaleY: Float = 1.0f
    @JvmField var tiltX: Float = 0f
    @JvmField var tiltY: Float = 0f
    constructor()
    companion object {
        @JvmStatic
        fun createApp(packageName: String, className: String, col: Float, row: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.APP
            item.packageName = packageName
            item.className = className
            item.col = col
            item.row = row
            item.spanX = 1f
            item.spanY = 1f
            item.page = page
            return item
        }
        @JvmStatic
        fun createWidget(widgetId: Int, col: Float, row: Float, spanX: Float, spanY: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.WIDGET
            item.widgetId = widgetId
            item.col = col
            item.row = row
            item.spanX = spanX
            item.spanY = spanY
            item.page = page
            return item
        }
        @JvmStatic
        fun createClock(col: Float, row: Float, spanX: Float, spanY: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.CLOCK
            item.col = col
            item.row = row
            item.spanX = spanX
            item.spanY = spanY
            item.page = page
            return item
        }
        @JvmStatic
        fun createFolder(name: String, col: Float, row: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.FOLDER
            item.folderName = name
            item.col = col
            item.row = row
            item.spanX = 1f
            item.spanY = 1f
            item.page = page
            item.folderItems = ArrayList()
            return item
        }
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val item = other as HomeItem
        if (type != item.type) return false
        if (widgetId != -1 && widgetId == item.widgetId) return true
        if (packageName != item.packageName) return false
        if (className != item.className) return false
        return item.col == col && item.row == row && page == item.page
    }
    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (className?.hashCode() ?: 0)
        result = 31 * result + col.hashCode()
        result = 31 * result + row.hashCode()
        result = 31 * result + page
        result = 31 * result + widgetId
        return result
    }
}
