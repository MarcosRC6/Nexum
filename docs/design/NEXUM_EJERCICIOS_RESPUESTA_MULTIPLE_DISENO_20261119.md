# NEXUM — acuerdos de diseño para futuros modos de ejercicios (19/11/2026)

**Estado: DISEÑO APROBADO PARCIALMENTE. No implementar aún.** El usuario está terminando de revisar los demás apartados. La presente PR solamente corrige «Hoy» e incorpora enlaces web.

## Banco común de ejercicios, temas y grupos: decisiones adicionales (19/11/2026)
- **Todos los formatos viven en la Biblioteca existente**, bajo sus temas y grupos actuales. Un tema puede contener simultáneamente preguntas tipo test, tarjetas de memoria y ejercicios de respuesta múltiple escrita. Un grupo puede contener temas con cualquier combinación de tipos, sin crear estructuras paralelas o una quinta pestaña.
- Dentro de la vista de cada tema/grupo, mostrar y permitir añadir **todos los tipos de ejercicios**; usar un filtro desplegable «Tipo de ejercicio» con «Todos», «Test», «Tarjetas» y «Respuesta múltiple escrita», más futuros formatos. La cantidad de elementos por tipo y los contadores de la Biblioteca deben seguir siendo comprensibles; no llamar «preguntas tipo test» a todas las tarjetas o ejercicios.
- Al pulsar «Añadir» dentro de un tema, abrir desplegable «Tipo de ejercicio» y después el **editor específico** de ese formato. El ejercicio nuevo hereda por defecto el tema y grupo de origen; permitir asignar o cambiar tema/grupo de forma explícita, validando relaciones y evitando elementos huérfanos.
- Mantener las herramientas conocidas del banco —consultar, buscar, editar, mover, borrar, importar/exportar y detectar duplicados— con comportamiento adecuado al tipo, sin forzar las preguntas clásicas a nuevos formatos.
- Las **tarjetas de repaso automáticas** también deben aparecer en el banco común como tarjetas ordinarias identificables por su origen automático. Desde allí se pueden inspeccionar y gestionar, y **asignar o cambiar tema/grupo**. No esconderlas en una cola inaccesible.

## Selección desplegable al iniciar Entrenamiento o Examen
- En la configuración de una sesión, tras elegir uno o varios temas/grupos, presentar desplegable **«Tipo de ejercicios»**: «Solo test», «Solo tarjetas», «Solo respuesta múltiple escrita» y «Mixto» (todas las modalidades disponibles). Posibilidad futura de selección personalizada de más de un tipo, sin imponerla en esta primera versión.
- Cada modo conserva **su interfaz propia**: en Test, elegir una respuesta; en Tarjetas, recordar/revelar; en Respuesta múltiple, introducir lista de respuestas y comprobar. El motor de sesión puede encadenar distintos formatos sin alterar el editor, la corrección ni los datos de cada uno.
- El conjunto candidato surge exclusivamente de los temas/grupos seleccionados. Mostrar previamente cuántos ejercicios hay de cada tipo. Si un tipo no tiene ejercicios, deshabilitarlo o avisar claramente; nunca iniciar silenciosamente un tipo diferente.
- «Mixto» selecciona únicamente tipos que realmente existan en los temas elegidos, con distribución previsible y sin duplicar ítems; el total de ejercicios y la distribución por tipo deben ser visibles antes de iniciar. Las modalidades de Entrenamiento y Examen mantienen sus respectivas políticas de revelación y revisión.
- Estadísticas y puntuaciones se registran por ejercicio y tipo; al calcular un resultado global usar una normalización documentada para que un ítem con quince respuestas no distorsione silenciosamente la nota frente a una pregunta test.
- **Compatibilidad:** las preguntas actuales conservan exactamente su tipo test y sus sesiones antiguas siguen siendo legibles. El esquema, backups e importaciones futuros se versionan y migran con pruebas de no pérdida de datos.

## Nombre y usos
- Nombre **general** del nuevo formato: **«Respuesta múltiple escrita»**. «Conexiones de calles» era únicamente un ejemplo, NO el nombre del formato: también sirve para enumerar herramientas, funciones, componentes, protocolos, estaciones, elementos geográficos, etc.
- El editor definirá el enunciado, imagen base opcional, lista de respuestas esperadas, alias aceptados y reglas de corrección. No depender de datos del mapa si el ejercicio no es geográfico.

## Mecánica para responder
- Campo de entrada donde se escribe una respuesta. Botón visible **«Añadir»** (nunca mostrar «Añadir / Enter»).
- La tecla **Enter/Intro del teclado** ejecuta la misma acción que «Añadir». Respuestas ingresadas se muestran como etiquetas editables/eliminables para corregir erratas antes de entregar.
- Al finalizar, **«Comprobar»** (Entrenamiento) o **«Entregar»** (Examen). Indicar claramente coincidencias, fallos y respuestas faltantes. En Entrenamiento puede haber corrección inmediata configurable; Examen no revela soluciones antes de la entrega.
- Evitar falsos positivos por substring o distancia de Levenshtein: normalizar mayúsculas, tildes y prefijos frecuentes, reconocer alias explícitos, validar inequívocamente en el universo de respuestas del ejercicio, impedir que la misma respuesta cuente dos veces; ante ambigüedad no dar por correcta una respuesta distinta.
- El alcance geográfico del enunciado (calle completa, zona, o tramo) forma parte de los datos del ejercicio y de su criterio de corrección.

