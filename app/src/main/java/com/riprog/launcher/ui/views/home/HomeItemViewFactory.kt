package com.riprog.launcher.ui.views.home

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.riprog.launcher.R
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.logic.managers.SettingsManager
import java.util.Calendar

class HomeItemViewFactory(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val model: AppRepository,
    private val appWidgetManager: AppWidgetManager,
    private val appWidgetHost: AppWidgetHost
) {

    fun createAppView(item: HomeItem, allApps: List<AppItem>): View {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER

        val iconView = ImageView(context)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        val baseSize = context.resources.getDimensionPixelSize(R.dimen.grid_icon_size)
        val scale = settingsManager.iconScale
        val size = (baseSize * scale).toInt()

        val iconParams = LinearLayout.LayoutParams(size, size)
        iconView.layoutParams = iconParams

        val labelView = TextView(context)
        labelView.setTextColor(context.getColor(R.color.foreground))
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10 * scale)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 1
        labelView.ellipsize = TextUtils.TruncateAt.END

        val packageName = item.packageName
        if (packageName != null) {
            val app = allApps.find { it.packageName == packageName }
            if (app != null) {
                model.loadIcon(app) { bitmap -> iconView.setImageBitmap(bitmap) }
                labelView.text = app.label
            } else {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                labelView.text = "..."
            }
        }

        container.addView(iconView)
        container.addView(labelView)
        if (settingsManager.isHideLabels) {
            labelView.visibility = View.GONE
        }
        return container
    }

    fun createClockView(): View {
        val clockRoot = LinearLayout(context)
        clockRoot.orientation = LinearLayout.VERTICAL
        clockRoot.gravity = Gravity.CENTER

        val tvTime = TextView(context)
        tvTime.textSize = 64f
        tvTime.setTextColor(context.getColor(R.color.foreground))
        tvTime.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)

        val tvDate = TextView(context)
        tvDate.textSize = 18f
        tvDate.setTextColor(context.getColor(R.color.foreground_dim))
        tvDate.gravity = Gravity.CENTER

        clockRoot.addView(tvTime)
        clockRoot.addView(tvDate)

        val updateTask: Runnable = object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                tvTime.text = DateFormat.getTimeFormat(context).format(cal.time)
                tvDate.text = DateFormat.getMediumDateFormat(context).format(cal.time)
                tvTime.postDelayed(this, 10000)
            }
        }
        tvTime.post(updateTask)
        return clockRoot
    }

    fun createWidgetView(item: HomeItem): View? {
        val info = appWidgetManager.getAppWidgetInfo(item.widgetId) ?: return null
        return try {
            val hostView = appWidgetHost.createView(context, item.widgetId, info)
            hostView.setAppWidget(item.widgetId, info)
            hostView
        } catch (e: Exception) {
            null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
