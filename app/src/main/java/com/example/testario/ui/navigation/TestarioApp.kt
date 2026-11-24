package com.example.testario.ui.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.testario.ui.screen.NexumCapsule
import com.example.testario.ui.theme.TestarioBackground
import androidx.compose.ui.platform.LocalConfiguration
import com.example.testario.ui.screen.LocalNexumRail
import com.example.testario.ui.screen.MainSection
import com.example.testario.ui.screen.NexumNavRail
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.testario.domain.QuickTestLaunchResolver
import com.example.testario.domain.QuickTestLaunchResult
import com.example.testario.widget.NexumQuickTestWidgetModel
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.testario.update.NexumRelease
import com.example.testario.update.NexumUpdateManager
import com.example.testario.update.NexumUpdateResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.planario.app.PlanarioDependencies
import com.example.planario.domain.timer.StudyTimerState
import com.example.testario.data.model.TestConfig
import com.example.testario.data.model.TestMode
import com.example.testario.ui.screen.GroupsScreen
import com.example.testario.ui.screen.HomeScreen
import com.example.testario.ui.screen.NexumBackgroundWatermark
import com.example.testario.ui.screen.LibraryTopicsScreen
import com.example.testario.ui.screen.QuestionEditorScreen
import com.example.testario.ui.screen.UnifiedQuestionEditorScreen
import com.example.testario.ui.screen.SettingsScreen
import com.example.testario.ui.screen.StatsScreen
import com.example.testario.ui.screen.TestConfigScreen
import com.example.testario.ui.screen.TestScreen
import com.example.testario.ui.screen.MixedSessionScreen
import com.example.testario.ui.screen.TopicDetailScreen
import com.example.testario.ui.screen.StudyExerciseEditorScreen
import com.example.testario.ui.screen.StudyExercisePracticeScreen
import com.example.testario.ui.screen.UnifiedPlanScreen
import com.example.testario.ui.theme.TestarioTheme
import com.example.testario.viewmodel.BehaviorSettingsViewModel
import com.example.testario.viewmodel.HomeSettingsViewModel
import com.example.testario.viewmodel.ImportExportViewModel
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.StatsViewModel
import com.example.testario.viewmodel.TestSessionViewModel
import com.example.testario.viewmodel.MixedSessionViewModel
import com.example.testario.viewmodel.ThemeViewModel

