# NEXUM 1.8 — Widgets redimensionables y configuración completa

Actualización oficial de NEXUM 1.7, basada en los cambios de la PR #26.

## Mejoras

- **Hoy**: modo compacto con tema activo, minutos estudiados y barra de progreso; al ampliar, objetivos diarios con progreso y acceso al Plan. Los test vinculados desde objetivos siguen disponibles en tamaños que permiten mostrar acciones.
- **Test rápido**: elegir cuántas preguntas hacer (de 1 a 500), un tema concreto, todos los temas o una plantilla guardada. Los ajustes se guardan por instancia y se conserva la configuración anterior de widgets de v1.7.
- **Estudio/Pomodoro**: reducir el reloj al formato compacto, alternar entre cronómetro y Pomodoro mediante flechas y pulsar la esfera para abrir la pantalla Reloj. Se han eliminado los accesos redundantes de ambas esquinas.
- **Pomodoro**: carga la duración definida en Plan; no regresa a 25 minutos por abrir primero un widget. La cuenta atrás en pausa o detenida muestra los segundos exactos.
- **Android 12+**: formatos alternativos mediante `RemoteViews` dependientes del espacio concedido por el launcher, y mejor compatibilidad con tamaños ajustables en Android anteriores.

## Instalación

- `applicationId`: `com.example.estudario` (**sin cambios**).
- `versionCode`: **14**; `versionName`: **1.8**.
- APK oficial firmada mediante el workflow de GitHub; se verifica el certificado permanente para conservar instalación y datos.
- Antes de actualizar, exportar una copia global desde Ajustes. Instalar directamente encima de NEXUM 1.7, **sin desinstalar**.

## Nota sobre widgets existentes

Algunos launchers conservan el tamaño original y las restricciones anteriores de los widgets. Si tras actualizar no se pueden redimensionar, elimina **solo el widget del escritorio** y añádelo otra vez: no borres NEXUM ni sus datos. El tamaño mínimo exacto sigue dependiendo de las celdas del launcher.

## Verificación

Pruebas automáticas de la PR #26: tests unitarios, Debug APK, preview paralelo, compilación Release, Lint y security gate. La compilación firmada y su certificado se comprueban de nuevo antes de crear la Release pública. Siguen pendientes pruebas visuales y de uso sobre la tablet del usuario; compilar no certifica comportamiento en todos los launchers.
