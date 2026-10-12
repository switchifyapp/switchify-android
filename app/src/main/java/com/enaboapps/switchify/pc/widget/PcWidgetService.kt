package com.enaboapps.switchify.pc.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.control.PcControlLink
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.storage.PcPairingStorage
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class PcWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PcWidgetItems(applicationContext, PcConnectionGraph.getInstance(applicationContext).pairings)
}

class PcWidgetItems(
    private val context: Context,
    private val pairings: PcPairingStorage
) : RemoteViewsService.RemoteViewsFactory {
    private var pcs: List<PcWidgetPc> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        pcs = runBlocking { withTimeoutOrNull(LOAD_TIMEOUT_MS) { PcWidgetPc.load(pairings) } } ?: pcs
    }

    override fun onDestroy() {
        pcs = emptyList()
    }

    override fun getCount(): Int = pcs.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.pc_widget_item)
        val pc = pcs.getOrNull(position) ?: return views
        views.setTextViewText(R.id.pc_widget_item_name, pc.saved.displayName)
        views.setTextViewText(R.id.pc_widget_item_detail, context.getString(pc.detail))
        views.setOnClickFillInIntent(
            R.id.pc_widget_item,
            Intent().setData(PcControlLink.uri(null, pc.saved.desktopId).toUri())
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = pcs.getOrNull(position)?.saved?.desktopId?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}

data class PcWidgetPc(val saved: PcSavedPc, val isDefault: Boolean) {
    val detail: Int
        get() = when {
            isDefault -> R.string.pc_widget_default
            saved.platform == PcPlatform.Windows -> R.string.pc_widget_windows
            saved.platform == PcPlatform.MacOs -> R.string.pc_widget_mac
            else -> R.string.pc_widget_paired
        }

    companion object {
        suspend fun load(pairings: PcPairingStorage): List<PcWidgetPc> {
            val saved = pairings.list()
            val defaultId = try {
                pairings.defaultDesktopId()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            return order(saved, defaultId)
        }

        fun order(saved: List<PcSavedPc>, defaultId: String?): List<PcWidgetPc> =
            saved.map { PcWidgetPc(it, it.desktopId == defaultId) }.sortedByDescending { it.isDefault }
    }
}
