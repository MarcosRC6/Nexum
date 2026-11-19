# NEXUM — acuerdos de diseño para futuros modos de ejercicios (19/11/2026)

**Estado: DISEÑO APROBADO PARCIALMENTE. No implementar aún.** El usuario está terminando de revisar los demás apartados. La presente PR solamente corrige «Hoy» e incorpora enlaces web.

## Acceso a los distintos tipos de ejercicio
- En la pantalla donde se inicia una sesión de estudio, **selector desplegable** de modalidad/formato: Test clásico, Tarjetas de memoria, Respuesta múltiple escrita y futuras variantes. No abrir una quinta pestaña inferior.
- Al añadir una pregunta a un tema de Biblioteca, **desplegable «Tipo de ejercicio»**. Cada tipo de ejercicio tiene un editor distinto que muestra sólo los campos adecuados a ese formato.
- Mantener temas/grupos organizativos, estadísticas y entrenamientos/exámenes. Evitar mezclas de puntuaciones engañosas entre formatos. No forzar preguntas antiguas a convertirse al nuevo tipo.

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

## Tarjetas de repaso derivadas de errores: configuración voluntaria (19/11/2026)
- **Opción desactivada por defecto:** «Crear tarjetas de repaso a partir de los errores». No generar tarjetas si el usuario no habilita expresamente esta función. El ejercicio y sus estadísticas deben funcionar igualmente sin ella.
- Esta opción es **independiente** de la configuración existente para **añadir preguntas falladas a la cola de repasos**. Son dos controles distintos: el usuario puede activar ambos, uno solo o ninguno.
- Al completar y corregir un ejercicio de «Respuesta múltiple escrita», si la opción está habilitada, el sistema **propone** tarjetas sobre las respuestas omitidas o confundidas; el usuario puede revisar, aceptar o descartar la propuesta antes de incorporarla a su biblioteca. No crear ni insertar material de estudio irrevocablemente por un fallo aislado.
- **Entrenamiento:** proponer al finalizar la corrección. **Examen:** esperar a que se entregue y se abra la revisión; no revelar soluciones ni pistas antes de ese momento.
- Cada tarjeta debe ser **autónoma y contextual**: el anverso identifica claramente el ejercicio de origen (vía principal, zona/tramo o tema), de modo que no se convierta en una pregunta aislada o ambigua; el reverso contiene la solución verificada y podrá reutilizar la imagen base y los marcadores cuando proceda.
- Controlar duplicados por referencia al ejercicio de origen + respuesta identificada; no crear infinitas tarjetas al fallar repetidamente el mismo dato. Las repeticiones pueden actualizar la prioridad/frecuencia de repaso sin sobrescribir estadísticas existentes.
- La fuente de las tarjetas debe ser la **lista de respuestas validada** del ejercicio: nunca inventar respuestas ni dar por cierto un alias ambiguo. Permitir borrar o desactivar tarjetas generadas sin borrar el ejercicio original ni su historial.
- En un ejercicio con, por ejemplo, doce respuestas correctas y tres olvidadas, mostrar «Se han detectado tres posibles tarjetas» sólo si la opción está habilitada; no añadirlas automáticamente sin revisión.
- Este apartado es una **decisión de diseño**, no una función programada en esta PR.

## Seguridad y consistencia antes de programar
- Diseñar esquema versionado/migraciones de nuevos ejercicios y respaldos, exportación/importación y deduplicación semántica por tipo de ejercicio; no alterar el esquema actual por improvisación.
- Validar alias vacíos/repetidos/ambiguos, puntuación con respuestas múltiples, cancelaciones, guardar progreso, corrección en Entrenamiento vs Examen, accesibilidad de teclado Enter, tamaños de pantalla, imágenes perdidas/desplazadas, marcadores fuera del 0..1, backups antiguos y restauraciones.
- Las listas de calles y conexiones concretas requieren fuente cartográfica validada. Nunca inventar que una vía conecta con otra por la similitud del nombre ni convertir este ejemplo en banco real sin documentación.

## Estado de esta PR
En esta PR no se ha implementado motor de ejercicios, tipos, editores, puntajes ni interfaces de mapas. Se guarda sólo el presente acuerdo de diseño para retomarlo cuando el usuario termine la inspección.
