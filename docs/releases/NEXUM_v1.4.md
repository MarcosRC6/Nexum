# NEXUM 1.4 — Notas y verificación

## Novedades
- Cronómetro de estudio con notificación permanente y acciones de pausar, reanudar y detener/guardar (ya integrado en main mediante PR #14).
- Barajado equilibrado de respuestas desde el primer test (PR #14).
- Widget Android «Test rápido» con plantilla opcional por instancia (PR #15).
- Guardar y reanudar un test sin registrar resultados hasta finalizarlo (PR #16).
- Historial detallado de los cinco últimos exámenes finalizados y revisión de respuestas (PR #16).
- Gráficas de evolución por temas y grupos con hasta treinta sesiones resumidas (PR #16).

## Compatibilidad
- VersionName: 1.4; versionCode: 9.
- El identificador Android permanece `com.example.estudario`.
- Debe conservarse el certificado de firma permanente de NEXUM v1.3 (SHA-256: `2ba2c89de56e0fcfbc1a0b6df804800972d8bcd226b495b5d9628bcb4f2a2249`).
- La actualización migra el esquema de datos v2 a v3 sin eliminar preguntas, plantillas, objetivos ni estadísticas preexistentes.
- Las series históricas detalladas solo contienen datos de los nuevos exámenes; no se pueden reconstruir las respuestas de los exámenes antiguos a partir de las notas agregadas.

## Comprobaciones
- GitHub Actions: `testDebugUnitTest`, `assembleDebug`, build preview paralelo, `assembleRelease`, `lintDebug` y seguridad.
- El workflow oficial comprueba firma, paquete, versión y SHA-256 antes de publicar.
- Antes de actualizar se recomienda crear una copia completa desde Ajustes → Datos.
- Las pruebas en un teléfono real (widget/launcher, test suspendido, imágenes y backup/restore) son posteriores a los tests automáticos y no deben presentarse como realizadas.
