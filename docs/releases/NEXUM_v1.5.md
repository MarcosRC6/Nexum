# NEXUM 1.5 — Publicación oficial

## Novedades

- **Sincronización inalámbrica entre dos Android**: Ajustes → Datos → Sincronizar dispositivos, debajo de Exportar. El emisor (Enviar / Host) anuncia una conexión y el receptor (Recibir / Join) busca dispositivos próximos, verifica el código en ambos y recibe una copia completa.
- **Seguridad de datos**: la recepción comprueba tamaño, SHA-256 y formato ZIP. No modifica el dispositivo receptor hasta confirmar que se sustituirá todo, y guarda una copia de recuperación privada antes de restaurar. El emisor conserva su información. No hay fusión de cambios, sincronización continua ni nube.
- **Tutorial inicial**: guía omisible de siete capítulos, desde bienvenida e Inicio hasta Biblioteca, Tests, Plan, Estadísticas y Datos. El botón ? de Ajustes permite repetir todo o únicamente apartados.
- **Nombres de exportación**: prefijo NEXUM, sección, tipo/alcance y fecha. Los archivos antiguos de Testario/Planario conservan compatibilidad.

## Compatibilidad

- `versionName=1.5`, `versionCode=10`.
- Mismo identificador Android `com.example.estudario` y certificado de firma oficial de NEXUM v1.4; la Release debe ser rechazada por CI si la huella de firma difiere.
- APK firmada apta para instalarse sobre la 1.4 sin desinstalarla. **Recomendación: exportar una copia completa antes de actualizar**.

## Pruebas y límites

- El proyecto tiene CI para unit tests, APK Debug, variante Preview, Release compile, Android Lint y seguridad.
- **Importante:** Nearby requiere dos Android reales cercanos con Google Play Services compatibles. La prueba de descubrimiento, emparejamiento, envío de imágenes, transferencia interrumpida y restauración de seguridad **no ha sido realizada todavía**.
- Límite inicial de la copia completa: 128 MiB comprimidos.
- La nueva sincronización usa permisos Bluetooth/Wi‑Fi/dispositivos cercanos, solicitados durante su uso, no al iniciar la app.
- No confundir validación de compilación con la prueba funcional en dos dispositivos. Este lanzamiento se hace para que el usuario pueda probarlo.
