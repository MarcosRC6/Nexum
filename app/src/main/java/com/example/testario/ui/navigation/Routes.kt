package com.example.testario.ui.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val TOPIC_DETAIL = "topic/{topicId}"
    const val QUESTION_EDITOR = "question/{topicId}?questionId={questionId}"
    const val TEST_CONFIG = "testConfig?preselectedTopicId={preselectedTopicId}&templateId={templateId}"
    const val GROUPS = "groups"
    const val TEST_SCREEN = "testScreen"
    const val PLAN = "plan/{tab}"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val SETTINGS_HOME = "settingsHome"

    fun topicDetail(topicId: String) = "topic/$topicId"

    fun questionEditor(topicId: String, questionId: String? = null): String {
        return if (questionId == null) {
            "question/$topicId"
        } else {
            "question/$topicId?questionId=$questionId"
        }
    }

    fun testConfig(preselectedTopicId: String? = null, templateId: String? = null): String {
        val params = buildList {
            if (preselectedTopicId != null) add("preselectedTopicId=$preselectedTopicId")
            if (templateId != null) add("templateId=$templateId")
        }
        return if (params.isEmpty()) "testConfig" else "testConfig?${params.joinToString("&")}"
    }

    fun plan(tab: String = "plan") = "plan/$tab"
}
