package com.enaboapps.switchify.pc.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.control.PcControlLink

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
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views(context, it)) }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.pc_widget_list)
    }

    @Suppress("DEPRECATION")
    private fun views(context: Context, appWidgetId: Int): RemoteViews {
        val items = Intent(context, PcWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        items.data = items.toUri(Intent.URI_INTENT_SCHEME).toUri()
        val openPcControl = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            PcControlLink.intent(context, null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPc = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_PC,
            PcControlLink.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return RemoteViews(context.packageName, R.layout.pc_widget).apply {
            setRemoteAdapter(R.id.pc_widget_list, items)
            setEmptyView(R.id.pc_widget_list, R.id.pc_widget_empty)
            setPendingIntentTemplate(R.id.pc_widget_list, openPc)
            setOnClickPendingIntent(R.id.pc_widget_header, openPcControl)
            setOnClickPendingIntent(R.id.pc_widget_empty, openPcControl)
        }
    }

    companion object {
        private const val REQUEST_OPEN = 0
        private const val REQUEST_OPEN_PC = 1

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = widgetIds(context, manager)
            if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.pc_widget_list)
        }

        private fun widgetIds(context: Context, manager: AppWidgetManager): IntArray =
            manager.getAppWidgetIds(ComponentName(context, PcWidgetProvider::class.java))
    }
}
