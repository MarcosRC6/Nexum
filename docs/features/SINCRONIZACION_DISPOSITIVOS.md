# NEXUM — Transferir datos entre dispositivos (Host / Join)

## Objetivo
La transferencia es directa, inalámbrica y sin Internet mediante Nearby Connections. La opción aparece en **Ajustes → Datos → debajo de Exportar → Sincronizar dispositivos**. El código sustituye al antiguo diseño de carpeta compartida; no requiere nube, ni selección de carpeta ni archivos ZIP enviados a mano.

## Enviar / Crear conexión
1. En el dispositivo con la información que deseas conservar pulsa **ENVIAR · Crear conexión**. Quedará visible a los NEXUM cercanos.
2. En el otro móvil abre **RECIBIR · Unirse a conexión** y selecciona el emisor.
3. Ambos muestran un código de autenticación: **compáralo visualmente y acepta solo si coincide**. Si no coincide, cancela.
4. El emisor pulsa **Compartir copia completa**, lee el alcance de los datos y confirma el envío.
5. Se transfiere un ZIP interno de NEXUM; el receptor comprueba tamaño, SHA-256 y estructura completa antes de permitir la importación.

## Recibir / Sustituir
6. El receptor ve la confirmación: la copia del emisor reemplazará **todos** sus temas, imágenes, grupos, estadísticas, historial, plantillas, planificación, objetivos, test pendiente y ajustes. **No hay fusión**.
7. Si el cronómetro está activo deberá detenerse/guardarse antes de confirmar.
8. Antes de restaurar, el receptor crea y valida una copia local privada. Solo entonces restaura la copia entrante. Si falla, intenta restaurar el estado anterior.
9. Tras finalizar, reinicia NEXUM para refrescar todos los ajustes visuales. Se puede usar **Recuperar los datos anteriores** desde la misma pantalla.

## Seguridad y límites
- Nearby Connections usa Bluetooth/BLE/Wi-Fi, con cifrado de transporte. La seguridad frente a conexiones no autorizadas depende de **comparar y aceptar los códigos en ambos móviles**. No se establece conexión completa sin aceptación en ambos.
- No hay servidores propios, ni cuenta ni almacenamiento compartido externo. El ZIP temporal y las copias de recuperación quedan en el almacenamiento privado de la app.
- Ninguna recepción modifica los datos automáticamente. Si se cancela o corta antes de completar, no se ejecuta la restauración.
- Máximo **128 MiB comprimidos**, por el diseño actual del restaurador global, y 768 MiB descomprimidos. Las copias de mayor tamaño requieren una evolución hacia restauración en streaming.
- Se necesitan permisos inalámbricos para anunciar, descubrir y conectar. También servicios de Google Play compatibles en ambos Android.
- Esta PR no fusiona con main ni publica APK por sí sola. Requiere pruebas de Android reales en dos dispositivos, incluyendo fallo de transferencia, permiso denegado, respaldo y restauración.

## Tutorial
Al abrir NEXUM por primera vez, tras la pantalla inicial, se muestra una guía **omitable**. Explica Inicio, Biblioteca y Grupos, Tests, Planificación, Estadísticas y Ajustes/Datos. En el icono **?** de la esquina derecha del encabezado de Ajustes puede repetirse completa o por capítulos. Salir o finalizar la guía marca el recorrido como visto, pero nunca borra el acceso desde Ajustes.

## Exportaciones
Los nombres visibles se generan como `NEXUM_<APARTADO>_<COMPLETO/SIMPLE>[_PARCIAL]_<YYYYMMDD_HHMMSS>.<zip|json>`. Se mantienen los nombres internos y el formato de importación de Testario y Planario para retrocompatibilidad.