## Imagen de enunciado y corrección: decisión importante
- Para estos ejercicios **NO exigir dos imágenes independientes**. Se utiliza **UNA sola imagen base de enunciado** y durante la corrección se superponen etiquetas/indicadores sobre ESA misma imagen.
- Cada respuesta correcta puede tener un **número estable** (1, 2, 3...), con una o varias posiciones normalizadas (x,y) sobre la imagen. Durante la corrección se muestran esos números sobre los puntos/elementos correspondientes y se vinculan con la lista «1. Respuesta».
- El editor debe permitir añadir, mover y borrar marcadores numerados de forma visual; para importaciones automáticas/IA se deben admitir esos mismos números y coordenadas en un formato documentado, verificable e independiente del tamaño de pantalla.
- Por defecto, la imagen inicial no revela las etiquetas de corrección ni el número de conexiones: se dibujan como una capa al corregir, sin modificar destructivamente el archivo base. Ofrecer otra política de pistas sólo si se acuerda.
- Esta decisión **no retira** el soporte de imagen de enunciado + imagen de explicación **independientes** ya existente en los tests clásicos.

## Tarjetas de repaso automáticas: ajuste opcional independiente (19/11/2026)
- **Nombre exacto del ajuste en NEXUM:** «Tarjetas de repaso automáticas» (con tilde). Debajo se mostrará una descripción breve, por ejemplo: «Genera tarjetas para repasar los conceptos que olvides o confundas en ejercicios escritos».
- **Interruptor desactivado de forma predeterminada** y enteramente independiente de la opción ya existente de incorporar preguntas falladas a la cola de repasos. El usuario puede activar ninguna, una o ambas.
- Cuando el usuario lo activa, las tarjetas derivadas de respuestas erróneas u omitidas **se generan automáticamente tras una corrección válida**. No exigir una confirmación manual obligatoria para cada tarjeta, pues contradice el nombre y el comportamiento automático. La revisión/gestión manual sí debe estar disponible: ver las tarjetas en el banco, editar, descartar, mover, clasificar o borrar. Si más adelante se ofrece «Revisar antes de incorporar», será una preferencia **adicional y opcional**, no un requisito.
- **Ubicación por defecto:** heredar el tema y el grupo del ejercicio original siempre que sean válidos. Facilitar en el banco común asignar **otro tema y/o grupo** a tarjetas automáticas o creadas manualmente, con selección por desplegable y validación para no dejar relaciones incoherentes. Incluir búsqueda y filtro «Origen: automático/manual» para localizarlas.
- Al terminar un ejercicio, la corrección puede mostrar una confirmación discreta como «3 tarjetas de repaso creadas · Ver tarjetas», sin interferir con las soluciones o la nota. También debe ser posible gestionar el lote de tarjetas desde el banco.
- **Entrenamiento:** crear tras comprobar/corregir definitivamente. **Examen:** crear sólo después de entregar y finalizar la corrección, nunca mientras las soluciones están ocultas.
- Las tarjetas serán autónomas y contextualizadas (enunciado, zona/tramo y ejercicio origen), con respuesta verificada e imagen base/marcadores si aportan información. No generar contenidos inventados, ni convertir una respuesta ambigua en un hecho seguro.
- **Deduplicación estable:** misma respuesta validada del mismo ejercicio no genera otra tarjeta por cada fallo. Fallar repetidamente puede afectar a su prioridad de repaso, sin borrar historial ni duplicar ítems. Borrar una tarjeta automática no borra el ejercicio original.
- Desactivar el interruptor detiene la generación futura; **no elimina automáticamente las tarjetas ya generadas**.
- Este apartado está aprobado como **especificación funcional futura**, no implementado en esta PR.

## Seguridad y consistencia antes de programar
- Diseñar esquema versionado/migraciones de nuevos ejercicios y respaldos, exportación/importación y deduplicación semántica por tipo de ejercicio; no alterar el esquema actual por improvisación.
- Validar alias vacíos/repetidos/ambiguos, puntuación con respuestas múltiples, cancelaciones, guardar progreso, corrección en Entrenamiento vs Examen, accesibilidad de teclado Enter, tamaños de pantalla, imágenes perdidas/desplazadas, marcadores fuera del 0..1, backups antiguos y restauraciones.
- Las listas de calles y conexiones concretas requieren fuente cartográfica validada. Nunca inventar que una vía conecta con otra por la similitud del nombre ni convertir este ejemplo en banco real sin documentación.

## Estado de esta PR
En esta PR no se ha implementado motor de ejercicios, tipos, editores, puntajes ni interfaces de mapas. Se guarda sólo el presente acuerdo de diseño para retomarlo cuando el usuario termine la inspección.
