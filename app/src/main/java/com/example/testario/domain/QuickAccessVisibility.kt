package com.example.testario.domain

/** Pure visibility rule: a Links-only home must not disappear. */
object QuickAccessVisibility {
    fun show(
        library: Boolean,
        test: Boolean,
        plan: Boolean,
        statistics: Boolean,
        links: Boolean
    ): Boolean = library || test || plan || statistics || links
}
