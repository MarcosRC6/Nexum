# NEXUM — acuerdos de diseño para futuros modos de ejercicios (19/11/2026)

**Estado: DISEÑO APROBADO PARCIALMENTE. No implementar aún.** El usuario está terminando de revisar los demás apartados. La presente PR solamente corrige «Hoy» e incorpora enlaces web.

## Banco común de ejercicios, temas y grupos: decisiones adicionales (19/11/2026)
- **Todos los formatos viven en la Biblioteca existente**, bajo sus temas y grupos actuales. Un tema puede contener simultáneamente preguntas tipo test, tarjetas de memoria y ejercicios de respuesta múltiple escrita. Un grupo puede contener temas con cualquier combinación de tipos, sin crear estructuras paralelas o una quinta pestaña.
- Dentro de la vista de cada tema/grupo, mostrar y permitir añadir **todos los tipos de ejercicios**; usar un filtro desplegable «Tipo de ejercicio» con «Todos», «Test», «Tarjetas» y «Respuesta múltiple escrita», más futuros formatos. La cantidad de elementos por tipo y los contadores de la Biblioteca deben seguir siendo comprensibles; no llamar «preguntas tipo test» a todas las tarjetas o ejercicios.
- Al pulsar «Añadir» dentro de un tema, abrir desplegable «Tipo de ejercicio» y después el **editor específico** de ese formato. El ejercicio nuevo hereda por defecto el tema y grupo de origen; permitir asignar o cambiar tema/grupo de forma explícita, validando relaciones y evitando elementos huérfanos.
- Mantener las herramientas conocidas del banco —consultar, buscar, editar, mover, borrar, importar/exportar y detectar duplicados— con comportamiento adecuado al tipo, sin forzar las preguntas clásicas a nuevos formatos.
- Las **tarjetas Anki creadas manualmente o importadas** también forman parte del banco común. Desde él se pueden consultar, editar, asignar o cambiar tema/grupo y gestionar sus intervalos de repaso. No crear tarjetas nuevas automáticamente a partir de los errores.

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
- En el ejemplo del callejero, la **imagen de enunciado consiste en una única línea recta que representa la vía principal**, sin dibujar calles laterales, puntos de conexión, nombres ni otros trazos que revelen las respuestas. El alumnado debe recordar tanto las vías que conectan como su posición y lateral, cuando se le pida.

## Mecánica para responder
- Campo de entrada donde se escribe una respuesta. Botón visible **«Añadir»** (nunca mostrar «Añadir / Enter»).
- La tecla **Enter/Intro del teclado** ejecuta la misma acción que «Añadir». Respuestas ingresadas se muestran como etiquetas editables/eliminables para corregir erratas antes de entregar.
- Al finalizar, **«Comprobar»** (Entrenamiento) o **«Entregar»** (Examen). Indicar claramente coincidencias, fallos y respuestas faltantes. En Entrenamiento puede haber corrección inmediata configurable; Examen no revela soluciones antes de la entrega.
- Evitar falsos positivos por substring o distancia de Levenshtein: normalizar mayúsculas, tildes y prefijos frecuentes, reconocer alias explícitos, validar inequívocamente en el universo de respuestas del ejercicio, impedir que la misma respuesta cuente dos veces; ante ambigüedad no dar por correcta una respuesta distinta.
- El alcance geográfico del enunciado (calle completa, zona, o tramo) forma parte de los datos del ejercicio y de su criterio de corrección.

## Imagen de enunciado y corrección: decisión importante
- Para estos ejercicios **NO exigir dos imágenes independientes**. Se utiliza **UNA sola imagen base de enunciado** y durante la corrección se superponen etiquetas/indicadores sobre ESA misma imagen.
- Cada respuesta correcta puede tener un **número estable** (1, 2, 3...), con una o varias posiciones normalizadas (x,y) sobre la imagen. Durante la corrección se muestran esos números sobre los puntos/elementos correspondientes y se vinculan con la lista «1. Respuesta».
- En el caso de una vía lineal, la **corrección superpone** los marcadores de las conexiones a ambos lados de la línea, en la posición y **secuencia espacial** que el autor haya establecido. La corrección puede mostrar flechas/nombres junto a los números, pero ningún punto de conexión debe verse durante el enunciado.
- El autor puede indicar el **sentido de circulación o sentido de lectura/orientación** mediante una flecha opcional sobre la línea base, si considera que forma parte de la pregunta; no es obligatorio ni se infiere automáticamente. Si el autor no lo añade, tampoco se muestra. La corrección mantiene siempre la misma orientación que el enunciado.
- El editor debe permitir añadir, mover y borrar marcadores numerados de forma visual, vincularlos a respuestas y comprobar si su secuencia y lateral son coherentes; los archivos preparados por personas o herramientas externas, incluida IA si el usuario la utiliza para elaborar material, pueden proporcionar los mismos números/coordenadas en un formato documentado, verificable e independiente del tamaño de pantalla. **La app no generará preguntas ni tarjetas nuevas automáticamente al detectar errores.**
- Por defecto, la imagen inicial no revela las etiquetas de corrección ni el número de conexiones: se dibujan como una capa al corregir, sin modificar destructivamente el archivo base. Ofrecer otra política de pistas sólo si se acuerda.
- Esta decisión **no retira** el soporte de imagen de enunciado + imagen de explicación **independientes** ya existente en los tests clásicos.

