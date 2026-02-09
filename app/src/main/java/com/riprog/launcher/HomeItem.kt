package com.riprog.launcher

class HomeItem {
    enum class Type {
        APP, WIDGET, CLOCK
    }

    @JvmField
    var type: Type? = null
    @JvmField
    var packageName: String? = null
    @JvmField
    var className: String? = null
    @JvmField
    var row = 0f
    @JvmField
    var col = 0f
    @JvmField
    var spanX = 1
    @JvmField
    var spanY = 1
    @JvmField
    var page = 0
    @JvmField
    var widgetId = -1

    constructor()

    companion object {
        @JvmStatic
        fun createApp(packageName: String?, className: String?, col: Float, row: Float, page: Int): HomeItem {
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
    }
}
