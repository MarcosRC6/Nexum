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
import org.json.JSONObject

/** Cada instancia elige plantilla, tema o banco completo y su cantidad de preguntas. */
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
            saveConfig(context, newId, getConfig(context, oldId))
            removeSelection(context, oldId)
        }
        updateAllWidgets(context)
    }

    companion object {
        private const val PREFS = "nexum_quick_test_widgets"
        private fun legacyKey(id: Int) = "template_$id"
        private fun configKey(id: Int) = "selection_$id"
        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Compatibilidad con los widgets NEXUM 1.7 que sólo guardaban una plantilla. */
        fun getSelection(context: Context, widgetId: Int): String? =
            getConfig(context, widgetId).templateId

        fun saveSelection(context: Context, widgetId: Int, templateId: String?) {
            saveConfig(context, widgetId, if (templateId == null) QuickWidgetSelection()
                else QuickWidgetSelection(mode = QuickWidgetMode.TEMPLATE, templateId = templateId))
        }

        fun getConfig(context: Context, widgetId: Int): QuickWidgetSelection {
            val prefs = prefs(context)
            val raw = prefs.getString(configKey(widgetId), null)
            if (!raw.isNullOrEmpty()) {
                val config = runCatching {
                    val item = JSONObject(raw)
                    val mode = QuickWidgetMode.valueOf(item.optString("mode"))
                    QuickWidgetSelection(
                        mode = mode,
                        topicId = item.optString("topicId").takeIf { it.isNotBlank() },
                        templateId = item.optString("templateId").takeIf { it.isNotBlank() },
                        questionCount = item.optInt("questionCount", 10)
                    ).normalized()
                }.getOrNull()
                if (config != null) return config
            }
            return prefs.getString(legacyKey(widgetId), null)?.let {
                QuickWidgetSelection(mode = QuickWidgetMode.TEMPLATE, templateId = it)
            } ?: QuickWidgetSelection()
        }

        fun saveConfig(context: Context, widgetId: Int, config: QuickWidgetSelection) {
            val normalized = config.normalized()
            val json = JSONObject().apply {
                put("mode", normalized.mode.name)
                put("topicId", normalized.topicId)
                put("templateId", normalized.templateId)
                put("questionCount", normalized.questionCount)
            }
            prefs(context).edit().putString(configKey(widgetId), json.toString())
                .remove(legacyKey(widgetId)).apply()
        }

        private fun removeSelection(context: Context, id: Int) {
            prefs(context).edit().remove(configKey(id)).remove(legacyKey(id)).apply()
        }

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, NexumQuickTestWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(appContext, manager, it) }
        }

        fun updateWidget(context: Context, widgetId: Int) =
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val database = TestarioLocalStore.get(context).database.value
            val config = getConfig(context, widgetId)
            val valid = config.isValid(database)
            val views = RemoteViews(context.packageName, R.layout.widget_nexum_quick_test)

            views.setTextViewText(
                R.id.nexumQuickWidgetSelection, config.label(database)
            )
            views.setTextViewText(
                R.id.nexumQuickWidgetStart, if (valid) "Iniciar test" else "Configurar test"
            )
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val configure = PendingIntent.getActivity(
                context,
                widgetId * 8 + 1,
                Intent(context, NexumQuickTestWidgetConfigurationActivity::class.java).apply {
                    action = "com.example.estudario.widget.CONFIGURE_TEST_$widgetId"
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                },
                flags
            )
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.estudario.action.LAUNCH_QUICK_TEST_WIDGET_$widgetId"
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_START_DESTINATION_ROUTE,
                    NexumQuickTestWidgetModel.LAUNCH_ROUTE)
                if (config.mode == QuickWidgetMode.TEMPLATE) {
                    putExtra(MainActivity.EXTRA_QUICK_TEST_TEMPLATE_ID, config.templateId)
                } else {
                    putStringArrayListExtra(
                        MainActivity.EXTRA_QUICK_TEST_TOPIC_IDS,
                        ArrayList(config.topicIds(database))
                    )
                    putExtra(MainActivity.EXTRA_QUICK_TEST_COUNT, config.questionCount)
                }
            }
            val start = PendingIntent.getActivity(context, widgetId * 8, launchIntent, flags)
            views.setOnClickPendingIntent(
                R.id.nexumQuickWidgetTapArea, if (valid) start else configure
            )
            views.setOnClickPendingIntent(R.id.nexumQuickWidgetConfigure, configure)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
