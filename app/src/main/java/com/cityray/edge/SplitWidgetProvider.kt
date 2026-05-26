package com.cityray.edge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SplitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context)) }
    }

    companion object {
        private val buttonIds = listOf(R.id.widget_button_1, R.id.widget_button_2, R.id.widget_button_3, R.id.widget_button_4)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SplitWidgetProvider::class.java))
            ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val repo = PairRepository(context)
            val pairs = repo.favorites().filter { it.showInWidget }.take(4)
            val remote = RemoteViews(context.packageName, R.layout.widget_split_favorites)
            buttonIds.forEachIndexed { index, buttonId ->
                val pair = pairs.getOrNull(index)
                remote.setTextViewText(buttonId, pair?.name ?: "Open Edge Pro")
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = if (pair == null || pair.leftPackage.isBlank() || pair.rightPackage.isBlank()) Intent.ACTION_MAIN else CityRayActions.ACTION_LAUNCH_PAIR
                    putExtra(CityRayActions.EXTRA_PAIR_INDEX, repo.favorites().indexOf(pair).coerceAtLeast(0))
                }
                remote.setOnClickPendingIntent(buttonId, PendingIntent.getActivity(context, 200 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            return remote
        }
    }
}
