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