## Tarjetas tipo Anki: repetición espaciada opcional y configurable (19/11/2026)
- **Se retira por petición expresa el proyecto «Tarjetas de repaso automáticas»: NEXUM NO creará automáticamente preguntas ni tarjetas a partir de respuestas falladas u omitidas.** Eliminar del diseño futuro el interruptor y los automatismos anteriores con ese nombre; no introducirlos de nuevo de forma implícita. Esto no impide crear tarjetas manualmente o importarlas como material preparado voluntariamente.
- Mantener separada y sin modificaciones la **cola habitual de preguntas falladas** ya existente: no dependerá de las tarjetas ni de la repetición espaciada.
- En el sistema de tarjetas **tipo Anki**, ofrecer la opción **«Repetición espaciada»**, activable o desactivable por el usuario, **desactivada por defecto**. No imponer calendario de repaso cuando esté desactivada: las tarjetas podrán practicarse libremente.
- Con la función activada, cada tarjeta conserva fecha de último repaso, próxima revisión, intervalo, historial de calificaciones y datos del algoritmo elegido. El usuario podrá configurar los **intervalos y límites de tiempo** (por ejemplo, pasos de aprendizaje, primer intervalo, máximo, intervalos tras fallo), con límites y valores por defecto prudentes y explicaciones claras.
- Al revelar la solución, ofrecer **botones de dificultad o valoración** inspirados en Anki, por ejemplo «Otra vez», «Difícil», «Bien» y «Fácil»; permitir configurar su presentación y los intervalos que producen, sin prometer que una etiqueta equivalga siempre a un tiempo fijo si se usa un algoritmo adaptativo. Mostrar al lado la previsión del próximo repaso cuando proceda.
- El usuario puede repasar voluntariamente antes del vencimiento, editar o restablecer el calendario de una tarjeta y pausar/desactivar la repetición espaciada sin perder las tarjetas, soluciones, temas/grupos ni registros históricos. Distinguir «tarjetas pendientes» de «todas las tarjetas» para que no quede inaccesible el material.
- Las tarjetas se gestionan en los temas/grupos de la Biblioteca y se pueden mover, buscar, importar o exportar con datos de contenido. Definir por separado si un backup personal incluye el historial completo de programación (sí) y si un paquete compartido con otra persona incluye su calendario personal (no).
- **Evitar efectos colaterales:** un Examen con tarjetas no debe revelar ni puntuar anticipadamente la solución; la programación de tarjetas por repetición espaciada sólo se actualiza en revisiones válidas según reglas acordadas, sin duplicar eventos por una rotación de pantalla, reinicio o doble pulsación.
- **Estado:** diseño futuro, aún NO implementado en NEXUM ni parte funcional de esta PR.

## Seguridad y consistencia antes de programar
- Diseñar esquema versionado/migraciones de nuevos ejercicios y respaldos, exportación/importación y deduplicación semántica por tipo de ejercicio; no alterar el esquema actual por improvisación.
- Validar alias vacíos/repetidos/ambiguos, puntuación con respuestas múltiples, cancelaciones, guardar progreso, corrección en Entrenamiento vs Examen, accesibilidad de teclado Enter, tamaños de pantalla, imágenes perdidas/desplazadas, marcadores fuera del 0..1, backups antiguos y restauraciones.
- Las listas de calles y conexiones concretas requieren fuente cartográfica validada. Nunca inventar que una vía conecta con otra por la similitud del nombre ni convertir este ejemplo en banco real sin documentación.

## Estado de esta PR
En esta PR no se ha implementado motor de ejercicios, tipos, editores, puntajes ni interfaces de mapas. Se guarda sólo el presente acuerdo de diseño para retomarlo cuando el usuario termine la inspección.
