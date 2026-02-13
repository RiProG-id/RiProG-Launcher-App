package com.riprog.launcher

class HomeItem {
    enum class Type {
        APP, WIDGET, CLOCK, FOLDER
    }

    @JvmField var type: Type? = null
    @JvmField var packageName: String? = null
    @JvmField var className: String? = null
    @JvmField var row: Float = 0f
    @JvmField var col: Float = 0f
    @JvmField var spanX: Int = 1
    @JvmField var spanY: Int = 1
    @JvmField var page: Int = 0
    @JvmField var widgetId: Int = -1
    @JvmField var folderName: String? = null
    @JvmField var folderItems: MutableList<HomeItem>? = null

    // Advanced Freeform Transformations
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
            item.spanX = 1
            item.spanY = 1
            item.page = page
            return item
        }

        @JvmStatic
        fun createWidget(widgetId: Int, col: Float, row: Float, spanX: Int, spanY: Int, page: Int): HomeItem {
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
        fun createClock(col: Float, row: Float, spanX: Int, spanY: Int, page: Int): HomeItem {
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
            item.spanX = 1
            item.spanY = 1
            item.page = page
            item.folderItems = mutableListOf()
            return item
        }
    }
}
