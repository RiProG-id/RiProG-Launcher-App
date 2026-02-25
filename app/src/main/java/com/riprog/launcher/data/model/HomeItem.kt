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

    var spanX: Int = 1

    var spanY: Int = 1

    var page: Int = 0

    var originalRow: Float = 0f
    var originalCol: Float = 0f
    var originalSpanX: Int = 1
    var originalSpanY: Int = 1
    var originalPage: Int = 0

    var widgetId: Int = -1


    var rotation: Float = 0f

    var scale: Float = 1.0f

    var tiltX: Float = 0f

    var tiltY: Float = 0f

    companion object {

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
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = 1
            item.originalSpanY = 1
            item.originalPage = page
            return item
        }


        fun createWidget(widgetId: Int, col: Float, row: Float, spanX: Int, spanY: Int, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.WIDGET
            item.widgetId = widgetId
            item.col = col
            item.row = row
            item.spanX = spanX
            item.spanY = spanY
            item.page = page
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = spanX
            item.originalSpanY = spanY
            item.originalPage = page
            return item
        }


        fun createClock(col: Float, row: Float, spanX: Int, spanY: Int, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.CLOCK
            item.col = col
            item.row = row
            item.spanX = spanX
            item.spanY = spanY
            item.page = page
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = spanX
            item.originalSpanY = spanY
            item.originalPage = page
            return item
        }


        fun createFolder(name: String, col: Float, row: Float, page: Int): HomeItem {
            val item = HomeItem()
            item.type = Type.FOLDER
            item.folderName = name
            item.col = col
            item.row = row
            item.spanX = 1
            item.spanY = 1
            item.page = page
            item.originalCol = col
            item.originalRow = row
            item.originalSpanX = 1
            item.originalSpanY = 1
            item.originalPage = page
            return item
        }
    }
}
