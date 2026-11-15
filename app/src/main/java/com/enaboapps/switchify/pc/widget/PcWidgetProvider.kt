package com.enaboapps.switchify.pc.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.control.PcControlLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PcWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            onUpdate(context, manager, widgetIds(context, manager))
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAsync(context.applicationContext, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAsync(context.applicationContext, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun updateAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                update(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_OPEN = 0
        private const val REQUEST_OPEN_PC = 1
        private const val REQUEST_OPEN_BUTTON = 2
        private const val LOAD_TIMEOUT_MS = 5_000L
        private const val EXTRA_VERSION_CODE = "com.enaboapps.switchify.pc.widget.VERSION_CODE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            update(context.applicationContext, manager, widgetIds(context, manager))
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val pairings = PcConnectionGraph.getInstance(context).pairings
            val pcs = withTimeoutOrNull(LOAD_TIMEOUT_MS) { PcWidgetPc.load(pairings) } ?: emptyList()
            ids.forEach { manager.updateAppWidget(it, views(context, manager, it, pcs)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.pc_widget_list)
        }

        private fun views(context: Context, manager: AppWidgetManager, appWidgetId: Int, pcs: List<PcWidgetPc>): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return RemoteViews(
                    PcWidgetLayout.entries.associate { layout ->
                        SizeF(layout.minWidthDp, layout.minHeightDp) to views(context, appWidgetId, layout, pcs)
                    }
                )
            }
            val options = manager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val layout = if (width == 0 || height == 0) {
                PcWidgetLayout.AllPcs
            } else {
                PcWidgetLayout.forSize(width.toFloat(), height.toFloat())
            }
            return views(context, appWidgetId, layout, pcs)
        }

        private fun views(context: Context, appWidgetId: Int, layout: PcWidgetLayout, pcs: List<PcWidgetPc>): RemoteViews =
            when (layout) {
                PcWidgetLayout.AllPcs -> listViews(context, appWidgetId)
                PcWidgetLayout.OnePc -> RemoteViews(context.packageName, R.layout.pc_widget_small).apply {
                    bindButton(context, R.id.pc_widget_first, R.id.pc_widget_first_name, pcs.firstOrNull())
                }
                PcWidgetLayout.TwoPcs -> RemoteViews(context.packageName, R.layout.pc_widget_pair).apply {
                    bindButton(context, R.id.pc_widget_first, R.id.pc_widget_first_name, pcs.firstOrNull())
                    val second = pcs.getOrNull(1)
                    setViewVisibility(R.id.pc_widget_second, if (second == null) View.GONE else View.VISIBLE)
                    if (second != null) bindButton(context, R.id.pc_widget_second, R.id.pc_widget_second_name, second)
                }
            }

        private fun RemoteViews.bindButton(context: Context, buttonId: Int, nameId: Int, pc: PcWidgetPc?) {
            if (pc == null) {
                setTextViewText(nameId, context.getString(R.string.pc_widget_set_up))
                setContentDescription(buttonId, context.getString(R.string.pc_widget_empty))
                setOnClickPendingIntent(buttonId, openPcControl(context))
                return
            }
            setTextViewText(nameId, pc.saved.displayName)
            setContentDescription(buttonId, "${pc.saved.displayName}, ${context.getString(pc.detail)}")
            setOnClickPendingIntent(
                buttonId,
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN_BUTTON,
                    PcControlLink.intent(context, null, pc.saved.desktopId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        @Suppress("DEPRECATION")
        private fun listViews(context: Context, appWidgetId: Int): RemoteViews {
            val items = Intent(context, PcWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE)
            items.data = items.toUri(Intent.URI_INTENT_SCHEME).toUri()
            val openPc = PendingIntent.getActivity(
                context,
                REQUEST_OPEN_PC,
                PcControlLink.intent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val openPcControl = openPcControl(context)
            return RemoteViews(context.packageName, R.layout.pc_widget).apply {
                setRemoteAdapter(R.id.pc_widget_list, items)
                setEmptyView(R.id.pc_widget_list, R.id.pc_widget_empty)
                setPendingIntentTemplate(R.id.pc_widget_list, openPc)
                setOnClickPendingIntent(R.id.pc_widget_header, openPcControl)
                setOnClickPendingIntent(R.id.pc_widget_empty, openPcControl)
            }
        }

        private fun openPcControl(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            PcControlLink.intent(context, null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun widgetIds(context: Context, manager: AppWidgetManager): IntArray =
            manager.getAppWidgetIds(ComponentName(context, PcWidgetProvider::class.java))
    }
}

enum class PcWidgetLayout(val minWidthDp: Float, val minHeightDp: Float) {
    OnePc(40f, 40f),
    TwoPcs(130f, 40f),
    AllPcs(110f, 100f);

    companion object {
        fun forSize(widthDp: Float, heightDp: Float): PcWidgetLayout = when {
            widthDp >= AllPcs.minWidthDp && heightDp >= AllPcs.minHeightDp -> AllPcs
            widthDp >= TwoPcs.minWidthDp -> TwoPcs
            else -> OnePc
        }
    }
}
