package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/* ============================================================================
 * HOME SCREEN WIDGET — canlı fiyat/VPIN/strateji (ViewModel push'lar)
 * ========================================================================== */
class BozokWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
    }

    companion object {
        fun pushSnapshot(context: Context, symbol: String, price: String, vpinText: String, strategyText: String, returnText: String) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BozokWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            for (id in ids) updateAppWidget(context, manager, id, symbol, price, vpinText, strategyText, returnText)
        }

        fun updateAppWidget(
            context: Context, manager: AppWidgetManager, id: Int,
            symbol: String = "BTCUSDT", price: String = "—", vpinText: String = "VPIN —",
            strategyText: String = "BEKLENİYOR", returnText: String = "R:R —"
        ) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val views = RemoteViews(context.packageName, R.layout.widget_bozok_layout).apply {
                setTextViewText(R.id.widget_symbol, symbol)
                setTextViewText(R.id.widget_price, price)
                setTextViewText(R.id.widget_vpin, vpinText)
                setTextViewText(R.id.widget_strategy, strategyText)
                setTextViewText(R.id.widget_return, returnText)
                setOnClickPendingIntent(R.id.widget_container, pi)
            }
            manager.updateAppWidget(id, views)
        }
    }
}
