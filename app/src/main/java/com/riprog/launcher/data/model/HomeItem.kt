package com.riprog.launcher.data.model

class HomeItem {
    enum class Type { APP, WIDGET, CLOCK, FOLDER }

    var id: Long = 0
    var type: Type? = null
    var packageName: String? = null
    var className: String? = null
    var row: Float = 0f
    var col: Float = 0f
    var spanX: Float = 1f
    var spanY: Float = 1f
    var page: Int = 0
    var widgetId: Int = -1
    var folderName: String? = null
    var folderItems: MutableList<HomeItem>? = null

    var rotation: Float = 0f
    var scaleX: Float = 1.0f
    var scaleY: Float = 1.0f
    var tiltX: Float = 0f
    var tiltY: Float = 0f

    companion object {
        @JvmStatic
        fun createApp(packageName: String?, className: String?, col: Float, row: Float, page: Int): HomeItem {
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
        fun createFolder(name: String?, col: Float, row: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.FOLDER
            item.folderName = name
            item.col = col
            item.row = row
            item.spanX = 1f
            item.spanY = 1f
            item.page = page
            item.folderItems = mutableListOf()
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
        return item.col.compareTo(col) == 0 && item.row.compareTo(row) == 0 && page == item.page
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (className?.hashCode() ?: 0)
        result = 31 * result + col.toBits()
        result = 31 * result + row.toBits()
        result = 31 * result + page
        result = 31 * result + widgetId
        return result
    }
}
