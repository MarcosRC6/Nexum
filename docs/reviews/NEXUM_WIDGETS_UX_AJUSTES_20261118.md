# NEXUM — segunda revisión de widgets (capturas reales del 18/11/2026)

Base: NEXUM 1.7, `main` commit `8c166be2fecc6e6e37ef3273e116430b75c7f29f`.
Solicitud: mejorar tamaños, información y configuración de los tres widgets a partir de capturas reales de tablet.

## Widgets y UX acordada

### Hoy
- El **formato compacto** enseña exclusivamente «Tema activo», el nombre del tema, el tiempo total de hoy respecto al objetivo y una barra de progreso. No muestra objetivos ni marcos adicionales.
- El **formato ampliado** añade objetivos diarios reales, ordenados, marcando los realizados, con un resumen de cuántos faltan y acceso a Plan. Los accesos «Iniciar test» que ya existían para objetivos vinculados se conservan si hay espacio suficiente.
- El tamaño, no un modo persistido de NEXUM 1.7, determina qué información cabe. Redimensionar hacia abajo vuelve a compacto y hacia arriba permite ver objetivos.
- El diseño ajusta el número de objetivos visibles a la altura realmente disponible; no deja un gran panel vacío con una tarjeta pequeña en la esquina.

### Test rápido
- Configuración **por instancia**, con tres alternativas explícitas:
  - **Todos mis temas** con número de preguntas configurable (1–500).
  - **Un tema concreto** con su nombre e ID estable y número de preguntas configurable.
  - **Plantilla guardada**, conservando las opciones completas de la plantilla, incluido su número de preguntas.
- Mantener el engranaje para reconfigurar después y la vista previa actualizada al seleccionar opciones.
- No duplicar bancos ni lógica de creación de test. Las opciones se envían por el flujo ya existente de `MainActivity` a `QuickTestLaunchResolver`.
- Los widgets anteriores que solo guardaban un ID de plantilla siguen reconociéndose; se migran al guardar la nueva configuración.

### Estudio y Pomodoro
- Mantener el formato normal **4×3 por defecto** y permitir reducirlo a un diseño compacto para el launcher (en torno a **2×2**).
- Quitar los accesos «NEXUM · Inicio» y «Reloj» en las esquinas inferiores; pulsar el cuerpo/contador del widget, salvo flechas y botones, abre directamente la pantalla Reloj.
- Conservar ambas caras alternables con flechas, todos los controles y el cronómetro de estudio existente.
- Un Pomodoro STOPPED/FINISHED debe mostrar la duración configurada en **Plan → Reloj**, no los 25 minutos iniciales del controlador antes de abrir la pantalla Reloj. El ajuste se sincroniza al iniciar las dependencias y al pulsar Iniciar desde el widget; no reiniciar una sesión RUNNING ni PAUSED.
- Mostrar los segundos detenidos exactamente: `60:00` y no un `59:59` producido por los instantes de representación de RemoteViews.

## Implementación técnica
- Ajustadas las restricciones `minResizeWidth`, `minResizeHeight` y `resizeMode` de los metadatos; los tres widgets admiten reajuste de tamaño en ambos ejes.
- **Android 12+:** se publican alternativas de `RemoteViews(Map<SizeF, RemoteViews>)` para que el sistema cambie automáticamente entre tamaños reales. **Android 8–11:** se conserva la selección por `OPTION_APPWIDGET_MIN_WIDTH/HEIGHT`.
- No se cambian `applicationId`, `versionCode`, certificado oficial, formato de backups ni datos personales. No añadir permisos.
- El launcher decide la cuadrícula y puede conservar metadatos antiguos de widgets ya situados: si no redimensiona tras instalar una futura actualización, retirar **solo el widget** y volverlo a colocar; no desinstalar la app ni borrar datos.

## Validación requerida antes de merge/publicación
1. `testDebugUnitTest`, Debug APK, preview paralelo, compilación Release, Lint y publication security gate.
2. La tablet de las capturas: añadir los tres widgets de nuevo y redimensionarlos paso a paso; verificar formato compacto/expandido, sin texto cortado y sin huecos gigantes.
3. Hoy: sincronización del tema, tiempo, progreso y objetivos completados/pedientes; que no se confundan los objetivos fijos del día con plantillas de test.
4. Test rápido: todos los temas + 1/10/30 preguntas; tema concreto; plantilla; cambio por engranaje; varios widgets distintos; plantillas o temas eliminados deben requerir reconfigurar.
5. Reloj: alternar caras, 4×3 y 2×2, toque fuera de botones abre Reloj, control independiente de iniciar/pausar/reanudar/guardar/reiniciar.
6. Pomodoro: duración de **60 minutos** configurada en Plan, apertura primero por widget y primero por app; STOPPED 60:00, RUNNING 59:59 después de un segundo, PAUSED estable; no retroceder a 25 al cambiar de tamaño o proceso.
7. No mezclar las horas de estudio y el Pomodoro, no perder minutos, sin modificaciones de datos ni de firma.
8. No publicar ni fusionar esta rama sin nueva autorización explícita del propietario.

Referencia de Android: https://developer.android.com/develop/ui/views/appwidgets/layouts
