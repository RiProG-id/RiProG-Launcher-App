package com.riprog.launcher.callback

interface PageActionCallback {
    fun onAddPage()
    fun onRemovePage()
    fun getPageCount(): Int
}
