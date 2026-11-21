# NEXUM 2.0 · Auditoría técnica previa a estabilidad — 21/11/2026

## Alcance, fuente y restricciones
- Base de inspección: **release pública NEXUM 2.0 Beta 8**, tag `v2.0-beta.8`, código `55d5990e6ac53aab31714280f9296bf03d430ffc`, versionCode **22**.
- `main` permanece en v1.8 SHA `9ca83d6557ba85e445820f49d2225c66caac48a9`. Los cambios de esta auditoría están en una rama nueva/PR draft, nunca en `main`.
- Inspección de código Kotlin/Compose, tests y workflows GitHub; **sin acceso al dispositivo ni a los bancos privados del usuario**. La existencia de tests y CI no acredita comportamiento en hardware.
- Invariantes: `com.example.estudario`, firma oficial, esquema v7, IDs/estadísticas/preguntas/plantillas/sesiones y cuatro pestañas. Prohibido downgrade, reinstalación destructiva y publicación sin autorización.

## Hallazgos y dictamen

| ID | Severidad | Diagnóstico acreditado por código | Estado |
| --- | --- | --- | --- |
| I01 | P0 | `TopicImportMatcher.kt` eliminaba todos los operadores y separadores numéricos al construir la huella. Distintas preguntas (`C++`/`C#`, `1,5`/`15`) podían descartarse como duplicadas dentro de un tema | **CORREGIDO EN PR DRAFT**; tests nuevos, CI pendiente de cierre |
| I02 | P0 | `QuestionSyncMatcher.kt` utilizaba la misma normalización destructiva para emparejar/resignar IDs de respuestas, mientras `ImportManager.normalizeText` la aplicaba a enunciado y respuesta correcta en sincronización | **CORREGIDO EN PR DRAFT**; misma normalización conservadora compartida y tests |
| U01 | P2 | `StudyExerciseEditorScreen.kt` todavía mostraba un botón de `Vista previa de Anki` que el propietario solicitó retirar | **CORREGIDO EN PR DRAFT**, sin cambio de modelos |
| I03 | P1 | `ExportManager.exportCompleteZip` incluye `groups` y `testTemplates`, pero la importación habitual `ImportManager.importBatchProcessed` solo escribe `topics`. La organización incluida en un paquete de biblioteca no se incorpora mediante ese flujo. No afecta a la restauración global, que usa otro camino | **PENDIENTE**: definir importación acumulativa de grupos/plantillas con remapeo de IDs y prueba de round-trip; bloquear afirmaciones de compatibilidad integral hasta cubrirlo |
| R01 | P0 (QA) | `UnifiedDataManager.restoreGlobalBackup` aplica imágenes, DB, Plan, enlaces, ajustes y sesiones en operaciones separadas. Existe ZIP preventivo y el flujo público `NexumNearbyArchive` intenta revertir, pero no hay atomicidad transaccional universal entre almacenes | **PENDIENTE DE PRUEBA DE FALLOS**: simular fallo de disco tras cada subfase, comprobar recuperación íntegra |
| R02 | P1 | `SettingsScreen` usa `InputStream.readBytes()` para el importador unificado antes de identificar formato. No existe límite de lectura en esa llamada; con ZIP muy grande podría agotar memoria | **PENDIENTE**: staging con límites explícitos y estrategia para backups grandes sin bloquear bancos existentes |
| M01 | P1 | `ImageManager.persistImageToInternalStorage` denomina `.jpg` a cualquier archivo copiado y devuelve URI temporal si falla, mientras `StudyImageStore` ya incorpora validación MIME/dimensiones para ejercicios. Riesgo de dependencias de URI temporales en caminos clásicos | **PENDIENTE**: revisar todos los llamadores antes de unificar; no cambiar rutas históricas a ciegas |
| S01 | P0 (QA) | `MixedSessionRepository.registerCompletion` registra historia+estadísticas de la sesión mixta mediante `saveDatabaseDurably` e idempotencia por ID. `MixedSessionViewModel` guarda snapshot y lo limpia al finalizar. El sistema es razonable estructuralmente, pero no está cubierto físicamente con kill/restore/sin espacio | **PENDIENTE QA físico/fallos** |
| A01 | P0 (QA) | `WrittenMarkerFeedback.fromGradedResult` utiliza `correctIds` procedentes del mismo baremo, evitando discrepancia entre número rojo/verde y corrección textual. Existe prueba JVM para fallo de nº 3 | **PUBLICADO BETA 8**, requiere verificación física en práctica, examen y revisión |
| A02 | P0 (QA) | `OcclusionTouchEditor` es fullscreen y permite cuatro asas, zoom y pan. Los gestos anidados aún requieren tests hardware (pinch durante dibujo, EXIF, rotación, máscaras pequeñas) | **PUBLICADO BETA 8**, requiere QA |
| W01 | P1 (QA) | `QuickAccessVisibility` permite `links=true` aunque los cuatro accesos antiguos estén apagados. Hay tests unitarios, pero no QA de reinstanciación widget/backup | **PUBLICADO BETA 8**, requiere QA |
| T01 | P0 (QA) | `StudyTimerController`, `StudyTimerForegroundService` y el tracking de Planario deben comprobarse frente a interrupciones, importación y rollover; issue #19 abierta | **PENDIENTE QA y revisión específica** |
| P01 | P1 (decisión) | `AnkiReviewEngine` conserva cuatro ratings y scheduling opcional; interoperabilidad `.apkg` y sync incremental entre dispositivos son trabajo futuro | **SIN MODIFICAR** por política de decisiones |

