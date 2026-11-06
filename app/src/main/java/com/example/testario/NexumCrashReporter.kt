package com.example.testario

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.time.Instant

/**
 * Captures the fatal stack trace locally so a startup crash cannot leave the
 * user with no way to report or reopen the app. Does not copy study databases,
 * questions, answers or any backups. Sharing is always a user action.
 */
object NexumCrashReporter {
    private const val PREFS_NAME = "nexum_crash_diagnostics"
    private const val KEY_LAST_CRASH = "last_fatal_crash"
    private const val MAX_CHARS = 18_000
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    save(appContext, error)
                } catch (_: Throwable) {
                    // Never replace the real crash with a diagnostics exception.
                }
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun lastCrash(context: Context): String? =
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_CRASH, null)
        }.getOrNull()

    fun save(context: Context, error: Throwable) {
        val report = buildString {
            append("NEXUM — cierre inesperado\n")
            append(Instant.now().toString())
            append("\nAndroid: ")
            append(android.os.Build.VERSION.SDK_INT)
            append("\n\n")
            append(Log.getStackTraceString(error).take(MAX_CHARS))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_CRASH, report).commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LAST_CRASH).commit()
    }

    fun share(context: Context, report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Informe de cierre de NEXUM")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir informe de NEXUM"))
    }
}
