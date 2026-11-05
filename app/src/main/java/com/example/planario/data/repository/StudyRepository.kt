package com.example.planario.data.repository

import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.FixedObjective
import com.example.planario.core.model.StudyDay
import com.example.planario.core.model.StudySettings
import com.example.planario.core.model.StudyTopic
import com.example.planario.core.model.ThemeMode
import com.example.planario.core.model.TimeEntry
import com.example.planario.core.model.TimeEntryType
import com.example.planario.data.local.StoredStudyData
import com.example.planario.data.local.StudyLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class StudyRepository(
    private val localDataSource: StudyLocalDataSource
) {

    private val storedData = localDataSource.load()

    private val _topics = MutableStateFlow(storedData.topics)
    val topics: StateFlow<List<StudyTopic>> = _topics.asStateFlow()

    private val _fixedObjectives = MutableStateFlow(storedData.fixedObjectives)
    val fixedObjectives: StateFlow<List<FixedObjective>> = _fixedObjectives.asStateFlow()

    private val _studyDays = MutableStateFlow(storedData.studyDays)
    val studyDays: StateFlow<Map<LocalDate, StudyDay>> = _studyDays.asStateFlow()

    private val _settings = MutableStateFlow(storedData.settings)
    val settings: StateFlow<StudySettings> = _settings.asStateFlow()

    private val _timeEntries = MutableStateFlow(storedData.timeEntries)
    val timeEntries: StateFlow<List<TimeEntry>> = _timeEntries.asStateFlow()

    val activeTopic: Flow<StudyTopic?> =
        _topics
            .map { topics ->
                topics
                    .filter { !it.isArchived }
                    .sortedBy { it.queuePosition }
                    .firstOrNull()
            }
            .distinctUntilChanged()

    val enabledFixedObjectives: Flow<List<FixedObjective>> =
        _fixedObjectives
            .map { objectives ->
                objectives
                    .filter { it.isEnabled }
                    .sortedBy { it.order }
            }
            .distinctUntilChanged()

    fun addTopic(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        val currentTopics = _topics.value
        val activeQueue = currentTopics
            .filter { !it.isArchived }
            .sortedBy { it.queuePosition }

        val newTopic = StudyTopic(
            name = cleanName,
            queuePosition = activeQueue.size,
            round = 1,
            isArchived = false
        )

        /*
         * Un tema nuevo todavía no se ha visto ninguna vez.
         *
         * Si ya hay temas "reciclados" al final de la cola (temas que ya
         * han avanzado de vuelta respecto a lo que todavía queda pendiente),
         * el nuevo tema
         * se inserta justo antes de ese bloque. De esta forma entra al final
         * de lo que todavía queda por estudiar en la vuelta actual y nunca
         * se vuelve a un tema ya terminado antes de pasar por el tema nuevo.
         *
         * Si todavía no hay ningún tema reciclado, simplemente se añade al
         * final de la cola sin interrumpir el tema activo.
         */
        val remainingQueue = activeQueue.drop(1)

        /*
         * El tema nuevo empieza siempre en Vuelta 1. Se coloca después del
         * tema que ya está activo, pero antes del primer tema de la cola que
         * ya esté esperando una vuelta posterior.
         *
         * Ejemplo:
         * A V1 (activo) -> B V1 -> C V1 -> D V2 -> E V2
         * al añadir X queda:
         * A V1 -> B V1 -> C V1 -> X V1 -> D V2 -> E V2
         *
         * Si todos siguen en Vuelta 1, se añade simplemente al final.
         */
        val firstAlreadyRepeatedIndex = remainingQueue.indexOfFirst { topic ->
            topic.round > newTopic.round
        }

        val insertionIndex = if (firstAlreadyRepeatedIndex >= 0) {
            firstAlreadyRepeatedIndex + 1 // +1 porque hemos quitado el activo con drop(1)
        } else {
            activeQueue.size
        }

        val updatedActiveQueue = activeQueue.toMutableList().apply {
            add(insertionIndex, newTopic)
        }

        val archivedTopics = currentTopics.filter { it.isArchived }

        _topics.value = applyTopicQueueOrder(
            activeTopicsInOrder = updatedActiveQueue,
            archivedTopics = archivedTopics
        )
        refreshDay(LocalDate.now())
    }

    fun clearAllData() {
        _topics.value = emptyList()
        _fixedObjectives.value = emptyList()
        _studyDays.value = emptyMap()
        _timeEntries.value = emptyList()
        _settings.value = StudySettings()
        localDataSource.clearActiveStudyTimerTracking()

        persist()
    }

    fun updateTopicName(topicId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return

        _topics.value = _topics.value.map { topic ->
            if (topic.id == topicId) {
                topic.copy(name = cleanName)
            } else {
                topic
            }
        }

        refreshDay(LocalDate.now())
    }

    fun archiveTopic(topicId: String) {
        _topics.value = normalizeTopicQueue(
            _topics.value.map { topic ->
                if (topic.id == topicId) {
                    topic.copy(isArchived = true)
                } else {
                    topic
                }
            }
        )

        refreshDay(LocalDate.now())
    }

    fun activateTopic(topicId: String) {
        val currentTopics = _topics.value
        val activeQueue = currentTopics
            .filter { !it.isArchived }
            .sortedBy { it.queuePosition }

        val currentActive = activeQueue.firstOrNull() ?: return
        val topicToActivate = activeQueue.firstOrNull { topic ->
            topic.id == topicId
        } ?: return

        if (currentActive.id == topicToActivate.id) return

        /*
         * Al activar manualmente otro tema:
         * 1. El elegido pasa a ser el primero y, por tanto, el tema activo.
         * 2. El que estaba activo pasa al FINAL de la cola.
         * 3. El resto conserva su orden relativo.
         *
         * Así nunca queda el tema anterior en segunda posición, evitando
         * alternar continuamente entre dos temas y manteniendo un ciclo real
         * por toda la cola.
         *
         * No se modifica "round": cambiar de tema manualmente no equivale a
         * haber terminado una vuelta.
         */
        val remainingTopics = activeQueue.filter { topic ->
            topic.id != currentActive.id &&
                    topic.id != topicToActivate.id
        }

        val reorderedQueue =
            listOf(topicToActivate) +
                    remainingTopics +
                    currentActive

        val archivedTopics = currentTopics.filter { it.isArchived }

        _topics.value = applyTopicQueueOrder(
            activeTopicsInOrder = reorderedQueue,
            archivedTopics = archivedTopics
        )

        refreshDay(LocalDate.now())
    }

    fun moveTopicUp(topicId: String) {
        movePendingTopic(topicId = topicId, direction = -1)
    }

    fun moveTopicDown(topicId: String) {
        movePendingTopic(topicId = topicId, direction = 1)
    }

    private fun movePendingTopic(
        topicId: String,
        direction: Int
    ) {
        if (direction != -1 && direction != 1) return

        val currentTopics = _topics.value
        val activeQueue = currentTopics
            .filter { !it.isArchived }
            .sortedBy { it.queuePosition }

        val currentIndex = activeQueue.indexOfFirst { topic ->
            topic.id == topicId
        }

        // La posición 0 es el tema activo. Para cambiarla se usa "Activar".
        if (currentIndex <= 0) return

        val targetIndex = currentIndex + direction

        // Los temas pendientes solo pueden moverse dentro de las posiciones 1..N.
        // Así el reordenado manual nunca sustituye accidentalmente al tema activo.
        if (targetIndex < 1 || targetIndex > activeQueue.lastIndex) return

        val reorderedQueue = activeQueue.toMutableList().apply {
            val movingTopic = removeAt(currentIndex)
            add(targetIndex, movingTopic)
        }

        val archivedTopics = currentTopics.filter { it.isArchived }

        _topics.value = applyTopicQueueOrder(
            activeTopicsInOrder = reorderedQueue,
            archivedTopics = archivedTopics
        )

        // El tema activo no cambia, por lo que no hace falta regenerar el snapshot
        // del día. Solo persistimos el nuevo orden de la cola.
        persist()
    }

    fun completeActiveTopic() {
        val currentTopics = _topics.value
        val activeTopic = getActiveTopicValue() ?: return

        val maxPosition = currentTopics
            .filter { !it.isArchived }
            .maxOfOrNull { it.queuePosition } ?: 0

        val updatedTopics = currentTopics.map { topic ->
            if (topic.id == activeTopic.id) {
                topic.copy(
                    queuePosition = maxPosition + 1,
                    round = topic.round + 1
                )
            } else {
                topic
            }
        }

        _topics.value = normalizeTopicQueue(updatedTopics)
        refreshDay(LocalDate.now())
    }

    fun addFixedObjective(name: String, targetMinutes: Int) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        if (targetMinutes <= 0) return

        val currentObjectives = _fixedObjectives.value
        val nextOrder = currentObjectives.maxOfOrNull { it.order }?.plus(1) ?: 0

        val newObjective = FixedObjective(
            name = cleanName,
            targetMinutes = targetMinutes,
            isEnabled = true,
            order = nextOrder
        )

        _fixedObjectives.value = currentObjectives + newObjective
        persist()
    }

    fun updateFixedObjective(
        objectiveId: String,
        name: String,
        targetMinutes: Int
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        if (targetMinutes <= 0) return

        _fixedObjectives.value = _fixedObjectives.value.map { objective ->
            if (objective.id == objectiveId) {
                objective.copy(
                    name = cleanName,
                    targetMinutes = targetMinutes
                )
            } else {
                objective
            }
        }

        persist()
    }

    fun setFixedObjectiveEnabled(objectiveId: String, enabled: Boolean) {
        _fixedObjectives.value = _fixedObjectives.value.map { objective ->
            if (objective.id == objectiveId) {
                objective.copy(isEnabled = enabled)
            } else {
                objective
            }
        }

        persist()
    }

    fun deleteFixedObjective(objectiveId: String) {
        _fixedObjectives.value = _fixedObjectives.value
            .filterNot { it.id == objectiveId }
            .mapIndexed { index, objective ->
                objective.copy(order = index)
            }

        _studyDays.value = _studyDays.value.mapValues { (_, day) ->
            day.copy(
                completedFixedObjectiveIds = day.completedFixedObjectiveIds - objectiveId
            )
        }

        persist()
    }

    fun toggleFixedObjectiveCompleted(
        objectiveId: String,
        date: LocalDate = LocalDate.now()
    ) {
        val previousDay = _studyDays.value[date] ?: createDaySnapshot(date)
        val activeTopic = getActiveTopicValue()
        val studiedMinutes = getStudiedMinutesForDate(date)
        val dailyGoalMinutes = _settings.value.dailyGoalMinutes
        val isFreeDay = isFreeDay(date, previousDay)

        val updatedCompletedIds = if (previousDay.completedFixedObjectiveIds.contains(objectiveId)) {
            previousDay.completedFixedObjectiveIds - objectiveId
        } else {
            previousDay.completedFixedObjectiveIds + objectiveId
        }

        val updatedDay = previousDay.copy(
            date = date,
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = previousDay.isFinishedManually
            ),
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = dailyGoalMinutes,
            activeTopicIdSnapshot = previousDay.activeTopicIdSnapshot ?: activeTopic?.id,
            activeTopicNameSnapshot = previousDay.activeTopicNameSnapshot ?: activeTopic?.name,
            completedFixedObjectiveIds = updatedCompletedIds
        )

        _studyDays.value = _studyDays.value + (date to updatedDay)
        persist()
    }

    fun addManualTime(
        minutes: Int,
        date: LocalDate = LocalDate.now()
    ) {
        if (minutes <= 0) return

        val entry = TimeEntry(
            date = date,
            minutes = minutes,
            type = TimeEntryType.MANUAL,
            label = "Estudio"
        )

        _timeEntries.value = _timeEntries.value + entry
        refreshDay(date)
    }

    fun addManualTime(
        minutes: Int,
        label: String,
        date: LocalDate = LocalDate.now()
    ) {
        if (minutes <= 0) return

        val entry = TimeEntry(
            date = date,
            minutes = minutes,
            type = TimeEntryType.MANUAL,
            label = label.trim().ifBlank { "Estudio" }
        )

        _timeEntries.value = _timeEntries.value + entry
        refreshDay(date)
    }

    fun addTimerTime(
        minutes: Int,
        date: LocalDate = LocalDate.now()
    ) {
        if (minutes <= 0) return

        val entry = TimeEntry(
            date = date,
            minutes = minutes,
            type = TimeEntryType.TIMER,
            label = "Cronómetro"
        )

        _timeEntries.value = _timeEntries.value + entry
        refreshDay(date)
    }


    fun finishDay(
        date: LocalDate = LocalDate.now()
    ) {
        val previousDay = _studyDays.value[date] ?: createDaySnapshot(date)
        val studiedMinutes = getStudiedMinutesForDate(date)
        val activeTopic = getActiveTopicValue()
        val dailyGoalMinutes = _settings.value.dailyGoalMinutes
        val isFreeDay = isFreeDay(date, previousDay)

        val updatedDay = previousDay.copy(
            date = date,
            isFinishedManually = true,
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = true
            ),
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = dailyGoalMinutes,
            activeTopicIdSnapshot = previousDay.activeTopicIdSnapshot ?: activeTopic?.id,
            activeTopicNameSnapshot = previousDay.activeTopicNameSnapshot ?: activeTopic?.name
        )

        _studyDays.value = _studyDays.value + (date to updatedDay)
        persist()
    }

    fun resetDay(
        date: LocalDate = LocalDate.now()
    ) {
        val previousDay = _studyDays.value[date] ?: createDaySnapshot(date)
        val activeTopic = getActiveTopicValue()
        val dailyGoalMinutes = _settings.value.dailyGoalMinutes

        _timeEntries.value = _timeEntries.value.filterNot { entry ->
            entry.date == date
        }

        val dayWithoutProgress = previousDay.copy(
            date = date,
            isFinishedManually = false,
            studiedMinutes = 0,
            dailyGoalMinutes = dailyGoalMinutes,
            activeTopicIdSnapshot = activeTopic?.id,
            activeTopicNameSnapshot = activeTopic?.name,
            completedFixedObjectiveIds = emptySet()
        )

        val isFreeDay = isFreeDay(
            date = date,
            day = dayWithoutProgress
        )

        val updatedDay = dayWithoutProgress.copy(
            status = calculateDayStatus(
                date = date,
                studiedMinutes = 0,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = false
            )
        )

        _studyDays.value = _studyDays.value + (date to updatedDay)
        localDataSource.clearActiveStudyTimerTracking()
        persist()
    }

    fun startActiveStudyTimerTracking(
        date: LocalDate = LocalDate.now()
    ) {
        localDataSource.startActiveStudyTimerTracking(date)
    }

    fun pauseActiveStudyTimerTracking() {
        localDataSource.pauseActiveStudyTimerTracking()
    }

    fun resumeActiveStudyTimerTracking() {
        localDataSource.resumeActiveStudyTimerTracking()
    }

    fun clearActiveStudyTimerTracking() {
        localDataSource.clearActiveStudyTimerTracking()
    }

    fun getActiveStudyTimerSnapshot() =
        localDataSource.loadActiveStudyTimerSnapshot()

    fun setRestDay(
        date: LocalDate = LocalDate.now(),
        isRestDay: Boolean = true
    ) {
        val previousDay = _studyDays.value[date] ?: createDaySnapshot(date)
        val studiedMinutes = getStudiedMinutesForDate(date)
        val activeTopic = getActiveTopicValue()

        val updatedDay = previousDay.copy(
            date = date,
            isRestDay = isRestDay,
            forceStudyDay = if (isRestDay) false else previousDay.forceStudyDay,
            isFinishedManually = if (isRestDay) false else previousDay.isFinishedManually,
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = _settings.value.dailyGoalMinutes,
                isFreeDay = if (isRestDay) true else isFreeDay(
                    date = date,
                    day = previousDay.copy(isRestDay = false)
                ),
                isFinishedManually = if (isRestDay) false else previousDay.isFinishedManually
            ),
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = _settings.value.dailyGoalMinutes,
            activeTopicIdSnapshot = previousDay.activeTopicIdSnapshot ?: activeTopic?.id,
            activeTopicNameSnapshot = previousDay.activeTopicNameSnapshot ?: activeTopic?.name
        )

        _studyDays.value = _studyDays.value + (date to updatedDay)
        persist()
    }

    fun cancelFreeDay(
        date: LocalDate = LocalDate.now()
    ) {
        val previousDay = _studyDays.value[date] ?: createDaySnapshot(date)
        val studiedMinutes = getStudiedMinutesForDate(date)
        val activeTopic = getActiveTopicValue()

        val updatedDay = previousDay.copy(
            isRestDay = false,
            forceStudyDay = true,
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = _settings.value.dailyGoalMinutes,
                isFreeDay = false,
                isFinishedManually = previousDay.isFinishedManually
            ),
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = _settings.value.dailyGoalMinutes,
            activeTopicIdSnapshot = previousDay.activeTopicIdSnapshot ?: activeTopic?.id,
            activeTopicNameSnapshot = previousDay.activeTopicNameSnapshot ?: activeTopic?.name
        )

        _studyDays.value = _studyDays.value + (date to updatedDay)
        persist()
    }

    fun setDailyGoalMinutes(minutes: Int) {
        if (minutes <= 0) return

        _settings.value = _settings.value.copy(
            dailyGoalMinutes = minutes
        )

        refreshAllStudyDays()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(
            notificationsEnabled = enabled
        )

        persist()
    }

    fun setStudyReminderEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(
            studyReminderEnabled = enabled
        )

        persist()
    }

    fun setStudyReminderTime(time: String) {
        if (!time.isValidHourMinuteTime()) return

        _settings.value = _settings.value.copy(
            studyReminderTime = time
        )

        persist()
    }

    fun setFixedObjectivesReminderEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(
            fixedObjectivesReminderEnabled = enabled
        )

        persist()
    }

    fun setFixedObjectivesReminderTime(time: String) {
        if (!time.isValidHourMinuteTime()) return

        _settings.value = _settings.value.copy(
            fixedObjectivesReminderTime = time
        )

        persist()
    }

    fun setRestDayNotificationEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(
            restDayNotificationEnabled = enabled
        )

        persist()
    }

    fun setRestDayNotificationTime(time: String) {
        if (!time.isValidHourMinuteTime()) return

        _settings.value = _settings.value.copy(
            restDayNotificationTime = time
        )

        persist()
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(
            dailySummaryEnabled = enabled
        )

        persist()
    }

    fun setDailySummaryTime(time: String) {
        if (!time.isValidHourMinuteTime()) return

        _settings.value = _settings.value.copy(
            dailySummaryTime = time
        )

        persist()
    }

    fun setDefaultFreeWeekDays(days: Set<Int>) {
        _settings.value = _settings.value.copy(
            defaultFreeWeekDays = days
                .filter { it in 1..7 }
                .toSet()
        )

        refreshAllStudyDays()
    }

    fun toggleDefaultFreeWeekDay(dayOfWeekValue: Int) {
        if (dayOfWeekValue !in 1..7) return

        val current = _settings.value.defaultFreeWeekDays

        val updated = if (current.contains(dayOfWeekValue)) {
            current - dayOfWeekValue
        } else {
            current + dayOfWeekValue
        }

        setDefaultFreeWeekDays(updated)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        _settings.value = _settings.value.copy(
            darkMode = themeMode
        )

        persist()
    }

    fun setLastPomodoroMinutes(minutes: Int) {
        if (minutes <= 0) return

        _settings.value = _settings.value.copy(
            lastPomodoroMinutes = minutes
        )

        persist()
    }

    fun getStudyDay(date: LocalDate = LocalDate.now()): StudyDay {
        return _studyDays.value[date] ?: createDaySnapshot(date)
    }

    fun getEffectiveStudyDay(date: LocalDate = LocalDate.now()): StudyDay {
        val day = getStudyDay(date)
        val studiedMinutes = getStudiedMinutesForDate(date)
        val isFreeDay = isFreeDay(date, day)

        return day.copy(
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = _settings.value.dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = day.isFinishedManually
            ),
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = _settings.value.dailyGoalMinutes
        )
    }

    fun isFreeDay(
        date: LocalDate = LocalDate.now()
    ): Boolean {
        return isFreeDay(
            date = date,
            day = _studyDays.value[date]
        )
    }

    fun isDefaultFreeDay(
        date: LocalDate = LocalDate.now()
    ): Boolean {
        val day = _studyDays.value[date]
        if (day?.forceStudyDay == true) return false

        return _settings.value.defaultFreeWeekDays.contains(date.dayOfWeek.value)
    }

    fun getStudiedMinutesForDate(date: LocalDate = LocalDate.now()): Int {
        return _timeEntries.value
            .filter { it.date == date }
            .sumOf { it.minutes }
    }

    fun getRemainingMinutesForDate(date: LocalDate = LocalDate.now()): Int {
        val studiedMinutes = getStudiedMinutesForDate(date)
        val dailyGoalMinutes = _settings.value.dailyGoalMinutes

        return (dailyGoalMinutes - studiedMinutes).coerceAtLeast(0)
    }

    fun hasCompletedDailyGoal(date: LocalDate = LocalDate.now()): Boolean {
        return getStudiedMinutesForDate(date) >= _settings.value.dailyGoalMinutes
    }

    fun exportBackupJson(): String {
        val activeTimer = localDataSource.loadActiveStudyTimerSnapshot()
        val activeTimerMinutes = activeTimer.elapsedMinutes()

        val exportedTimeEntries = if (
            activeTimer.isActive &&
            activeTimer.date != null &&
            activeTimerMinutes > 0
        ) {
            _timeEntries.value + TimeEntry(
                date = activeTimer.date,
                minutes = activeTimerMinutes,
                type = TimeEntryType.TIMER,
                label = "Cronómetro · sesión activa incluida en la copia"
            )
        } else {
            _timeEntries.value
        }

        return localDataSource.exportToJson(
            StoredStudyData(
                topics = _topics.value,
                fixedObjectives = _fixedObjectives.value,
                studyDays = _studyDays.value,
                timeEntries = exportedTimeEntries,
                settings = _settings.value
            )
        )
    }

    fun importBackupJson(json: String): Boolean {
        val importedData = localDataSource.importFromJson(json) ?: return false

        _topics.value = normalizeTopicQueue(importedData.topics)
        _fixedObjectives.value = importedData.fixedObjectives
        _studyDays.value = importedData.studyDays
        _timeEntries.value = importedData.timeEntries
        _settings.value = importedData.settings

        persist()

        return true
    }

    private fun refreshDay(date: LocalDate) {
        val previousDay = _studyDays.value[date] ?: createDaySnapshot(date)
        val activeTopic = getActiveTopicValue()
        val studiedMinutes = getStudiedMinutesForDate(date)
        val dailyGoalMinutes = _settings.value.dailyGoalMinutes
        val isFreeDay = isFreeDay(date, previousDay)

        val updatedDay = previousDay.copy(
            date = date,
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = previousDay.isFinishedManually
            ),
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = dailyGoalMinutes,
            activeTopicIdSnapshot = previousDay.activeTopicIdSnapshot ?: activeTopic?.id,
            activeTopicNameSnapshot = previousDay.activeTopicNameSnapshot ?: activeTopic?.name
        )

        _studyDays.value = _studyDays.value + (date to updatedDay)
        persist()
    }

    private fun refreshAllStudyDays() {
        val dates = (_studyDays.value.keys + LocalDate.now()).toSet()

        dates.forEach { date ->
            refreshDay(date)
        }

        if (dates.isEmpty()) {
            persist()
        }
    }

    private fun createDaySnapshot(date: LocalDate): StudyDay {
        val activeTopic = getActiveTopicValue()
        val studiedMinutes = getStudiedMinutesForDate(date)
        val dailyGoalMinutes = _settings.value.dailyGoalMinutes
        val isDefaultFreeDay = _settings.value.defaultFreeWeekDays.contains(date.dayOfWeek.value)

        return StudyDay(
            date = date,
            status = calculateDayStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isDefaultFreeDay,
                isFinishedManually = false
            ),
            isRestDay = false,
            forceStudyDay = false,
            studiedMinutes = studiedMinutes,
            dailyGoalMinutes = dailyGoalMinutes,
            activeTopicIdSnapshot = activeTopic?.id,
            activeTopicNameSnapshot = activeTopic?.name,
            completedFixedObjectiveIds = emptySet()
        )
    }

    private fun isFreeDay(
        date: LocalDate,
        day: StudyDay?
    ): Boolean {
        if (day?.forceStudyDay == true) return false
        if (day?.isRestDay == true) return true

        return _settings.value.defaultFreeWeekDays.contains(date.dayOfWeek.value)
    }

    private fun calculateDayStatus(
        date: LocalDate,
        studiedMinutes: Int,
        dailyGoalMinutes: Int,
        isFreeDay: Boolean,
        isFinishedManually: Boolean = false
    ): DayStatus {
        if (isFreeDay) return DayStatus.REST
        if (isFinishedManually) return DayStatus.COMPLETED
        if (dailyGoalMinutes > 0 && studiedMinutes >= dailyGoalMinutes) return DayStatus.COMPLETED
        if (studiedMinutes > 0) return DayStatus.IN_PROGRESS
        if (date.isBefore(LocalDate.now())) return DayStatus.NOT_COMPLETED

        return DayStatus.EMPTY
    }

    private fun getActiveTopicValue(): StudyTopic? {
        return _topics.value
            .filter { !it.isArchived }
            .sortedBy { it.queuePosition }
            .firstOrNull()
    }

    /**
     * Aplica exactamente el orden recibido a la cola activa.
     *
     * Es distinto de normalizeTopicQueue(): normalizeTopicQueue() ordena primero
     * por queuePosition y por eso no sirve cuando acabamos de construir un orden
     * nuevo en memoria. Aquí reescribimos queuePosition según el orden de la lista
     * antes de persistirlo.
     */
    private fun applyTopicQueueOrder(
        activeTopicsInOrder: List<StudyTopic>,
        archivedTopics: List<StudyTopic>
    ): List<StudyTopic> {
        val reindexedActiveTopics = activeTopicsInOrder.mapIndexed { index, topic ->
            topic.copy(queuePosition = index)
        }

        return reindexedActiveTopics + archivedTopics
    }

    private fun normalizeTopicQueue(topics: List<StudyTopic>): List<StudyTopic> {
        val activeTopics = topics
            .filter { !it.isArchived }
            .sortedBy { it.queuePosition }
            .mapIndexed { index, topic ->
                topic.copy(queuePosition = index)
            }

        val archivedTopics = topics
            .filter { it.isArchived }

        return activeTopics + archivedTopics
    }

    private fun persist() {
        localDataSource.save(
            StoredStudyData(
                topics = _topics.value,
                fixedObjectives = _fixedObjectives.value,
                studyDays = _studyDays.value,
                timeEntries = _timeEntries.value,
                settings = _settings.value
            )
        )
    }
}

private fun String.isValidHourMinuteTime(): Boolean {
    val parts = split(":")
    if (parts.size != 2) return false

    val hours = parts[0].toIntOrNull() ?: return false
    val minutes = parts[1].toIntOrNull() ?: return false

    return hours in 0..23 && minutes in 0..59 && parts[0].length == 2 && parts[1].length == 2
}