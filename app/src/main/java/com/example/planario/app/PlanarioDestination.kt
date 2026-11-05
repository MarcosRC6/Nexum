package com.example.planario.app

sealed class PlanarioDestination(
    val route: String,
    val label: String,
    val iconText: String
) {
    data object Home : PlanarioDestination(
        route = "home",
        label = "Inicio",
        iconText = "🏠"
    )

    data object Plan : PlanarioDestination(
        route = "plan",
        label = "Plan",
        iconText = "📝"
    )

    data object Calendar : PlanarioDestination(
        route = "calendar",
        label = "Calendario",
        iconText = "📅"
    )

    data object Timer : PlanarioDestination(
        route = "timer",
        label = "Reloj",
        iconText = "⏱️"
    )

    data object Settings : PlanarioDestination(
        route = "settings",
        label = "Ajustes",
        iconText = "⚙️"
    )
}

val bottomBarDestinations = listOf(
    PlanarioDestination.Home,
    PlanarioDestination.Plan,
    PlanarioDestination.Calendar,
    PlanarioDestination.Timer,
    PlanarioDestination.Settings
)