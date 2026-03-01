package com.riprog.launcher.logic.callbacks

interface PageActionCallback {
    fun onAddPage()
    fun onRemovePage()
    fun getPageCount(): Int
}