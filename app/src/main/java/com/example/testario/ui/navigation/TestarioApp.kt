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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.FastOutSlowInEasing
import com.example.testario.ui.theme.TestarioBackground
import androidx.compose.ui.platform.LocalConfiguration
import com.example.testario.ui.screen.LocalNexumRail
import com.example.testario.ui.screen.LocalShowQuestionOrigin
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

    // Las cinco secciones principales viven en un paginador que sigue al dedo.
    // La ruta sigue siendo la fuente de verdad: navegar mueve el paginador, y soltar el
    // paginador en otra página navega a su ruta.
    val pagerState = rememberPagerState(pageCount = { MAIN_ROUTES.size })
    val pageStateHolder = rememberSaveableStateHolder()

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

    // "keep" navega a Plan sin cambiar la pestaña que tenía abierta.
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
            val isMainRoute = route == null || route in MAIN_ROUTES
            // El paginador sigue compuesto un instante tras abrir una pantalla de detalle para
            // que la transición no deje el fondo vacío.
            var keepPager by remember { mutableStateOf(true) }
            LaunchedEffect(isMainRoute) {
                if (isMainRoute) keepPager = true else { kotlinx.coroutines.delay(450); keepPager = false }
            }
            val showPager = isMainRoute || keepPager
            val pagerDriven = remember { booleanArrayOf(false) }
            var previousWasMain by remember { mutableStateOf(true) }
            val currentRoute by rememberUpdatedState(route)

            // Navegar (cápsula, botones, enlaces) mueve el paginador.
            LaunchedEffect(route) {
                val target = MAIN_ROUTES.indexOf(route)
                val animate = previousWasMain
                previousWasMain = target >= 0
                if (target >= 0 && target != pagerState.currentPage) {
                    pagerDriven[0] = true
                    try {
                        if (animate) {
                            // En saltos largos no se dibujan las pantallas intermedias: se salta
                            // junto a la de destino y solo se anima el último tramo.
                            val distance = target - pagerState.currentPage
                            if (kotlin.math.abs(distance) > 1) {
                                pagerState.scrollToPage(target - Integer.signum(distance))
                            }
                            pagerState.animateScrollToPage(target)
                        } else pagerState.scrollToPage(target)
                    } finally {
                        pagerDriven[0] = false
                    }
                }
            }
            // Soltar el paginador en otra página navega a su ruta.
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    val now = currentRoute
                    if (!pagerDriven[0] && now in MAIN_ROUTES && MAIN_ROUTES[page] != now) {
                        when (page) {
                            0 -> goHome()
                            1 -> goLibrary()
                            2 -> goGroups()
                            3 -> goPlan("keep")
                            else -> goTest()
                        }
                    }
                }
            }
            // Posición fraccionaria de la cápsula: Biblioteca y Grupos comparten sección, y
            // Plan, Reloj y Calendario también.
            val capsulePosition = remember(pagerState) {
                { pageToCapsule(pagerState.currentPage + pagerState.currentPageOffsetFraction) }
            }
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
            CompositionLocalProvider(
                LocalNexumRail provides (showRail || capsuleSection != null),
                LocalShowQuestionOrigin provides behaviorSettingsViewModel.showQuestionOrigin
            ) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showPager) {
                val planArgs = if (route == Routes.PLAN) currentBackStackEntry?.arguments?.getString("tab") else null
                val configEntry = if (route == Routes.TEST_CONFIG) currentBackStackEntry else null
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (showCapsule) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                    key = { it },
                    // Las páginas vecinas ya están compuestas al empezar a arrastrar, así que
                    // el gesto no paga el coste de dibujar una pantalla entera.
                    beyondViewportPageCount = 1
                ) { page ->
                    pageStateHolder.SaveableStateProvider(page) {
                        when (page) {
                            0 -> HomeScreen(
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
                            1 -> LibraryTopicsScreen(
                                libraryViewModel = libraryViewModel,
                                statsViewModel = statsViewModel,
                                onOpenTopic = { navController.navigate(Routes.topicDetail(it)) },
                                onHome = { goHome() },
                                onCreateTest = { goTest() },
                                onPlan = { goPlan() },
                                onGroups = { goGroups() },
                                onStats = { goStats() },
                                onSettings = { goSettings() }
                            )
                            2 -> GroupsScreen(
                                libraryViewModel = libraryViewModel,
                                onHome = { goHome() },
                                onTopics = { goLibrary() },
                                onCreateTest = { goTest() },
                                onPlan = { goPlan() },
                                onStats = { goStats() },
                                onSettings = { goSettings() }
                            )
                            3 -> UnifiedPlanScreen(
                                libraryViewModel = libraryViewModel,
                                initialTab = planArgs,
                                onHome = { goHome() },
                                onLibrary = { goLibrary() },
                                onTest = { goTest() },
                                onStats = { goStats() },
                                onSettings = { goSettings() },
                                onStartQuickTest = { topicIds, count, templateId ->
                                    launchLinkedQuickTest(topicIds, count, templateId)
                                }
                            )
                            else -> {
                                val topicArg = configEntry?.arguments?.getString("preselectedTopicId")
                                val templateArg = configEntry?.arguments?.getString("templateId")
                                key(topicArg, templateArg) {
                                    TestConfigScreen(
                                        libraryViewModel = libraryViewModel,
                                        sessionViewModel = sessionViewModel,
                                        mixedSessionViewModel = mixedSessionViewModel,
                                        preselectedTopicId = topicArg,
                                        preselectedTemplateId = templateArg,
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
                            }
                        }
                    }
                }
            }
            // Las secciones principales se dibujan en el paginador de arriba: sus destinos
            // solo existen para la pila de navegación y quedan vacíos.
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 14 } }
            ) {
            composable(Routes.HOME) {
                BackHandler(enabled = timerState.studyTimerState == StudyTimerState.RUNNING) {
                    showTimerExitDialog = true
                }
            }
            composable(Routes.LIBRARY) { }
            composable(Routes.GROUPS) { }

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

            composable(Routes.TEST_CONFIG) { }

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

            composable(Routes.PLAN) { }

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
            }
            if (showCapsule && capsuleSection != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(TestarioBackground)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NexumCapsule(
                        position = capsulePosition,
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

/** Páginas del paginador principal, en orden, con la ruta que representa cada una. */
private val MAIN_ROUTES = listOf(Routes.HOME, Routes.LIBRARY, Routes.GROUPS, Routes.PLAN, Routes.TEST_CONFIG)

/** Página fraccionaria -> posición en la cápsula (Inicio 0, Biblioteca 1, Plan 2, Test 3). */
private fun pageToCapsule(page: Float): Float {
    val sections = floatArrayOf(0f, 1f, 1f, 2f, 3f)
    val p = page.coerceIn(0f, sections.lastIndex.toFloat())
    val i = p.toInt().coerceAtMost(sections.lastIndex - 1)
    return sections[i] + (sections[i + 1] - sections[i]) * (p - i)
}
