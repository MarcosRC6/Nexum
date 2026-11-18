package com.example.testario.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.testario.MainActivity
import com.example.testario.R
import com.example.testario.data.local.TestarioLocalStore

/** Android launcher widget; no service, timer or notifications are created. */
class NexumQuickTestWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { removeSelection(context, it) }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            saveSelection(context, newId, getSelection(context, oldId))
            removeSelection(context, oldId)
        }
        updateAllWidgets(context)
    }

    companion object {
        private const val PREFS = "nexum_quick_test_widgets"

        private fun key(widgetId: Int) = "template_" + widgetId

        fun getSelection(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key(widgetId), null)

        fun saveSelection(context: Context, widgetId: Int, templateId: String?) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (templateId == null) editor.remove(key(widgetId))
            else editor.putString(key(widgetId), templateId)
            editor.apply()
        }

        private fun removeSelection(context: Context, widgetId: Int) {
            saveSelection(context, widgetId, null)
        }

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, NexumQuickTestWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(appContext, manager, it) }
        }

        fun updateWidget(context: Context, widgetId: Int) {
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
        }

        private fun updateWidget(
            context: Context, manager: AppWidgetManager, widgetId: Int
        ) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

            val templates = TestarioLocalStore.get(context).database.value.testTemplates
            val selectedId = getSelection(context, widgetId)
            val valid = NexumQuickTestWidgetModel.isSelectionValid(templates, selectedId)
            val views = RemoteViews(context.packageName, R.layout.widget_nexum_quick_test)

            views.setTextViewText(
                R.id.nexumQuickWidgetSelection,
                NexumQuickTestWidgetModel.label(templates, selectedId)
            )
            views.setTextViewText(
                R.id.nexumQuickWidgetStart,
                if (valid) "Iniciar test" else "Elegir plantilla"
            )

            val configureIntent = Intent(
                context, NexumQuickTestWidgetConfigurationActivity::class.java
            ).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val configure = PendingIntent.getActivity(
                context, widgetId * 2 + 1, configureIntent, flags
            )
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.estudario.action.LAUNCH_QUICK_TEST_WIDGET"
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_START_DESTINATION_ROUTE, NexumQuickTestWidgetModel.LAUNCH_ROUTE)
                if (selectedId != null) putExtra(MainActivity.EXTRA_QUICK_TEST_TEMPLATE_ID, selectedId)
            }
            val start = PendingIntent.getActivity(
                context, widgetId * 2, launchIntent, flags
            )
            views.setOnClickPendingIntent(
                R.id.nexumQuickWidgetTapArea, if (valid) start else configure
            )
            views.setOnClickPendingIntent(R.id.nexumQuickWidgetConfigure, configure)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
