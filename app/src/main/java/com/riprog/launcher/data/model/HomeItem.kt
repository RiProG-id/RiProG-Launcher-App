package com.riprog.launcher.data.model

class HomeItem {
    enum class Type { APP, WIDGET, CLOCK, FOLDER }


    var type: Type? = null

    var packageName: String? = null

    var className: String? = null

    var folderName: String? = null

    var folderItems: MutableList<HomeItem> = mutableListOf()

    var row: Float = 0f

    var col: Float = 0f

    var spanX: Float = 1f

    var spanY: Float = 1f

    var page: Int = 0

    var originalRow: Float = 0f
    var originalCol: Float = 0f
    var originalSpanX: Float = 1f
    var originalSpanY: Float = 1f
    var originalPage: Int = 0

    var widgetId: Int = -1


    var rotation: Float = 0f

    var scale: Float = 1.0f

    var tiltX: Float = 0f

    var tiltY: Float = 0f

    var isLayoutLocked: Boolean = false

    companion object {

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
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = 1f
            item.originalSpanY = 1f
            item.originalPage = page
            return item
        }


        fun createWidget(widgetId: Int, col: Float, row: Float, spanX: Int, spanY: Int, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.WIDGET
            item.widgetId = widgetId
            item.col = col
            item.row = row
            item.spanX = spanX.toFloat()
            item.spanY = spanY.toFloat()
            item.page = page
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = spanX.toFloat()
            item.originalSpanY = spanY.toFloat()
            item.originalPage = page
            return item
        }


        fun createClock(col: Float, row: Float, spanX: Int, spanY: Int, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.CLOCK
            item.col = col
            item.row = row
            item.spanX = spanX.toFloat()
            item.spanY = spanY.toFloat()
            item.page = page
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = spanX.toFloat()
            item.originalSpanY = spanY.toFloat()
            item.originalPage = page
            return item
        }


        fun createFolder(name: String, col: Float, row: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.FOLDER
            item.folderName = name
            item.col = col
            item.row = row
            item.spanX = 1f
            item.spanY = 1f
            item.page = page
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = 1f
            item.originalSpanY = 1f
            item.originalPage = page
            return item
        }
    }
}
