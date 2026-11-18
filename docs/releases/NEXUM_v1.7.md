# NEXUM 1.7 — Widgets, estudio y Pomodoro

## Mejoras para el usuario
- **Nuevo widget Estudio y Pomodoro (4×3)** con dos caras alternables mediante flechas izquierda/derecha. El tiempo de estudio es el mismo de Planario, con iniciar, pausar, reanudar y guardar. El Pomodoro es una cuenta atrás independiente y **no suma horas de estudio**.
- **Widget Hoy adaptable**: versión compacta con progreso y objetivos; versión ampliada con secciones para los objetivos y accesos. Si un objetivo diario tiene un test vinculado, el formato grande muestra «Iniciar test» y reutiliza los temas, cantidad de preguntas y plantilla configurados.
- **Widget Test rápido 2×1** horizontal con acceso directo a iniciar el test y engranaje para cambiar su plantilla. Mantiene la configuración independiente de cada instancia.
- **Selector de widgets más claro**: nombres, vistas previas diferenciadas y previsualización al configurar. El widget Calendario redundante se retira del selector; el calendario interno de Plan se conserva.
- **Interfaz**: notificación del tiempo de estudio más concisa y eliminación de contornos neón superfluos en flechas de Grupos y botones auxiliares de Biblioteca.
- **Fiabilidad**: persistencia conservadora del Pomodoro; coordinación con el servicio del cronómetro y receptor privado para las órdenes de los botones.

## Compatibilidad
- APK oficial: `applicationId=com.example.estudario`, `versionName=1.7`, `versionCode=13`.
- Clave de firma permanente, verificada en el workflow oficial mediante certificado SHA-256.
- Sin cambios destructivos de bancos, preguntas, planificación ni formatos existentes.
- Una actualización de NEXUM 1.6 se instala encima, sin desinstalar, mediante la firma habitual.

## Límites y comprobaciones
- Los widgets Android estándar no aceptan deslizamiento horizontal libre: el cambio entre Estudio/Pomodoro utiliza flechas.
- La compatibilidad visual con cada launcher requiere una comprobación en dispositivo real; CI sólo confirma compilación, pruebas automatizadas y controles de seguridad.
- El acceso a test desde «Hoy» se muestra en la versión amplia del widget si existe un objetivo vinculado.
- Antes de actualizar, exportar una copia de seguridad desde Ajustes.
- Las pruebas físicas de transferencia Nearby entre dos dispositivos siguen siendo una tarea independiente, no afectada por esta versión.
