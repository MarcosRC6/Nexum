# NEXUM 2.0 — Puerta de publicación estable (21/11/2026)

**Dictamen actual: NO GO para publicar la Release estable sin verificar el RC.**

## Repositorio verificado

- Última prerelease examinada: `v2.0-beta.10`, publicación firmada, `versionCode=24`, fuente SHA `91c95e1d79f17c2e059a837f535d4f72ce0e7f62`.
- `main` protegida se mantiene en `v1.8`, SHA `9ca83d6557ba85e445820f49d2225c66caac48a9`.
- Beta 10 tuvo CI y publicación firmada **PASS**. La CI no ejecuta los tests `androidTest` sobre hardware.
- PR draft de preparación `#57`: deriva **exclusivamente** de Beta 10; no integrar arbitrariamente PR históricas divergentes.

## Hallazgos confirmados por lectura del código

| Prioridad | Área | Defecto o riesgo | Medida |
|---|---|---|---|
| P0 | Testario `TestarioLocalStore.saveDatabase` | Se publicaba el cambio en memoria antes de la escritura asíncrona. Si fallaba `AtomicFile`, únicamente hacía `printStackTrace`, sin notificación; riesgo de falsa confirmación y desaparición del último cambio tras cierre | RC: aviso visible, bloqueo de nuevas mutaciones, posibilidad de exportar ZIP de emergencia. Para plena garantía aún falta test de fallos en Android real |
| P0 | Planario `StudyLocalDataSource.load/save` | Una planificación JSON ilegible se copiaba como recuperación y la UI veía una lista vacía, pero un guardado normal podía sustituir el estado activo | RC: bloqueo persistente contra escrituras normales hasta restauración validada. Test `androidTest` añadido, **pendiente de ejecutar** |
| P0 | Planario `StudyRepository.importBackupJson` | Actualizaba en memoria temas/objetivos/días/tiempos/ajustes ANTES de confirmar `SharedPreferences.commit` | RC: persistir primero, después publicar los cinco `StateFlow`, y por último actualizar widgets |
| P0 | Cronómetro al cambio de día | `MutableSharedFlow(replay=0)` podía descartar el cierre automático sin suscriptores después de borrar el snapshot del reloj | RC: ante ausencia de observador, conservar sesión anterior como PAUSED recuperable en lugar de borrar; test JVM añadido. Faltan tests de proceso/servicio físico y cierre de día |
| P1 | Restauración global `UnifiedDataManager` | Varios almacenes y medios se restauran sucesivamente; se guarda ZIP preventivo y se intenta revertir, pero NO hay transacción ACID | Probar fallos inyectados tras cada subfase y certificar recuperación |
| P1 | Importación unificada `SettingsScreen` | `InputStream.readBytes()` carga íntegro el ZIP en memoria; puede causar OOM en backups grandes | Pendiente: lectura con límites y staging en disco sin dejar fuera backups legítimos |
| P1 | Imágenes/Anki | Zoom, creación de máscaras con un toque y esquina pequeña usan gestos Compose, solo comprobados mediante compilación y tests de reglas | QA físico necesario |
| P1 | Persistencia del cronómetro | Persistencia de heartbeat con `SharedPreferences.apply()` y cambios en foreground, sin prueba de fallos/kill para todos los Android | Verificar reanudación, cancelación, notificación, fecha y ahorro de batería |
| P2 | Funciones previstas | Sincronización incremental multidispositivo y compatibilidad `.apkg` todavía no integradas | No presentar como disponibles en Release 2.0 |

## Pruebas para autorizar salida

1. Gradle `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, `lintDebug` y auditoría de secretos y recursos **PASS en el SHA exacto**.
2. Ejecución de `PlanarioRecoveryInstrumentedTest` y pruebas de estrés/fallo de guardado de Testario sobre Android/emulador (la compilación de tests sola **no** las ejecuta).
3. Copia global completa fuera del dispositivo, restauración verificable con datos ficticios, forzar errores después de imágenes/BD/Plan/ajustes/enlaces/sesiones y recuperación.
4. ZIP de 1/2/5/20/40 temas, repetido, con subgrupos, plantillas, estadísticas e imágenes; comprobar que no se pierden ni cruzan IDs, temas ni marcadores.
5. Caso usuario: imagen Anki a pantalla completa de un toque; máscaras por toque, selección, movimiento/esquinas y zoom; imagen+texto sin máscara; escrito con 3 respuestas, error en 3 y punto ROJO; banderas Test/Escrita/Anki y filtro.
6. Cronómetro en notificación, pantalla bloqueada, importación, cierre de actividad, ahorro de batería, medianoche y restauración; guardar sin doble contabilización.
7. Widgets Hoy, Test rápido, Pomodoro y enlaces (con solo «Enlaces» activado), rollover de día y restaurar preferencias.
8. Comparación y merge PR RC contra `main` protegiendo historial y datos. Estable `2.0` debe llevar `versionCode >=25` para actualizar encima de Beta 10 (code24), **misma firma y paquete**.
9. Confirmación del propietario tras el smoke de los gestos y la persistencia. La autorización anterior era **condicional** a que la aplicación esté lista, no elimina estos controles.

## Límites
- No se ha tocado el teléfono del usuario ni sus datos, ni instalado o publicado otro APK.
- Código fuente y CI PASS no sustituyen pruebas de restauración, compatibilidad física, integridad en fallos y actualización del APK con la firma oficial.
- No publicar `v2.0` ni fusionar a `main` hasta completar la puerta de salida.
