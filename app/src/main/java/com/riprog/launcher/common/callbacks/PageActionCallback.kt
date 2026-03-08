package com.riprog.launcher.common.callbacks

interface PageActionCallback {
    fun onAddPage()
    fun onRemovePage()
    fun getPageCount(): Int
}