@Composable
fun TestarioApp(
    requestedDestinationRoute: String? = null,
    navigationRequestId: Int = 0,
    requestedQuickTestTemplateId: String? = null,
    requestedQuickTestTopicIds: List<String>? = null,
    requestedQuickTestQuestionCount: Int? = null
) {
    val themeViewModel: ThemeViewModel = viewModel()
    val behaviorSettingsViewModel: BehaviorSettingsViewModel = viewModel()
    val homeSettingsViewModel: HomeSettingsViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val statsViewModel: StatsViewModel = viewModel()
    val importExportViewModel: ImportExportViewModel = viewModel()
    val sessionViewModel: TestSessionViewModel = viewModel()
    val mixedSessionViewModel: MixedSessionViewModel = viewModel()

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val context = LocalContext.current
    val timerState by PlanarioDependencies.studyTimerController.state.collectAsState()
    val recoveryError by libraryViewModel.recoveryError.collectAsState()
    val planningRecoveryError by PlanarioDependencies.studyRepository.recoveryError.collectAsState()
    val activeRecoveryWarning = listOfNotNull(recoveryError, planningRecoveryError)
        .joinToString("\n\n")
    var recoveryNoticeDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(activeRecoveryWarning) {
        if (activeRecoveryWarning.isNotBlank()) recoveryNoticeDismissed = false
    }
    var showTimerExitDialog by remember { mutableStateOf(false) }
    var announcedRelease by remember { mutableStateOf<NexumRelease?>(null) }
    var dismissedUpdateNotice by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()

    // One quiet network check on startup. Never interrupt Test or Plan screens.
    LaunchedEffect(Unit) {
        val result = NexumUpdateManager.check(context.applicationContext)
        if (result is NexumUpdateResult.Available) {
            announcedRelease = result.release
        }
    }

    val darkTheme = when (themeViewModel.themeMode) {
        ThemeViewModel.ThemeMode.LIGHT -> false
        ThemeViewModel.ThemeMode.DARK -> true
        ThemeViewModel.ThemeMode.SYSTEM -> systemDark
    }

    fun goBack() {
        if (!navController.popBackStack()) {
            navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = false } }
        }
    }

    fun goHome() {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goLibrary() {
        navController.navigate(Routes.LIBRARY) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goTest() {
        navController.navigate(Routes.testConfig()) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goPlan(tab: String = "plan") {
        navController.navigate(Routes.plan(tab)) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goGroups() {
        navController.navigate(Routes.GROUPS) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goStats() {
        navController.navigate(Routes.STATS) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goSettings() {
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    fun goHomeSettings() {
        navController.navigate(Routes.SETTINGS_HOME) { launchSingleTop = true }
    }

    fun launchLinkedQuickTest(topicIds: List<String>, count: Int, templateId: String?) {
        when (val result = QuickTestLaunchResolver.resolve(
            libraryViewModel.database.value, topicIds, count, templateId
        )) {
            is QuickTestLaunchResult.Ready -> {
                sessionViewModel.start(result.topics, result.config)
                navController.navigate(Routes.TEST_SCREEN)
            }
            is QuickTestLaunchResult.Invalid ->
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val context = libraryViewModel.getApplication<android.app.Application>().applicationContext
        PlanarioDependencies.init(context)
        PlanarioDependencies.studyTimerController.attachContext(context)
        val recoveredSession = PlanarioDependencies.studyTimerController.recoverPendingStudyTimerIfNeeded()
        if (recoveredSession != null) {
            PlanarioDependencies.studyRepository.clearActiveStudyTimerTracking()
            if (recoveredSession.minutes > 0) {
                PlanarioDependencies.studyRepository.addTimerTime(
                    minutes = recoveredSession.minutes,
                    date = recoveredSession.date
                )
            }
            PlanarioDependencies.stopStudyTimerForegroundService()
        }
    }

    LaunchedEffect(requestedDestinationRoute, navigationRequestId, requestedQuickTestTemplateId,
        requestedQuickTestTopicIds, requestedQuickTestQuestionCount) {
        when (requestedDestinationRoute) {
            "plan" -> goPlan("plan")
            "timer" -> goPlan("timer")
            "calendar" -> goPlan("calendar")
            "settings" -> goSettings()
            "home" -> goHome()
            "home_today" -> goHome()
            NexumQuickTestWidgetModel.LAUNCH_ROUTE -> {
                // Reuse the same resolver as linked tests: no duplicated logic,
                // database migrations, or guessed template defaults.
                launchLinkedQuickTest(
                    topicIds = requestedQuickTestTopicIds
                        ?: NexumQuickTestWidgetModel.defaultTopicIds(libraryViewModel.database.value),
                    count = requestedQuickTestQuestionCount
                        ?: NexumQuickTestWidgetModel.DEFAULT_QUESTION_COUNT,
                    templateId = requestedQuickTestTemplateId
                )
            }
        }
    }

    TestarioTheme(
        darkTheme = darkTheme,
        uiScale = themeViewModel.uiScale
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (darkTheme) Color(0xFF0B0D0C) else Color(0xFFF2EDE1))
        ) {
            NexumBackgroundWatermark(
                modifier = Modifier.fillMaxSize(),
                darkTheme = darkTheme
            )
            // En pantallas anchas la navegación principal es un carril lateral;
            // en móvil sigue siendo la cápsula inferior de cada pantalla.
            val route = currentBackStackEntry?.destination?.route
            val railSection = when {
                route == Routes.HOME -> MainSection.HOME
                route == Routes.LIBRARY || route == Routes.GROUPS -> MainSection.LIBRARY
                route == Routes.TEST_CONFIG -> MainSection.TEST
                route == Routes.PLAN -> MainSection.PLAN
                route == Routes.STATS -> MainSection.STATS
                else -> null
            }
            // Secciones de la cápsula (Estadísticas solo cuelga del carril o de Inicio).
            val capsuleSection = railSection?.takeIf { it != MainSection.STATS }
            val cfg = LocalConfiguration.current
            // El carril necesita ~500 dp de alto: un móvil horizontal usa la cápsula.
            val showRail = railSection != null && cfg.screenWidthDp >= 600 && cfg.screenHeightDp >= 480
            Row(Modifier.fillMaxSize()) {
            if (showRail && railSection != null) {
                NexumNavRail(
                    selected = railSection,
                    onHome = { goHome() },
                    onLibrary = { goLibrary() },
                    onTest = { goTest() },
                    onPlan = { goPlan() },
                    onStats = { goStats() },
                    onSettings = { goSettings() }
                )
            }
            val showCapsule = !showRail && capsuleSection != null
            CompositionLocalProvider(LocalNexumRail provides (showRail || capsuleSection != null)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
            // En móvil, deslizar el dedo recorre Inicio → Biblioteca → Grupos → Plan → Test.
            val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
            val swipeBy: (Int) -> Unit = { delta ->
                val target = SWIPE_ORDER.getOrNull(SWIPE_ORDER.indexOf(route) + delta)
                if (route in SWIPE_ORDER && target != null) when (target) {
                    Routes.HOME -> goHome()
                    Routes.LIBRARY -> goLibrary()
                    Routes.GROUPS -> goGroups()
                    Routes.PLAN -> goPlan()
                    else -> goTest()
                }
            }
            // The library default is a 700 ms cross-fade, which feels sluggish.
            // Entre secciones principales la pantalla se desplaza según el sentido del gesto.
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (showCapsule) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
                    .pointerInput(showCapsule, route) {
                        if (!showCapsule) return@pointerInput
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                if (total <= -swipeThreshold) swipeBy(1)
                                else if (total >= swipeThreshold) swipeBy(-1)
                            },
                            onHorizontalDrag = { _, dx -> total += dx }
                        )
                    },
                enterTransition = {
                    val d = swipeDirection(initialState.destination.route, targetState.destination.route)
                    if (d != 0) slideIntoContainer(slideSide(d), tween(280)) + fadeIn(tween(200))
                    else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 }
                },
                exitTransition = {
                    val d = swipeDirection(initialState.destination.route, targetState.destination.route)
                    if (d != 0) slideOutOfContainer(slideSide(d), tween(280)) + fadeOut(tween(200))
                    else fadeOut(tween(160))
                },
                popEnterTransition = {
                    val d = swipeDirection(initialState.destination.route, targetState.destination.route)
                    if (d != 0) slideIntoContainer(slideSide(d), tween(280)) + fadeIn(tween(200))
                    else fadeIn(tween(220))
                },
                popExitTransition = {
                    val d = swipeDirection(initialState.destination.route, targetState.destination.route)
                    if (d != 0) slideOutOfContainer(slideSide(d), tween(280)) + fadeOut(tween(200))
                    else fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 14 }
                }
            ) {
            composable(Routes.HOME) {
                BackHandler(enabled = timerState.studyTimerState == StudyTimerState.RUNNING) {
                    showTimerExitDialog = true
                }
                HomeScreen(
                    libraryViewModel = libraryViewModel,
                    homeSettingsViewModel = homeSettingsViewModel,
                    onLibrary = { goLibrary() },
                    onGroups = { navController.navigate(Routes.GROUPS) { launchSingleTop = true } },
                    onCreateTest = { goTest() },
                    onPlan = { goPlan() },
                    onOpenTimer = { goPlan("timer") },
                    onStartQuickTest = { topicIds, count, templateId ->
                        launchLinkedQuickTest(topicIds, count, templateId)
                    },
                    onOpenTemplate = { templateId -> navController.navigate(Routes.testConfig(templateId = templateId)) },
                    onStats = { goStats() },
                    onSettings = { goSettings() },
                    onCustomizeHome = { goHomeSettings() },
                    savedTestSessionViewModel = sessionViewModel,
                    onResumeSavedTest = {
                        if (sessionViewModel.resumeSavedSession()) {
                            navController.navigate(Routes.TEST_SCREEN) { launchSingleTop = true }
                        } else {
                            Toast.makeText(context, "No se pudo recuperar el test guardado.", Toast.LENGTH_LONG).show()
                        }
                    },
                    focusTodayRequestId = if (requestedDestinationRoute == "home_today")
                        navigationRequestId else null
                )
            }

            composable(Routes.LIBRARY) {
                LibraryTopicsScreen(
                    libraryViewModel = libraryViewModel,
                    statsViewModel = statsViewModel,
                    onOpenTopic = { navController.navigate(Routes.topicDetail(it)) },
                    onHome = { goHome() },
                    onCreateTest = { goTest() },
                    onPlan = { goPlan() },
                    onGroups = { navController.navigate(Routes.GROUPS) { launchSingleTop = true } },
                    onStats = { goStats() },
                    onSettings = { goSettings() }
                )
            }

            composable(Routes.GROUPS) {
                GroupsScreen(
                    libraryViewModel = libraryViewModel,
                    onHome = { goHome() },
                    onTopics = { goLibrary() },
                    onCreateTest = { goTest() },
                    onPlan = { goPlan() },
                    onStats = { goStats() },
                    onSettings = { goSettings() }
                )
            }

            composable(Routes.TOPIC_DETAIL) { b ->
                val id = b.arguments?.getString("topicId") ?: return@composable
                TopicDetailScreen(
                    topicId = id,
                    libraryViewModel = libraryViewModel,
                    statsViewModel = statsViewModel,
                    onBack = { goBack() },
                    onAddQuestion = { navController.navigate(Routes.unifiedQuestionEditor(id)) },
                    onEditQuestion = { qId -> navController.navigate(Routes.questionEditor(id, qId)) },
                    onAddExercise = { _ -> navController.navigate(Routes.unifiedQuestionEditor(id)) },
                    onEditExercise = { exId -> navController.navigate(Routes.studyExerciseEditor(id, exId)) },
                    onPracticeExercises = { exerciseId ->
                        navController.navigate(Routes.studyExercisePractice(id, exerciseId))
                    },
                    onStartTest = { topicIdParaTest -> navController.navigate(Routes.testConfig(topicIdParaTest)) }
                )
            }

            composable(Routes.UNIFIED_QUESTION_EDITOR) { entry ->
                val id = entry.arguments?.getString("topicId") ?: return@composable
                UnifiedQuestionEditorScreen(
                    topicId = id, libraryViewModel = libraryViewModel, onBack = { goBack() }
                )
            }

            composable(Routes.QUESTION_EDITOR) { b ->
                val tId = b.arguments?.getString("topicId") ?: return@composable
                val qId = b.arguments?.getString("questionId")
                QuestionEditorScreen(
                    topicId = tId,
                    questionId = qId,
                    libraryViewModel = libraryViewModel,
                    onBack = { goBack() }
                )
            }

            composable(Routes.STUDY_EXERCISE_EDITOR) { entry ->
                val topicId = entry.arguments?.getString("topicId") ?: return@composable
                StudyExerciseEditorScreen(
                    topicId = topicId,
                    exerciseId = entry.arguments?.getString("exerciseId"),
                    initialKind = entry.arguments?.getString("initialKind")?.let {
                        runCatching { com.example.testario.data.model.StudyExerciseKind.valueOf(it) }.getOrNull()
                    } ?: com.example.testario.data.model.StudyExerciseKind.WRITTEN_MULTI,
                    libraryViewModel = libraryViewModel,
                    onBack = { goBack() }
                )
            }
            composable(Routes.STUDY_EXERCISE_PRACTICE) { entry ->
                val topicId = entry.arguments?.getString("topicId") ?: return@composable
                StudyExercisePracticeScreen(topicId, libraryViewModel, onBack = { goBack() },
                    selectedExerciseId = entry.arguments?.getString("exerciseId"))
            }

            composable(Routes.TEST_CONFIG) { backStackEntry ->
                val preselectedTopicId = backStackEntry.arguments?.getString("preselectedTopicId")
                val preselectedTemplateId = backStackEntry.arguments?.getString("templateId")
                TestConfigScreen(
                    libraryViewModel = libraryViewModel,
                    sessionViewModel = sessionViewModel,
                    mixedSessionViewModel = mixedSessionViewModel,
                    preselectedTopicId = preselectedTopicId,
                    preselectedTemplateId = preselectedTemplateId,
                    onBack = { goBack() },
                    onHome = { goHome() },
                    onLibrary = { goLibrary() },
                    onPlan = { goPlan() },
                    onStats = { goStats() },
                    onSettings = { goSettings() },
                    onStart = { navController.navigate(Routes.TEST_SCREEN) },
                    onStartMixed = { navController.navigate(Routes.MIXED_SESSION) }
                )
            }

            composable(Routes.TEST_SCREEN) {
                TestScreen(
                    libraryViewModel = libraryViewModel,
                    sessionViewModel = sessionViewModel,
                    statsViewModel = statsViewModel,
                    onFinish = { goHome() },
                    onEditQuestion = { topicId, questionId -> navController.navigate(Routes.questionEditor(topicId, questionId)) }
                )
            }

            composable(Routes.MIXED_SESSION) {
                MixedSessionScreen(
                    session = mixedSessionViewModel,
                    onLeave = { goTest() },
                    onEditQuestion = { topicId, questionId ->
                        navController.navigate(Routes.questionEditor(topicId, questionId))
                    }
                )
            }

            composable(Routes.PLAN) { backStackEntry ->
                val tab = backStackEntry.arguments?.getString("tab")
                UnifiedPlanScreen(
                    libraryViewModel = libraryViewModel,
                    initialTab = tab,
                    onHome = { goHome() },
                    onLibrary = { goLibrary() },
                    onTest = { goTest() },
                    onStats = { goStats() },
                    onSettings = { goSettings() },
                    onStartQuickTest = { topicIds, count, templateId ->
                        launchLinkedQuickTest(topicIds, count, templateId)
                    }
                )
            }

            composable(Routes.STATS) {
                StatsScreen(
                    libraryViewModel = libraryViewModel,
                    statsViewModel = statsViewModel,
                    onBack = { goBack() },
                    onHome = { goHome() },
                    onLibrary = { goLibrary() },
                    onCreateTest = { goTest() },
                    onPlan = { goPlan() },
                    onSettings = { goSettings() },
                    onReviewFailures = { topicId ->
                        val topic = libraryViewModel.database.value.topics.firstOrNull { it.id == topicId }
                        val topicsToPass = topic?.let { listOf(it) } ?: emptyList()
                        val failuresCount = topic?.questions?.count { it.stats.inFailures } ?: 0
                        sessionViewModel.start(
                            topicsToPass,
                            TestConfig(
                                mode = TestMode.FAILURES,
                                topicIds = listOf(topicId),
                                questionCount = failuresCount,
                                difficulties = emptySet()
                            )
                        )
                        navController.navigate(Routes.TEST_SCREEN)
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeViewModel = themeViewModel,
                    behaviorSettingsViewModel = behaviorSettingsViewModel,
                    homeSettingsViewModel = homeSettingsViewModel,
                    importExportViewModel = importExportViewModel,
                    statsViewModel = statsViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { goBack() },
                    onStats = { navController.navigate(Routes.STATS) { launchSingleTop = true } }
                )
            }

            composable(Routes.SETTINGS_HOME) {
                SettingsScreen(
                    themeViewModel = themeViewModel,
                    behaviorSettingsViewModel = behaviorSettingsViewModel,
                    homeSettingsViewModel = homeSettingsViewModel,
                    importExportViewModel = importExportViewModel,
                    statsViewModel = statsViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { goBack() },
                    onStats = { navController.navigate(Routes.STATS) { launchSingleTop = true } },
                    openHomeSettingsInitially = true
                )
            }
            }
            if (showCapsule && capsuleSection != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(TestarioBackground)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NexumCapsule(
                        selected = capsuleSection,
                        onHome = { goHome() },
                        onLibrary = { goLibrary() },
                        onPlan = { goPlan() },
                        onTest = { goTest() }
                    )
                }
            }
            }
            }
            }
        }

        if (activeRecoveryWarning.isNotBlank() && !recoveryNoticeDismissed) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Datos protegidos: revisa el almacenamiento") },
                text = {
                    Text(activeRecoveryWarning +
                        "\n\nNo desinstales ni borres datos. Si tienes cambios recientes " +
                        "todavía visibles, exporta primero una copia GLOBAL COMPLETA " +
                        "ZIP fuera del móvil. Si necesitas restaurar datos, usa únicamente " +
                        "una copia verificada en Ajustes.")
                },
                confirmButton = {
                    com.example.testario.ui.theme.NeonButton(onClick = {
                        recoveryNoticeDismissed = true
                        goSettings()
                    }) { Text("Abrir Ajustes") }
                }
            )
        }

        if (showTimerExitDialog && timerState.studyTimerState == StudyTimerState.RUNNING) {
            AlertDialog(
                onDismissRequest = { showTimerExitDialog = false },
                title = { Text("El reloj sigue corriendo") },
                text = {
                    Text(
                        "El cronómetro de estudio está contando. ¿Quieres detenerlo y guardar " +
                            "el tiempo, o dejarlo funcionando mientras sales de NEXUM?"
                    )
                },
                confirmButton = {
                    Column {
                        com.example.testario.ui.theme.NeonButton(
                            onClick = {
                                showTimerExitDialog = false
                                // Hide only the Activity. The foreground service and
                                // notification keep the running clock alive.
                                (context as? Activity)?.moveTaskToBack(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Seguir contando") }
                        com.example.testario.ui.theme.NeonOutlinedButton(
                            onClick = {
                                showTimerExitDialog = false
                                PlanarioDependencies.finishStudyTimerAndSave()
                                (context as? Activity)?.moveTaskToBack(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Detener y guardar") }
                    }
                },
                dismissButton = {
                    com.example.testario.ui.theme.NeonTextButton(
                        onClick = { showTimerExitDialog = false }
                    ) { Text("Cancelar") }
                }
            )
        }

        val update = announcedRelease
        if (update != null &&
            !dismissedUpdateNotice &&
            currentBackStackEntry?.destination?.route == Routes.HOME
        ) {
            AlertDialog(
                onDismissRequest = { dismissedUpdateNotice = true },
                title = { Text("Nueva versión de NEXUM") },
                text = {
                    Text("Ya está disponible NEXUM ${update.version}. Descarga la actualización oficial desde Ajustes sin borrar tus datos.")
                },
                confirmButton = {
                    com.example.testario.ui.theme.NeonButton(
                        onClick = {
                            dismissedUpdateNotice = true
                            goSettings()
                        }
                    ) { Text("Ver actualización") }
                },
                dismissButton = {
                    com.example.testario.ui.theme.NeonTextButton(
                        onClick = { dismissedUpdateNotice = true }
                    ) { Text("Más tarde") }
                }
            )
        }
    }
}

/** Orden de las secciones al deslizar: Inicio, Biblioteca, Grupos, Plan y Test. */
private val SWIPE_ORDER = listOf(Routes.HOME, Routes.LIBRARY, Routes.GROUPS, Routes.PLAN, Routes.TEST_CONFIG)

/** +1 si el destino queda a la derecha del origen, -1 a la izquierda, 0 si no son secciones principales. */
private fun swipeDirection(from: String?, to: String?): Int {
    val a = SWIPE_ORDER.indexOf(from)
    val b = SWIPE_ORDER.indexOf(to)
    return if (a < 0 || b < 0) 0 else b.compareTo(a)
}

private fun slideSide(direction: Int) =
    if (direction > 0) AnimatedContentTransitionScope.SlideDirection.Left
    else AnimatedContentTransitionScope.SlideDirection.Right
