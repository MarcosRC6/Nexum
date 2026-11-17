# NEXUM 1.6 — Biblioteca, importación, cronómetro y transferencia local

## Cambios
1. **Biblioteca:** gesto tirar hacia abajo en Temas y Grupos; actualiza desde el almacenamiento local sin borrar filtros ni importar de nuevo.
2. **Importación múltiple:** ZIP de Biblioteca estándar o conjunto de JSON individuales de temas; todos se interpretan y validan antes de aceptar. Los duplicados se consideran **dentro del tema de destino**; una pregunta compartida entre temas diferentes no hace desaparecer uno de ellos. Distintos textos de respuesta correcta no se tratan como duplicados idénticos. Resultado explícito de nuevos, fusionados, sin cambios, preguntas añadidas/repetidas.
3. **Cronómetro:** evita descartar RUNNING o PAUSED durante restauraciones destructivas; requiere finalizar y guardar antes de una restauración del Plan o NEXUM completo. Notificación Android con cronómetro del sistema; guardar tiempo desde notificación. Tras muerte de proceso, solo se recupera tiempo transcurrido con prueba conservadora de continuidad del mismo arranque del teléfono.
4. **Sincronización local entre dispositivos:** cualquiera puede prevalecer tras emparejar dos Android cercanos con código. Comparación orientativa de temas, preguntas, grupos, temas de Plan y objetivos. El origen y el receptor confirman el envío y la sustitución; se valida el ZIP por SHA-256 y se crea copia preventiva recuperable.
5. **Exportaciones:** se mantienen nombres `NEXUM_<APARTADO>_<TIPO>_<FECHA>.(zip|json)` de 1.5+ y la importación de formatos históricos.

## Compatibilidad
- `versionName=1.6`; `versionCode=12`; aplicación `com.example.estudario`.
- Huella de firma oficial requerida: `2ba2c89de56e0fcfbc1a0b6df804800972d8bcd226b495b5d9628bcb4f2a2249`.
- La actualización se instala encima de 1.5.1 sin desinstalar, manteniendo sus datos.

## Límites y seguridad
- No se crea nube, cuenta ni servidor. La transferencia Nearby requiere ambos teléfonos físicamente próximos y abiertos en la pantalla de sincronización; **no es continua ni fusiona cambios automáticamente**.
- La comparación de cantidades no es un diff completo del contenido.
- Guardar el tiempo antes de restaurar no evita que una copia global más antigua reemplace los tiempos anteriores: exportar copia de seguridad completa antes de la restauración.
- Los registros de tiempo actuales en el Plan siguen usando minutos enteros.
- La importación por tema ofrece diálogo para renombrar/fusionar y no constituye una transacción atómica de todo el lote; comprobar el balance tras cada importación.

## Validación
- GitHub Actions: testDebugUnitTest, assembleDebug, preview paralelo, compile Release, lint y puerta de seguridad.
- Producción: workflow exige firma permanente, paquete y versión exactos.
- Pruebas físicas de sincronización en **dos Android reales** todavía no realizadas. Probar emparejamiento, ambos sentidos, rechazo, guardado de recuperación, imágenes grandes y cronómetro activo/pausado.
- Antes de actualizar, crear una copia global desde Ajustes → Datos.
