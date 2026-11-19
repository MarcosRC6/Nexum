package com.example.testario.domain

import com.example.testario.data.model.ClozeGap

/**
 * Canonical cloze notation for NEXUM cards.
 *
 * {{term}}    -> hidden on EVERY round
 * {{1:term}}  -> hidden only on numbered round 1
 * {{2:term}}  -> hidden only on numbered round 2
 *
 * [[term]] / [[2:term]] are optional, equivalent keyboard syntax.
 * UI selection is the preferred authoring interface; markup is not required.
 *
 * All positions refer to the CLEAN text, never the authoring markup. Parsing is
 * strict: malformed tokens are rejected instead of silently losing a solution.
 */
object ClozeMarkupEngine {
    data class Parsed(val text: String, val gaps: List<ClozeGap>)

    fun parse(source: String): Parsed {
        val out = StringBuilder(source.length)
        val gaps = mutableListOf<ClozeGap>()
        var i = 0
        while (i < source.length) {
            // Escape only a markup opener, not arbitrary ordinary backslashes.
            if (source[i] == '\\' && i + 2 < source.length &&
                (source.startsWith("{{", i + 1) || source.startsWith("[[", i + 1))) {
                out.append(source, i + 1, i + 3)
                i += 3
                continue
            }
            val opener = when {
                source.startsWith("{{", i) -> "{{"
                source.startsWith("[[", i) -> "[["
                else -> null
            }
            if (opener == null) {
                out.append(source[i])
                i++
                continue
            }
            val closer = if (opener == "{{") "}}" else "]]"
            val end = source.indexOf(closer, i + 2)
            require(end >= 0) { "Hay un marcador sin cerrar en la posición ${i + 1}." }
            val body = source.substring(i + 2, end)
            require(!body.contains("{{") && !body.contains("[[") &&
                !body.contains("}}") && !body.contains("]]")) {
                "No se pueden anidar marcadores de fragmento."
            }
            val number = Regex("^([1-9][0-9]*):", RegexOption.MULTILINE).find(body)
                ?.takeIf { it.range.first == 0 }
            val group = number?.groupValues?.get(1)?.toIntOrNull()
            if (number != null) require(group != null && group <= 999) {
                "El número del fragmento debe estar entre 1 y 999."
            }
            val value = if (number != null) body.substring(number.value.length) else body
            require(value.isNotBlank()) { "No puede ocultarse un fragmento vacío." }
            require(value.length <= 1000) { "El fragmento seleccionado es demasiado largo." }
            val start = out.length
            out.append(value)
            gaps += ClozeGap(start = start, end = out.length, groupNumber = group)
            i = end + 2
        }
        return Parsed(out.toString(), gaps)
    }

    /** Rebuild authoring markup from stored clean text and positions, including old ClozeGap records. */
    fun encode(clean: String, gaps: List<ClozeGap>): String {
        if (gaps.isEmpty()) return escapeOpeners(clean)
        val sorted = gaps.sortedBy { it.start }
        var cursor = 0
        val out = StringBuilder(clean.length + gaps.size * 7)
        for (gap in sorted) {
            require(gap.start >= cursor && gap.end <= clean.length && gap.end > gap.start) {
                "Posiciones de fragmentos solapadas o inválidas."
            }
            out.append(escapeOpeners(clean.substring(cursor, gap.start)))
            out.append("{{")
            gap.groupNumber?.let { out.append(it).append(':') }
            out.append(clean, gap.start, gap.end)
            out.append("}}")
            cursor = gap.end
        }
        out.append(escapeOpeners(clean.substring(cursor)))
        return out.toString()
    }

    private fun escapeOpeners(s: String): String = s.replace("{{", "\\{{").replace("[[", "\\[[")

    /** Remove one selected marker without altering any other marked fragment. */
    fun unmark(source: String, ordinal: Int): String {
        require(ordinal >= 0) { "Índice de fragmento inválido." }
        var i = 0
        var n = 0
        while (i < source.length) {
            if (source[i] == '\\' && i + 2 < source.length &&
                (source.startsWith("{{", i + 1) || source.startsWith("[[", i + 1))) {
                i += 3
                continue
            }
            val open = when {
                source.startsWith("{{", i) -> "{{"
                source.startsWith("[[", i) -> "[["
                else -> null
            }
            if (open == null) { i++; continue }
            val end = source.indexOf(if (open == "{{") "}}" else "]]", i + 2)
            require(end >= 0) { "Marcador sin cerrar." }
            if (n == ordinal) {
                val body = source.substring(i + 2, end)
                val numbered = Regex("^[1-9][0-9]*:").find(body)
                val shown = if (numbered != null) body.substring(numbered.value.length) else body
                return source.substring(0,i) + shown + source.substring(end + 2)
            }
            i = end + 2
            n++
        }
        throw IllegalArgumentException("No existe el fragmento seleccionado.")
    }

    /** Choose numbered turn deterministically, preserving default always-hidden gaps. */
    fun numberedGroups(gaps: List<ClozeGap>): List<Int> =
        gaps.mapNotNull { it.groupNumber }.distinct().sorted()

    fun activeGroup(gaps: List<ClozeGap>, cursor: Int): Int? {
        val groups = numberedGroups(gaps)
        if (groups.isEmpty()) return null
        return groups[Math.floorMod(cursor, groups.size)]
    }

    fun activeGaps(gaps: List<ClozeGap>, cursor: Int): List<ClozeGap> {
        val active = activeGroup(gaps, cursor)
        return gaps.filter { it.groupNumber == null || it.groupNumber == active }
    }

    fun mask(text: String, gaps: List<ClozeGap>, cursor: Int): String {
        val ordered = activeGaps(gaps, cursor).sortedByDescending { it.start }
        var rendered = text
        for (gap in ordered) {
            require(gap.start >= 0 && gap.end <= rendered.length && gap.end > gap.start) {
                "El fragmento no corresponde al texto de la tarjeta."
            }
            rendered = rendered.replaceRange(gap.start, gap.end, "________")
        }
        return rendered
    }

    /**
     * Wrap selected text from a TextFieldValue in notation. Caret selection
     * boundaries refer to the markup currently shown in that editor.
     */
    fun markSelection(source: String, start: Int, end: Int, group: Int?): String {
        require(start in 0 until end && end <= source.length) { "Selecciona texto para marcar." }
        require(group == null || group in 1..999) { "Número de turno inválido." }
        val selected = source.substring(start, end)
        require(selected.isNotBlank()) { "Selecciona una palabra o frase no vacía." }
        require(!selected.contains("{{") && !selected.contains("}}") &&
            !selected.contains("[[") && !selected.contains("]]")) {
            "Selecciona texto sin marcadores ya existentes."
        }
        return source.substring(0, start) +
            "{{" + (group?.let { "$it:" } ?: "") + selected + "}}" +
            source.substring(end)
    }
}
