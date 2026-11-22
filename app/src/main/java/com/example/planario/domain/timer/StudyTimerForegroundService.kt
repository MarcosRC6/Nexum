package com.example.planario.domain.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.res.ResourcesCompat
import com.example.testario.MainActivity
import com.example.testario.R
import com.example.planario.app.PlanarioDependencies
import com.example.planario.app.widget.NexumStudyClockWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StudyTimerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var observerJob: Job? = null
    private var autoCloseObserverJob: Job? = null
    private var lastPublishedTimerState: StudyTimerState? = null
    private var lastPublishedPausedSeconds: Long = -1L
    private var lastPublishedPomodoroState: PomodoroTimerState? = null
    private var lastPublishedPomodoroSeconds: Long = -1L
    private var lastWidgetStudyState: StudyTimerState? = null
    private var lastWidgetPomodoroState: PomodoroTimerState? = null

    override fun onCreate() {
        super.onCreate()

        PlanarioDependencies.init(applicationContext)
        PlanarioDependencies.studyTimerController.attachContext(applicationContext)

        createNotificationChannel()
        recoverPendingTimerIfNeeded()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        recoverPendingTimerIfNeeded()

        return when (intent?.action) {
            ACTION_START -> {
                startForegroundNow()
                observeTimer()
                observeAutoClosedSessions()
                START_STICKY
            }

            ACTION_PAUSE -> {
                PlanarioDependencies.studyTimerController.pauseStudyTimer()
                PlanarioDependencies.studyRepository.pauseActiveStudyTimerTracking()
                startForegroundNow()
                observeTimer()
                observeAutoClosedSessions()
                START_STICKY
            }

            ACTION_RESUME -> {
                val controller = PlanarioDependencies.studyTimerController
                if (!controller.hasPendingStudyTimerCompletion()) {
                    controller.resumeStudyTimer()
                    PlanarioDependencies.studyRepository.resumeActiveStudyTimerTracking()
                }
                startForegroundNow()
                observeTimer()
                observeAutoClosedSessions()
                START_STICKY
            }

            ACTION_STOP -> {
                // A stop action must finish and save the clock, not merely hide
                // its notification while the timer continues unobserved.
                PlanarioDependencies.finishStudyTimerAndSave()
                val afterStop = PlanarioDependencies.studyTimerController.state.value
                if (afterStop.studyTimerState != StudyTimerState.STOPPED ||
                    afterStop.pomodoroTimerState == PomodoroTimerState.RUNNING ||
                    afterStop.pomodoroTimerState == PomodoroTimerState.PAUSED) {
                    startForegroundNow()
                    NexumStudyClockWidgetReceiver.updateAllWidgets(applicationContext)
                    START_STICKY
                } else {
                    stopServiceSafely()
                    START_NOT_STICKY
                }
            }

            else -> {
                startForegroundNow()
                observeTimer()
                observeAutoClosedSessions()
                START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the task away is different from pressing Back. Android cannot
        // show a dialog after task removal; preserve the running timer instead.
        PlanarioDependencies.studyTimerController.persistCurrentStudyTimerNow()
        PlanarioDependencies.studyTimerController.persistCurrentPomodoroNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        PlanarioDependencies.studyTimerController.persistCurrentStudyTimerNow()
        PlanarioDependencies.studyTimerController.persistCurrentPomodoroNow()

        observerJob?.cancel()
        observerJob = null

        autoCloseObserverJob?.cancel()
        autoCloseObserverJob = null

        runCatching {
            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    private fun recoverPendingTimerIfNeeded() {
        PlanarioDependencies.recoverPendingStudyTimerIfNeeded()
    }

    private fun startForegroundNow() {
        val notification = buildNotification(
            state = PlanarioDependencies.studyTimerController.state.value
        )

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )
        val state = PlanarioDependencies.studyTimerController.state.value
        lastPublishedTimerState = state.studyTimerState
        lastPublishedPausedSeconds = state.studyElapsedSeconds
        lastPublishedPomodoroState = state.pomodoroTimerState
        lastPublishedPomodoroSeconds = state.pomodoroRemainingSeconds
        NexumStudyClockWidgetReceiver.updateAllWidgets(applicationContext)
    }

    private fun observeTimer() {
        if (observerJob != null) return

        observerJob = serviceScope.launch {
            PlanarioDependencies.studyTimerController.state.collectLatest { state ->
                if (state.studyTimerState != lastWidgetStudyState ||
                    state.pomodoroTimerState != lastWidgetPomodoroState) {
                    lastWidgetStudyState = state.studyTimerState
                    lastWidgetPomodoroState = state.pomodoroTimerState
                    NexumStudyClockWidgetReceiver.updateAllWidgets(applicationContext)
                }
                if (!StudyForegroundPolicy.shouldKeepRunning(
                        state.studyTimerState, state.pomodoroTimerState)) {
                    stopServiceSafely()
                } else {
                    updateNotification(state)
                }
            }
        }
    }

    private fun observeAutoClosedSessions() {
        if (autoCloseObserverJob != null) return

        autoCloseObserverJob = serviceScope.launch {
            PlanarioDependencies.studyTimerController.autoClosedStudyTimerSessions.collectLatest { session ->
                // Journal was persisted before this SharedFlow event. If a
                // collector is cancelled, recovery replays the same UUID.
                PlanarioDependencies.completeStagedStudyTimer(session)
            }
        }
    }

    private fun updateNotification(state: StudyTimerControllerState) {
        // Android's chronometer animates independently, even if background
        // notification updates are throttled. Rebuild on state transitions only.
        if (lastPublishedTimerState == state.studyTimerState &&
            lastPublishedPomodoroState == state.pomodoroTimerState &&
            (state.studyTimerState == StudyTimerState.RUNNING ||
                lastPublishedPausedSeconds == state.studyElapsedSeconds) &&
            (state.pomodoroTimerState == PomodoroTimerState.RUNNING ||
                lastPublishedPomodoroSeconds == state.pomodoroRemainingSeconds)) return
        lastPublishedTimerState = state.studyTimerState
        lastPublishedPausedSeconds = state.studyElapsedSeconds
        lastPublishedPomodoroState = state.pomodoroTimerState
        lastPublishedPomodoroSeconds = state.pomodoroRemainingSeconds
        val notification = buildNotification(state)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            notification
        )
    }

    private fun buildNotification(
        state: StudyTimerControllerState
    ): Notification {
        val title = when (state.studyTimerState) {
            StudyTimerState.RUNNING -> "Estudio en curso"
            StudyTimerState.PAUSED -> "Estudio pausado"
            StudyTimerState.STOPPED -> if (state.pomodoroTimerState == PomodoroTimerState.RUNNING)
                "Pomodoro en marcha" else "Pomodoro pausado"
        }

        // El cronómetro del sistema ya muestra los segundos en vivo.
        // No repetir un texto explicativo que ocupa espacio en la notificación.
        val content = when {
            state.studyTimerState == StudyTimerState.RUNNING -> null
            state.studyTimerState == StudyTimerState.PAUSED ->
                "Tiempo: ${state.studyElapsedSeconds.toTimerText()}"
            else -> "Restante: ${state.pomodoroRemainingSeconds.toTimerText()}"
        }
        val isStudyRunning = state.studyTimerState == StudyTimerState.RUNNING
        val isPomoRunning = state.studyTimerState == StudyTimerState.STOPPED &&
            state.pomodoroTimerState == PomodoroTimerState.RUNNING

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(getLargeNotificationIcon())
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(state.studyTimerState != StudyTimerState.STOPPED ||
                state.pomodoroTimerState == PomodoroTimerState.RUNNING)
            .setOnlyAlertOnce(true)
            .setShowWhen(isStudyRunning || isPomoRunning)
            .setUsesChronometer(isStudyRunning || isPomoRunning)
            .setChronometerCountDown(isPomoRunning)
            .setWhen(if (isPomoRunning)
                System.currentTimeMillis() + state.pomodoroRemainingSeconds * 1000L
                else System.currentTimeMillis() -
                    state.studyElapsedSeconds.coerceAtMost(86400L * 365L) * 1000L)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(createOpenAppPendingIntent())

        when (state.studyTimerState) {
            StudyTimerState.RUNNING -> {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pausar",
                    createServiceActionPendingIntent(ACTION_PAUSE)
                )
            }

            StudyTimerState.PAUSED -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Reanudar",
                    createServiceActionPendingIntent(ACTION_RESUME)
                )
            }

            StudyTimerState.STOPPED -> Unit
        }

        if (state.studyTimerState != StudyTimerState.STOPPED) {
            // Also available after the user swipes the Activity from Recents.
            // Android cannot show the in-app Back confirmation in that case.
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Guardar tiempo",
                createServiceActionPendingIntent(ACTION_STOP)
            )
        }

        return builder.build()
    }

    private fun getLargeNotificationIcon(): Bitmap? {
        val drawable = ResourcesCompat.getDrawable(
            resources,
            R.mipmap.ic_launcher,
            theme
        ) ?: return null

        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) {
            drawable.intrinsicWidth
        } else {
            128
        }

        val height = if (drawable.intrinsicHeight > 0) {
            drawable.intrinsicHeight
        } else {
            128
        }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        drawable.setBounds(
            0,
            0,
            canvas.width,
            canvas.height
        )

        drawable.draw(canvas)

        return bitmap
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            intent,
            flags
        )
    }

    private fun createServiceActionPendingIntent(
        action: String
    ): PendingIntent {
        val intent = Intent(
            this,
            StudyTimerForegroundService::class.java
        ).apply {
            this.action = action
        }

        val requestCode = when (action) {
            ACTION_PAUSE -> PAUSE_REQUEST_CODE
            ACTION_RESUME -> RESUME_REQUEST_CODE
            ACTION_STOP -> STOP_REQUEST_CODE
            else -> START_REQUEST_CODE
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            flags
        )
    }

    private fun stopServiceSafely() {
        observerJob?.cancel()
        observerJob = null

        autoCloseObserverJob?.cancel()
        autoCloseObserverJob = null

        runCatching {
            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )
        }

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cronómetro de estudio",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Muestra el cronómetro de estudio mientras está activo."
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.planario.action.START_STUDY_TIMER_SERVICE"
        const val ACTION_PAUSE = "com.example.planario.action.PAUSE_STUDY_TIMER_SERVICE"
        const val ACTION_RESUME = "com.example.planario.action.RESUME_STUDY_TIMER_SERVICE"
        const val ACTION_STOP = "com.example.planario.action.STOP_STUDY_TIMER_SERVICE"

        private const val CHANNEL_ID = "study_timer_channel"
        private const val NOTIFICATION_ID = 1001

        private const val OPEN_APP_REQUEST_CODE = 2001
        private const val START_REQUEST_CODE = 2002
        private const val PAUSE_REQUEST_CODE = 2003
        private const val RESUME_REQUEST_CODE = 2004
        private const val STOP_REQUEST_CODE = 2005
    }
}

private fun Long.toTimerText(): String {
    val hours = this / 3600L
    val minutes = (this % 3600L) / 60L
    val seconds = this % 60L

    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
