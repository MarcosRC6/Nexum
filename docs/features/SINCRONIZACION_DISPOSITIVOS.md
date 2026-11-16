# Sincronización entre dispositivos — NEXUM

## Funcionamiento

La primera versión utiliza el **selector de carpeta de Android** (Storage Access Framework). No utiliza una cuenta propia de NEXUM, ni requiere subir datos a servidores de NEXUM. Los dispositivos deben poder **leer y escribir la misma carpeta**, por ejemplo mediante un proveedor de nube que exponga su carpeta en el selector de archivos de Android.

1. En ambos móviles, entra en **Ajustes → Datos → Sincronización entre dispositivos**.
2. Asigna un nombre reconocible a cada móvil, por ejemplo `Móvil principal` y `Tablet`.
3. Pulsa **Seleccionar carpeta compartida** y elige **exactamente la misma carpeta** en ambos. No sirve escoger dos carpetas locales distintas.
4. En el dispositivo que tenga la información que deseas conservar, pulsa **Este dispositivo prevalece · Enviar**, lee el aviso y confirma.
5. En el otro dispositivo, pulsa **Buscar copias**, selecciona la copia correspondiente al móvil de origen y pulsa **Usar el NEXUM seleccionado · Recibir**.
6. Verifica el nombre, identificación y fecha de origen. Confirma solo si quieres **reemplazar todos los datos del dispositivo receptor**.
7. Cierra y vuelve a abrir NEXUM después de la recepción.

## Qué incluye

- Biblioteca, grupos, temas, respuestas y estadísticas
- Historial de exámenes, evolución y pruebas guardadas
- Imágenes propias de las preguntas presentes en el almacenamiento de NEXUM
- Planificación, calendario, objetivos, sesiones registradas y ajustes
- Vínculos de test rápido

El formato interno sigue siendo el de **copia global NEXUM v1**; las antiguas copias Testario/Planario siguen importándose por la vía habitual. El manifiesto añade una identificación de origen para sincronización.

## Resolución de conflictos

No hay fusión automática de cambios: el usuario **elige qué copia prevalece**. Las subidas nunca sobrescriben directamente los datos locales de otro dispositivo y conservan hasta tres instantáneas de cada origen. Antes de recibir, NEXUM genera y verifica una copia privada de recuperación. Para deshacer la última recepción, usa **Restaurar la última copia de recuperación**.

Los envíos y recepciones son **manuales y confirmados**. No es sincronización instantánea o bidireccional en segundo plano. Las carpetas deben estar ya sincronizadas por su proveedor, cuya velocidad o disponibilidad NEXUM no controla.

## Límites y privacidad

- **128 MiB comprimidos** como máximo para esta implementación: el proceso anterior de restauración aún consume los datos desde un `ByteArray`, y ese límite reduce el riesgo de quedarse sin memoria.
- Los ZIP no se cifran adicionalmente por NEXUM. Utiliza una carpeta privada, con un proveedor de nube de confianza.
- Un proveedor Android que no permita seleccionar carpetas o escribir archivos no podrá usarse. No implica pérdida de datos locales.
- Las pruebas automáticas no sustituyen una prueba real entre dos dispositivos Android, especialmente con archivos grandes, fotos, permisos caducados y desconexiones.
- La versión 1.4 publicada permanece intacta; el código vive en una PR independiente pendiente de autorización.