## Pruebas de regresión obligatorias
1. Instalar solo encima de copia **global completa verificada fuera del móvil**, con misma firma y versión creciente. Nunca desinstalar.
2. Importar 1/2/5/20/40 temas; repetir ZIP dos veces. Comprobar recuento, fingerprint con operadores y decimales, mismo contenido en distinto tema, respuestas correctas, ejercicios e imágenes, sin pérdidas ni duplicados. Contrastar grupos y plantillas.
3. Exportar ZIP global con temas, preguntas, estadísticas, fotos, ejercicios, grupo, plantilla, enlaces, Anki, historial, sesión pausada y Planario; restaurar sobre entorno de prueba y comparar IDs, contenidos y hashes. Forzar errores de disco y comprobar rollback.
4. Editor: Test A/B/C, campo explicación, imágenes por ubicación, Anki sin Vista previa redundante, oclusiones fullscreen con máscaras pequeñas, zoom 1–8× y tap en foto.
5. Respuestas escritas numeradas: solución 1/2/3, fallo en 3 => número 3 rojo; omisión roja, respuesta corregida en verde, puntuación idéntica en revisión y sesión independiente; revertir, editar y reimportar.
6. Sesiones: examen clásico/mixto, retomar tras kill, salir sin guardar, timer en segundo plano, revisión de resultados, historial y estadísticas sin doble registro.
7. Cronómetro y Pomodoro: pantalla bloqueada, cambiar de día/horario, importar temas, sincronización Nearby, guardar tiempo desde notificación sin pérdidas ni doble suma.
8. Widgets: tamaño compacto/expandido, rollover, accesos solo enlaces sin favoritos preexistentes, persistencia y acceso desde Home.
9. Accesibilidad y rendimiento: Android 8+, pantalla pequeña, orientación, fuentes grandes, TalkBack, gran número de temas/imágenes, imports de tamaño alto.
10. Seguridad: checks GitHub Action y revisión de ZIP malformado/zip slip/archivos con colisiones y esquemas futuros, sin secretos ni repositorio de bancos.

## Puertas de salida hacia versión estable 2.0
- **NO GO** mientras queden incidentes P0 de datos o errores reproducibles en Android real sin resolver.
- CI JVM/Gradle/Android compile/Lint/security PASS para el SHA exacto del candidato, además de pruebas físicas PASS documentadas, backups y recuperación.
- Validación del propietario de la interfaz final Testario/Planario y datos intactos.
- Publicación oficial 2.0 y merge a main requieren autorización separada; esta PR no autoriza ni ejecuta esas acciones.

## Fuentes concretas revisadas
`app/src/main/java/com/example/testario/domain/{TopicImportMatcher,QuestionSyncMatcher,ImportManager,ImportBatchPlanner,ExportManager,UnifiedDataManager,NexumNearbyArchive,StudyExerciseRules,WrittenMarkerFeedback,AnkiReviewEngine,MixedSessionRepository,StudyImageStore,ImageManager}.kt`;
`app/src/main/java/com/example/testario/ui/screen/{StudyExerciseEditorScreen,StudyExercisePracticeScreen,MixedSessionScreen,SettingsScreen,OcclusionTouchEditor,StudySketchCanvas,StudyVisualImage,WrittenCorrectionRows}.kt`;
`app/src/main/java/com/example/testario/data/local/{TestarioLocalStore,SavedTestSessionStore,SavedMixedSessionStore,WebQuickLinkStore}.kt`;
`app/src/main/java/com/example/planario/{data/local/StudyLocalDataSource,domain/timer/StudyTimerController,domain/timer/StudyTimerForegroundService}.kt`.
