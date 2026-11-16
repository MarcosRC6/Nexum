package com.example.testario.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** User-visible names only; legacy ZIP entry names remain import-compatible. */
object NexumExportNames {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
    fun filename(
        section: String, variant: String, extension: String,
        partial: Boolean = false, date: LocalDateTime = LocalDateTime.now()
    ): String {
        require(Regex("[A-Z_]+").matches(section))
        require(Regex("[A-Z_]+").matches(variant))
        require(extension == "zip" || extension == "json")
        val suffix = if (partial) "_PARCIAL" else ""
        return "NEXUM_${section}_${variant}${suffix}_${date.format(formatter)}.${extension}"
    }
    fun fullBackup(date: LocalDateTime = LocalDateTime.now()) =
        filename("TODO", "COMPLETO", "zip", date = date)
    fun plan(date: LocalDateTime = LocalDateTime.now()) =
        filename("PLANIFICACION", "COMPLETO", "json", date = date)
    fun library(zip: Boolean, partial: Boolean, date: LocalDateTime = LocalDateTime.now()) =
        filename("BIBLIOTECA", if (zip) "COMPLETO" else "SIMPLE", if (zip) "zip" else "json", partial, date)
}
