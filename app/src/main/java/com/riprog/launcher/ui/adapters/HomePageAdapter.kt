package com.riprog.launcher.ui.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.ui.activities.MainActivity

class HomePageAdapter(
    private val activity: MainActivity,
    private val settingsManager: SettingsManager,
    private val callback: Callback
) : RecyclerView.Adapter<HomePageAdapter.ViewHolder>() {

    interface Callback {
        fun onItemClick(item: HomeItem, view: View)
        fun onItemLongClick(item: HomeItem, view: View): Boolean
    }

    private var pages: List<List<HomeItem>> = ArrayList()

    fun setPages(newPages: List<List<HomeItem>>) {
        this.pages = newPages
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val recyclerView = RecyclerView(parent.context)
        recyclerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        recyclerView.clipChildren = false
        recyclerView.clipToPadding = false
        // We will set layout manager and adapter in onBind
        return ViewHolder(recyclerView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pageItems = pages[position]
        val rv = holder.itemView as RecyclerView

        val layoutManager = GridLayoutManager(rv.context, settingsManager.columns)
        rv.layoutManager = layoutManager

        val adapter = UnifiedLauncherAdapter(settingsManager, activity.model, object : UnifiedLauncherAdapter.Callback {
            override fun onItemClick(item: Any, view: View) {
                if (item is HomeItem) callback.onItemClick(item, view)
            }

            override fun onItemLongClick(item: Any, view: View): Boolean {
                return if (item is HomeItem) callback.onItemLongClick(item, view) else false
            }
        })
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val item = pageItems.getOrNull(position)
                return if (item is HomeItem) item.spanX else 1
            }
        }
        rv.adapter = adapter
        adapter.setItems(pageItems)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
