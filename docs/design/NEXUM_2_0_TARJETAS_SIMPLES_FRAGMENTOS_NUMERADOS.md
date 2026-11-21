# NEXUM 2.0 — tarjetas simples con fragmentos permanentes y turnos numerados

**Acuerdo del usuario, 19/11/2026 (segunda pasada tras Beta 1).**

## Principio

Una sola tarjeta de autoría: pregunta (con posibles imágenes), respuesta y fragmentos seleccionados. Nada obliga a entender «Cloze», cinco tipos Anki ni sintaxis especial. La interfaz permite seleccionar una palabra o frase escrita y pulsar **«Ocultar siempre»** o **«Ocultar en turno Nº»**. Se ofrece sintaxis opcional para teclado o importación.

La tarjeta conserva el texto limpio y los tramos marcados por ID/posición en el modelo serializado; **la notación nunca se muestra al alumno**.

## Sintaxis opcional

| Texto escrito en editor | Comportamiento |
| --- | --- |
| `{{Ebro}}` | Ebro se oculta en **todas** las apariciones de esta tarjeta. |
| `{{1:Zaragoza}}` | Zaragoza solo se oculta en la aparición del **grupo 1**. |
| `{{2:Aragón}}` | Aragón solo se oculta en la aparición del **grupo 2**. |
| `{{1:Zaragoza}}` y `{{1:Huesca}}` | Ambos se ocultan **juntos** en turno 1. |
| `[[Ebro]]`, `[[2:Aragón]]` | Alternativa de escritura idéntica; la edición por selección no necesita símbolos. |
| `\{{literal}}` | Mostrar literalmente `{{literal}}`, sin convertirlo en un hueco. |

**Ejemplo completo:** `El río {{Ebro}} pasa por {{1:Zaragoza}} y por {{2:Aragón}}.`

- Primera vez: «El río ______ pasa por ______ y por Aragón».
- Segunda vez: «El río ______ pasa por Zaragoza y por ______».
- Tercera vez: vuelve al turno 1.
- Los huecos sin número siempre permanecen ocultos, haya o no grupos numerados.

Los números son **etiquetas de grupo**, no cantidades de respuestas ni niveles de dificultad: 1, 2, 3…; si hay 1 y 3 sin 2, el recorrido se ordena 1→3→1 sin crear rondas vacías. Un hueco con grupo N no aparece como respuesta durante los otros turnos: su palabra se muestra normalmente en el enunciado. Al pulsar **Mostrar respuesta**, se restituye el texto de la pregunta y debajo aparece la solución de los huecos **efectivamente preguntados**.

## Persistencia

- El campo aditivo `ClozeGap.groupNumber: Int?` conserva `null` para huecos antiguos sin numeración (ocultos siempre).
- `TestarioDatabase.ankiClozeCursors` guarda el siguiente turno por **ID de la nota/tarjeta**. Empieza en 1; se incrementa una sola vez al revelar, no al renderizar, y rota modularmente entre grupos existentes.
- Los repasos Anki de cloze usan el ID fuente y el número de grupo; no duplican un mismo ejercicio almacenado.
- El texto limpio en `FlashcardContent.front` y las posiciones de cada fragmento se exportan en la copia privada. Se preservan en restauración/migración; un editor anterior lee las entradas nuevas como datos desconocidos solo si sus reglas lo permiten. No ofrecer downgrade sin copia previa.
- Se mantienen formatos avanzados anteriores para poder **leerlos y editarlos** sin borrarlos, pero el editor nuevo muestra por defecto la tarjeta básica.
- **Estudio libre no modifica los plazos Anki**. Solo en modo de repaso programado elegido explícitamente pueden alterarse intervalos.

## Errores que deben probarse

Fragmento vacío, sintaxis sin cierre, dos marcadores anidados, texto con letras no ASCII, selección en teclado Android, caracteres escapados, cambio de tarjeta sin revelar, vuelta a pantalla tras cerrar app, múltiples grupos del mismo número, decenas de huecos sin número, edición de una tarjeta existente y migración desde beta 1.

## Estado del trabajo

Implementados en rama draft #29: parser estricto, notación alternativa, selección desde TextField, modelo con `groupNumber`, cursor persistente, render de huecos alternos y vista previa. **No es una nueva APK publicada**; se necesita CI y pruebas en Android real para dar por finalizada la UX, los procesos de importación y los exámenes mixtos.
