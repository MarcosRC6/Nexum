# Ajustes de interfaz pendientes de validación física — 18/11/2026

Fuente: nota «Añadir a app», cuatro capturas del usuario.

## Decisiones del usuario
- Notificación del cronómetro: tiempo y controles únicamente; sin frase «El tiempo sigue contando…».
- Selector Android de widgets: distinguir qué widget se elige y mostrar ejemplos reconocibles.
- «Test rápido»: panel horizontal compacto **2 columnas × 1 fila**, con **Iniciar test** y selección de plantilla.
- Mantener «Hoy» como widget de utilidad. El widget «Calendario» sobra y se elimina del selector Android (el calendario dentro de Plan no cambia).
- Un widget de reloj es opcional y **no forma parte de este cambio**.
- Sin contorno neón en la flecha de Grupos ni en los botones de menú de preguntas y selección en Biblioteca.
- Conservar los bordes neón de las acciones y opciones realmente seleccionables; no cambiar reglas globales de estilo.

## Implementación preparada (no publicada)
- Se conserva el cronómetro Android nativo de la notificación y sus acciones. Se elimina solamente el texto redundante durante RUNNING.
- Se cambian a iconos neutros los menús e iconos auxiliares de Detalle de tema y la flecha de expandir grupos.
- El widget Test rápido conserva el ID de plantilla por instancia, el acceso directo al test, su configuración y la gestión de plantillas eliminadas; pasa a diseño de 2 × 1 con pulsación sobre toda el área principal y botón de configuración.
- El selector de widgets recibe etiquetas independientes y previsualizaciones vectoriales de «Test rápido» y «Hoy». Se desregistra el proveedor «Calendario», sin borrar datos ni código de calendario.
- Sin cambios de `applicationId`, `versionCode`, firma, formatos de backup ni estructura de preguntas.

## Pruebas físicas obligatorias antes de merge o Release
1. Launcher Android: aparecen etiquetas y vistas previas diferenciadas en los dos widgets; «Calendario» no aparece; comprobar Android 12/13+ y el launcher de la tablet de las capturas.
2. «Test rápido»: ocupa 2 × 1, el texto no se corta de manera inusable, comienza un test por defecto y por plantilla, el engranaje permite configurar de nuevo, plantilla eliminada redirige a configuración, dos instancias mantienen configuraciones independientes.
3. «Hoy»: sigue mostrando el resumen correcto, actualiza tras estudiar, no ha perdido navegación.
4. Cronómetro: RUNNING muestra cronómetro dinámico y no mensaje redundante, PAUSED conserva el tiempo, «Guardar tiempo» no descarta la sesión; bloquear pantalla, cerrar app, importar datos.
5. Biblioteca: expandir/cerrar grupos y menús «⋮»/filtros/selección siguen operativos y ya no dibujan marcos neón superfluos.
6. Comprobar widget «Calendario» previamente añadido: su proveedor ya no existe en el manifiesto, por lo que puede desaparecer o quedar sin actualizar; anunciar el cambio antes de distribuir una APK.
7. Mantener instalada la APK oficial 1.6 hasta validar una versión oficial con firma idéntica. **Nunca instalar debug sobre la oficial.**

## Ampliación autorizada en la misma PR — Estudio, Pomodoro y vista previa
- Nuevo widget «NEXUM · Estudio y Pomodoro» de referencia **4 × 3**. La cara Estudio muestra el contador transcurrido del controlador REAL de Planario (no almacena ni suma otro tiempo), Iniciar/Pausar/Reanudar y Guardar tiempo. La cara Pomodoro muestra una cuenta atrás real (duración configurada desde Reloj), Iniciar/Pausar/Reanudar y Reiniciar, sin añadir minutos de estudio por sí misma.
- Android estándar sólo permite toque y deslizamiento vertical en widgets: el intercambio entre caras se realiza con controles **‹ / ›**; no se declara compatibilidad falsa con swipe horizontal. La pantalla de configuración permite alternar la vista previa pulsando su cabecera. Documentación oficial: https://developer.android.com/develop/ui/views/appwidgets/overview.
- Persistencia Pomodoro independiente de los registros de tiempo y recuperación conservadora tras muerte de proceso; ante reinicio Android o salto de reloj, PAUSED sin inventar minutos.
- Controlador, app, widget y servicio de primer plano comparten los mismos estados y duraciones. Con Pomodoro activo se mantiene el servicio de notificación; no se crea otro cronómetro de estudio.
- Widget Hoy: versión compacta auténtica para 2×2 / alturas pequeñas con minutos, progreso y objetivos; secciones de detalle/semana sólo cuando el tamaño permite mostrarlas. Configuración por instancia AUTO / COMPACT / DETAIL, con vista previa usando datos locales. Los widgets ya existentes no pierden su estado, y AUTO es el modo predeterminado.
- Test rápido: vista previa reactiva al cambiar de plantilla. Android también recibe previews vectoriales diferenciadas para Hoy, Test rápido y Estudio/Pomodoro.
- Las configuraciones de widgets y Pomodoro se guardan exclusivamente en SharedPreferences locales. Sin permisos peligrosos nuevos, cambios de backup de bancos, firma, `applicationId`, versionado ni sincronización remota.

### Pruebas adicionales antes de autorizar merge
1. Widget Estudio desde escritorio (4×3): iniciar, pausar, reanudar, guardar una sola vez y comprobar Plan; no debe duplicar tiempos al abrir/cerrar NEXUM.
2. Widget Pomodoro: duración seleccionada en Reloj, cuenta atrás en el escritorio, pausa/reanudación, finalización y reinicio; no sumar horas al estudio.
3. Cerrar actividad, bloquear pantalla, matar proceso y reiniciar teléfono; estudiar y Pomodoro deben recuperar/preservar estado correctamente, sin minutos inventados.
4. Widget Hoy 2×2, 4×3, 4×5 y más: contenido sin cortes; cambiar AUTO/COMPACT/DETAIL, redimensionar y restaurar widgets sin perder preferencia.
5. Test rápido, Hoy y Estudio: selector Android con nombres y vistas previas independientes, configurar dos instancias y verificar que una no modifica la otra.
6. Tests JVM: `PomodoroRecoveryPolicyTest` y `NexumTodaySizePolicyTest`; pasar además `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, `lint` y control de seguridad en CI.
7. Validar controles y notificaciones en **Android real**. El sistema puede variar según launcher; no se ha probado físicamente al crear esta rama.

**Estado**: cambios sólo en rama `feature/widget-ux-neutral-icons-20261118` y PR #25 borrador; prohibido merge/release sin autorización.

### Acceso «Iniciar test» en Hoy ampliado
- En el tamaño grande, cuando hay un objetivo diario con temas o plantilla de Testario vinculados, la tercera acción de Hoy pasa de «Calendario» a **«Iniciar test»**; si no hay vínculo, sigue siendo «Calendario».
- Se reutiliza la ruta del widget Test rápido y `QuickTestLaunchResolver`; el widget comunica la lista de IDs del objetivo, su cantidad de preguntas y su plantilla (si existe), sin generar un banco ni inventar configuraciones nuevas.
- Si hay varios objetivos vinculados se prioriza el primero pendiente según orden; el selector de objetivos y todas las configuraciones históricas permanecen intactos.
- Comprobar física y funcionalmente que la plantilla con sus opciones reales abre correctamente y que una vinculación obsoleta informa del error sin iniciar un test distinto.
