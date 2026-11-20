package com.example.testario.ui.screen

import androidx.compose.runtime.saveable.Saver
import com.example.testario.data.model.Answer
import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.ClozeGap
import com.example.testario.data.model.ImageAnnotationPoint
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.data.model.SketchStroke
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Non-persistent, per-editor drafts: changing tabs cannot discard unsaved data.
 * These values live only in the navigation back stack's saveable state.
 * They are never added to a real Topic until the user presses Guardar.
 */
private val draftCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
internal val answersDraftSaver = Saver<List<Answer>, String>(
    save = { draftCodec.encodeToString(it) },
    restore = { draftCodec.decodeFromString<List<Answer>>(it) }
)
internal val solutionsDraftSaver = Saver<List<CanonicalSolution>, String>(
    save = { draftCodec.encodeToString(it) },
    restore = { draftCodec.decodeFromString<List<CanonicalSolution>>(it) }
)
internal val strokesDraftSaver = Saver<List<SketchStroke>, String>(
    save = { draftCodec.encodeToString(it) },
    restore = { draftCodec.decodeFromString<List<SketchStroke>>(it) }
)
internal val gapsDraftSaver = Saver<List<ClozeGap>, String>(
    save = { draftCodec.encodeToString(it) },
    restore = { draftCodec.decodeFromString<List<ClozeGap>>(it) }
)
internal val masksDraftSaver = Saver<List<ImageOcclusionMask>, String>(
    save = { draftCodec.encodeToString(it) },
    restore = { draftCodec.decodeFromString<List<ImageOcclusionMask>>(it) }
)
internal val annotationsDraftSaver = Saver<ImageAnnotations, String>(
    save = { draftCodec.encodeToString(it) },
    restore = { draftCodec.decodeFromString<ImageAnnotations>(it) }
)
