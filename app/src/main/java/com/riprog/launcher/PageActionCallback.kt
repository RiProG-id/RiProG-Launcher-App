package com.riprog.launcher

interface PageActionCallback {
    fun onAddPage()
    fun onRemovePage()
    fun getPageCount(): Int
}
