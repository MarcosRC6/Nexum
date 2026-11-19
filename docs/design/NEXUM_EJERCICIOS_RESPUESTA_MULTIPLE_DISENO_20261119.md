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

## Acuerdo aprobado: Entrenamiento mixto, Examen y Repaso Anki (19/11/2026)

- **Entrenamiento mixto:** se pueden combinar todos los formatos. Mostrar solución de tarjetas debajo del enunciado tras revelarla. La autoevaluación no modifica repetición espaciada **por defecto**; solo si el usuario lo activa explícitamente y el intento cuenta como repaso programado válido.
- **Examen mixto:** solo los ejercicios de **corrección objetiva** integran la nota numérica: test, respuesta múltiple escrita y tarjetas de huecos/respuesta escrita cuando su formato verifica una solución inequívoca. Los demás se reflejan aparte como **«Repaso manual»**, con recuento de vistas/recordadas, sin alterar nota. Si no hay puntuables objetivos, mostrar «Examen sin nota objetiva». No mostrar soluciones antes de revisión ni actualizar intervalos Anki durante exámenes.
- **Repaso Anki independiente:** accesible dentro de las pantallas existentes, sin añadir pestaña inferior. Estudio libre sin calendario; repaso programado únicamente cuando se active «Repetición espaciada». Mantener estadísticas de autoevaluación y eventos de programación separadas de puntuaciones objetivas.
- **Plantillas y vista previa:** guardar tipos elegidos en cada modalidad, mostrar recuento de objetivas vs autoevaluadas y evitar duplicados de notas bidireccionales, tarjetas derivadas o temas asociados a varios grupos.
- Detalles y escenarios en [NEXUM_TARJETAS_ANKI_REPASO_20261119.md](NEXUM_TARJETAS_ANKI_REPASO_20261119.md). **Aprobado conceptualmente; no implementado**.

## Nombre y usos
- Nombre **general** del nuevo formato: **«Respuesta múltiple escrita»**. «Conexiones de calles» era únicamente un ejemplo, NO el nombre del formato: también sirve para enumerar herramientas, funciones, componentes, protocolos, estaciones, elementos geográficos, etc.
- El editor definirá el enunciado, imagen base opcional, lista de respuestas esperadas, alias aceptados y reglas de corrección. No depender de datos del mapa si el ejercicio no es geográfico.
- En el ejemplo del callejero, la **imagen de enunciado consiste en una única línea recta que representa la vía principal**, sin dibujar calles laterales, puntos de conexión, nombres ni otros trazos que revelen las respuestas. El alumnado debe recordar tanto las vías que conectan como su posición y lateral, cuando se le pida.

## Editor de respuesta múltiple escrita: diseño validado por el usuario (19/11/2026)
- **Editor integrado en Biblioteca**, al añadir/editar un ejercicio mediante desplegable «Tipo de ejercicio». Interfaz propia de respuesta múltiple escrita, distribuida en cuatro apartados dentro de una misma pantalla: **(1) Enunciado, (2) Respuestas correctas, (3) Solución visual, (4) Vista previa**. Mantener asignación y cambio de tema/grupo.
- **Enunciado:** texto editable y **«Importar imagen»** desde el dispositivo, opcional. Como alternativa específica, **«Crear esquema»** dentro de NEXUM para dibujar una línea recta sencilla sin pistas, determinar orientación opcional y construir la imagen base sin necesidad de aplicaciones externas. El creador controla qué pistas visibles incorpora, nunca se dibujan conexiones automáticamente.
- **Respuestas:** cada solución tiene identificador persistente, nombre, alias explícitos opcionales y orden editable. Botón para añadir, editar, reordenar o retirar respuestas. Su numeración se corresponde con los marcadores de solución. Si se modifica un nombre o alias, no se pierde la vinculación mediante ID a los marcadores.
- **Solución visual:** usar la **misma imagen del enunciado** y una capa de puntos numerados invisibles al alumno hasta corregir. Permitir colocar, mover, borrar y vincular puntos con respuestas, seleccionar su lado, validar el orden espacial y disponer de varios puntos para una misma respuesta cuando corresponda. Flecha/sentido opcional definido por el creador; nunca inferir posiciones de calles reales sin datos. En ejercicios no geográficos, marcadores opcionales, sin imponer esquema de calles.
- **Vista previa:** alternar «Como alumno» y «Corrección» antes de guardar, comprobar emparejamiento de números/soluciones y detectar pistas involuntarias. Posibilidad de probar el corrector sin registrar estadísticas personales ni crear intentos reales.
- **Guardar borrador** recuperable frente a cierres inesperados, navegación o interrupción. Diferenciar «Guardar borrador» de «Guardar/publicar ejercicio válido»; no publicar si falta pregunta, lista de respuestas no vacía, si hay IDs/alias ambiguos o marcadores huérfanos. Confirmar borrado destructivo de imagen o respuestas con marcadores asociados.
- **Persistencia y portabilidad:** guardar imagen original y capa de marcadores por separado; emplear coordenadas relativas independientes de resolución, soportar redimensionado/rotación, verificar integridad de archivos; los JSON/ZIP versionados incluirán respuestas, alias, imágenes, IDs y coordenadas sin filtrar soluciones durante la presentación.
- Este diseño está **aceptado conceptualmente** por el usuario; no supone autorización para modificar el motor ni publicar una versión de NEXUM hasta finalizar las decisiones restantes.

## Mecánica para responder
- Campo de entrada donde se escribe una respuesta. Botón visible **«Añadir»** (nunca mostrar «Añadir / Enter»).
- La tecla **Enter/Intro del teclado** ejecuta la misma acción que «Añadir». Respuestas ingresadas se muestran como etiquetas editables/eliminables para corregir erratas antes de entregar.
- Al finalizar, **«Comprobar»** (Entrenamiento) o **«Entregar»** (Examen). Indicar claramente coincidencias, fallos y respuestas faltantes. En Entrenamiento puede haber corrección inmediata configurable; Examen no revela soluciones antes de la entrega.
- Evitar falsos positivos por substring o distancia de Levenshtein: normalizar mayúsculas, tildes y prefijos frecuentes, reconocer alias explícitos, validar inequívocamente en el universo de respuestas del ejercicio, impedir que la misma respuesta cuente dos veces; ante ambigüedad no dar por correcta una respuesta distinta.
- El alcance geográfico del enunciado (calle completa, zona, o tramo) forma parte de los datos del ejercicio y de su criterio de corrección.

## Puntuación de respuesta múltiple escrita: reglas y casos límite (19/11/2026)

**Estado de esta sección:** se ha elegido el **modelo de aciertos parciales con penalización por errores añadidos**. **Decisión del usuario:** la puntuación debe ser **editable igual que en los tests existentes de NEXUM**, no fijarse a −0,5. Se reutilizará el concepto de «Puntos por acierto» y «Penalización por fallo» que ya usa la configuración de Test y sus plantillas (valores base actuales: **+1** y **−0,25**). El valor −0,5 que se empleó en la simulación anterior sigue siendo un ejemplo de configuración posible, **no** un valor obligatorio. Todavía no se implementa.

### Unidades contables
- `N`: cantidad de soluciones canónicas distintas definidas y válidas en el ejercicio, mínimo 1. Cada solución tiene un ID estable, nombre, alias admitidos y, opcionalmente, una o varias posiciones de marcador. Varios marcadores de la misma solución no suman puntos múltiples por defecto.
- `C`: cantidad de soluciones canónicas distintas acertadas por el usuario después de una correspondencia uno-a-uno. Cada una suma los **puntos por acierto configurados `a`**, una sola vez, aunque se introduzcan el nombre completo y un alias.
- `E`: cantidad de **entradas finales inválidas distintas** (no vacías), después de eliminar equivalencias de normalización y duplicados. Cada entrada incorrecta suma la **penalización configurada `b`**, que es 0 o un número negativo. Los valores base de NEXUM son `a = 1`, `b = −0,25`; puede configurarse, por ejemplo, `b = −0,5` o `b = 0`. Dos nombres realmente diferentes cuentan como dos intentos incorrectos, salvo que resulten ser alias equivalentes verificables.
- `O = N − C`: soluciones omitidas, registradas en estadísticas, sin resta adicional; su ausencia ya limita la nota por no sumar.
- **Fórmula por ejercicio:** `puntuaciónBruta = C*a + E*b`; `notaPorcentaje = clamp(100 * puntuaciónBruta / (N*a), 0, 100)` con `N >= 1`, `a > 0`, `b <= 0`. Mostrar nota sobre 10 usando el mismo método que Test y conservar internamente los decimales sin redondear. `conocimientoRecordado = 100 * C / N` permanece como estadística separada.
- **Ubicación de la configuración:** en el apartado **«Baremo»** de la configuración de Entrenamiento/Examen, con los mismos controles editables y validaciones que los tests clásicos: «Puntos por acierto» (>0) y «Penalización por fallo» (<=0, permite 0). Para sesiones mixtas, mostrar la rúbrica que se aplicará a cada formato, usar el mismo valor por defecto en tests y respuestas escritas y permitir configuración independiente por tipo sólo si es explícita y visible, sin cambios encubiertos. Guardar los valores en las plantillas y en el snapshot de cada intento (`scoringRuleVersion`, `pointsCorrect`, `penaltyWrong`), para que modificar ajustes después no recalifique silenciosamente el historial.

### Tabla de expectativas para 12 soluciones, a = +1 y b = −0,5 (ejemplo configurable)
| C | E | O | Nota | Interpretación |
|---:|---:|---:|---:|---|
| 12 | 0 | 0 | 100 % | Perfecto |
| 9 | 0 | 3 | 75 % | Tres omitidas, sin errores añadidos |
| 9 | 2 | 3 | 66,67 % | Dos errores adicionales penalizados |
| 0 | 0 | 12 | 0 % | En blanco |
| 0 | 6 | 12 | 0 % | Nunca nota negativa |
| 12 | 2 | 0 | 91,67 % | Recuerda todas, pero añadió dos incorrectas |
| 9 | 2 | 3 | 66,67 % | Mismo resultado en móvil o tablet |

### Política de correspondencia y deduplicación
- Resolver sobre **IDs canónicos** de soluciones, no sobre texto literal. Normalizar mayúsculas/minúsculas, diacríticos, espacios repetidos, puntuación y abreviaturas/prefijos aprobados por el autor. Ejemplos: «Calle de Thomas Edison», «Thomas Edison» y «Edison» pueden designar la misma solución **si el alias es inequívoco** en ese ejercicio.
- Alias idéntico que corresponde a varias soluciones distintas: **ambigüedad**. No asignar una solución al azar ni acreditarla; antes de entregar, permitir ampliar el nombre sin mostrar la solución completa. En Examen no mostrar cuál es el nombre correcto ni si el valor existe en la lista.
- La distancia ortográfica o substring no basta para validar («Thomas Edisno» no debe aceptarse automáticamente por similitud si puede confundirse con otra vía). Se pueden prever sugerencias durante la revisión tras entregar o un mecanismo documentado para reclamar/revisar, sin filtración de respuestas durante Examen.
- Un nombre correcto y su alias introducidos dos veces: **un acierto, cero penalización por duplicado**; informar «Ya añadido» y no crear dos chips. Repeticiones idénticas de un valor erróneo tampoco deben multiplicar la penalización; la entrada vacía, espacios y pulsaciones repetidas no se añaden ni penalizan.
- Detectar errores tipográficos al validar en final; antes de «Comprobar»/«Entregar», el alumno puede editar/eliminar entradas libremente sin ninguna penalización: **sólo cuenta el conjunto final entregado**.
- Si hay dos cruces distintos con la misma calle, por defecto **una solución canónica / dos marcadores visuales**. Si el autor evalúa intencionadamente dos puntos de forma independiente, debe declarar dos ítems distintos y desambiguarlos expresamente; nunca duplicar soluciones sólo porque la línea tiene dos intersecciones.
- La orientación, el lado y el orden espacial son **datos de la solución visual**, no generan errores adicionales si no han sido expresamente exigidos como respuestas. Si el autor quiere evaluar posiciones/sentidos, definirlo como requisito visible y una rúbrica separada antes de implementar puntuación espacial.

### Entrenamiento, Examen y estado de sesión
- En **Entrenamiento**, «Añadir» o Enter registran una etiqueta editable; «Comprobar» congela la entrega, calcula el resultado y muestra aciertos, omisiones y errores junto a marcadores numerados de la imagen original. La corrección inmediata por respuesta, si existe como ajuste, debe ser opcional y consciente de que aporta pistas.
- En **Examen**, «Añadir» tampoco valida visiblemente qué es correcto. «Entregar» o el agotamiento del tiempo consolidan el conjunto final y mantienen ocultas las soluciones hasta la fase de revisión autorizada. Antes de entregar, las entradas pueden editarse sin penalización. Mostrar sólo el número de respuestas **introducidas**, nunca el número de aciertos o soluciones pendientes.
- «Comprobar»/«Entregar» es un acto **idempotente** por intento (`attemptId`): varios clics, rotación, reconstrucción de pantalla o sincronización no duplican notas/estadísticas/tiempos. Una vez corregido, editar requiere iniciar **un nuevo intento**.
- Salir o recibir una llamada antes de entregar: conservar un borrador recuperable, sin calificar como fallo ni generar estadística de respuesta. Un examen cuyo temporizador termina puede entregarse automáticamente con lo escrito y dejar las demás omitidas, siempre que sea la regla visible al empezar el examen.
- En cero soluciones o ejercicio mal formado: **no permitir publicarlo ni examinarse**. En importación, rechazar el ejercicio con error claro o dejarlo en cuarentena sin perder el resto del lote validado; reportar totales exactos, nunca mostrar éxito global silencioso.
- Si una solución cambia mientras hay una sesión abierta, congelar un **snapshot de soluciones y rúbrica** en el intento, o impedir la edición concurrente; nunca corregir según una lista distinta de la mostrada al comienzo.
- Para coincidencia ambigua o elemento disputado, mostrar «No validada» con posibilidad de revisión posterior en el banco y trazabilidad; no declarar correcta por suposición. El criterio de si se penaliza una ambigüedad técnicamente irresoluble debe quedar configurado antes del lanzamiento; **predeterminado provisional: incorrecta**, con posibilidad de recalificación auditada si se corrige el banco.

### Sesiones mixtas y estadísticas
- Cada ejercicio escrito contribuye **una puntuación normalizada entre 0 y 1**, independientemente del número de soluciones internas. Si hay 5 tests y 1 ejercicio escrito, la media por ejercicio evita que las 12 soluciones escritas pesen por sí mismas como 12 preguntas test. Plantillas/ponderación por tipo podrían añadirse de manera explícita más adelante, nunca oculta.
- Almacenar métricas **por ejercicio** (nota, C, E, O, tipo, regla y fecha) y **por solución canónica** (cuándo se recordó, omitió o confundió). Las preguntas clásicas conservan su registro de estadísticas y la cola de fallos existente, sin generar tarjetas nuevas automáticamente.
- Una importación o sincronización no debe sumar intentos/errores otra vez. Los eventos se identifican por `attemptId`, y se valida la versión de la regla y los IDs de las soluciones. Los historiales personales se incluyen en **backup global**, pero se excluyen de un ZIP de ejercicios para compartir.
- Probar equivalencias de alias, acentos, ambigüedades, duplicados, respuesta vacía, `E > N`, nota inferior a 0, 100 % con extras erróneos, `b = 0`, valores base +1/−0,25, penalización editable −0,5 y −1, baremos personalizados por plantilla y mixto, valores inválidos/NaN/infinito, exámenes temporizados, guardado/reanudación, varias pulsaciones y dos dispositivos con sincronización simultánea.

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

## Especificación complementaria: paquetes, imágenes, backups y sincronización (19/11/2026)

- El diseño detallado y propuesto del **ZIP mixto versionado**, esquema `WRITTEN_MULTI`/`ANKI_CARD`, manifest de integridad SHA-256, remapeo de IDs, deduplicación por tipo, importación transaccional, restauración y sincronización está en [NEXUM_PAQUETES_EJERCICIOS_IMPORTACION_V1_20261119.md](NEXUM_PAQUETES_EJERCICIOS_IMPORTACION_V1_20261119.md).
- Distinguir estrictamente **exportación didáctica** (sin progreso personal), **backup global** (con imágenes, organización, sesiones e historial Anki) y **sincronización** (conciliación de cambios y conflictos).
- Mantener formatos antiguos de Testario/NEXUM legibles por la nueva versión y evitar que un lector antiguo acepte silenciosamente ejercicios que no sabe interpretar. Un ZIP con múltiples temas debe mostrar recuentos por tema/ejercicio y jamás declarar éxito parcial como completo.
- **Estado:** especificación escrita, NO implementada, aún sujeta a cerrar decisiones funcionales de grupos y sincronización antes del desarrollo.

## Acuerdos de importación y siguiente diseño de tarjetas (19/11/2026)

- **Decisión adoptada:** NEXUM **exportará exclusivamente ZIP** (temas, biblioteca y copias de seguridad), pero **importará ZIP y JSON automáticamente** según contenido, sin un selector de tipo de archivo. Se conserva la lectura heredada de TXT/CSV cuando aplique; las imágenes y los marcadores se comparten en ZIP para no perder assets.
- Se actualiza el documento [NEXUM_PAQUETES_EJERCICIOS_IMPORTACION_V1_20261119.md](NEXUM_PAQUETES_EJERCICIOS_IMPORTACION_V1_20261119.md) con esa decisión.
- El siguiente paso funcional es revisar la **propuesta del editor de tarjetas tipo Anki y su programación opcional** en [NEXUM_TARJETAS_ANKI_REPASO_20261119.md](NEXUM_TARJETAS_ANKI_REPASO_20261119.md). Incluye editor anverso/reverso, estudio libre, «Otra vez / Difícil / Bien / Fácil», intervalos configurables y reglas para no confundir la autoevaluación con la nota objetiva de exámenes mixtos.
- **Importante:** el diseño de tarjetas está propuesto **para revisión, no aprobado en todos sus detalles**. No se han realizado cambios funcionales en Android.

## Acuerdo sobre tipos de tarjetas Anki y presentación de soluciones (19/11/2026)

- **El usuario ha elegido** que, al pulsar «Mostrar respuesta», la **pregunta NO se sustituye**: permanece visible arriba y la **respuesta aparece debajo**. Para tarjetas Cloze y ocultación de imagen se destapa además el hueco/área en su sitio, con explicación debajo.
- Las tarjetas incorporarán **selector «Tipo de tarjeta»** y editores específicos para Básica, Bidireccional, Completar huecos/Cloze, Respuesta escrita y Ocultación de imagen. No crear una pestaña de navegación distinta para cada variedad. Mantener la repetición espaciada optativa en todas.
- Conservar el contenido común de una nota y los IDs/programaciones individuales de las tarjetas derivadas (inversas, varios huecos o máscaras), sin duplicaciones en Biblioteca, ZIP ni estadísticas.
- Detalle en [NEXUM_TARJETAS_ANKI_REPASO_20261119.md](NEXUM_TARJETAS_ANKI_REPASO_20261119.md). **Pendiente** de implementar y de confirmar las decisiones sobre algoritmo y examen mixto.

## Formato interoperable Anki (.apkg) — decisión adicional (19/11/2026)

- **Aprobado como objetivo:** importar y exportar tarjetas al formato nativo **`.apkg`** compatible con Anki Desktop y AnkiDroid, conservando mazos, tipos representables y recursos multimedia. Se accede desde una opción diferenciada «Importar mazo de Anki» / «Exportar para Anki».
- Esto **no sustituye** el ZIP predeterminado de NEXUM para biblioteca general y copias de seguridad; es una **excepción de interoperabilidad** para tarjetas, ni vuelve a habilitar exportaciones JSON generales.
- Preservar notas y tarjetas derivadas (inversas, cloze, oclusión), controlar tipos personalizados y conflictos, avisar antes de cualquier degradación. No equiparar `.apkg` con sincronización AnkiWeb ni asumir transferibles sus calendarios personales sin validación.
- Diseño y condiciones de prueba en [NEXUM_TARJETAS_ANKI_REPASO_20261119.md](NEXUM_TARJETAS_ANKI_REPASO_20261119.md). **Pendiente de implementación**.

## Seguridad y consistencia antes de programar
- Diseñar esquema versionado/migraciones de nuevos ejercicios y respaldos, exportación/importación y deduplicación semántica por tipo de ejercicio; no alterar el esquema actual por improvisación.
- Validar alias vacíos/repetidos/ambiguos, puntuación con respuestas múltiples, cancelaciones, guardar progreso, corrección en Entrenamiento vs Examen, accesibilidad de teclado Enter, tamaños de pantalla, imágenes perdidas/desplazadas, marcadores fuera del 0..1, backups antiguos y restauraciones.
- Las listas de calles y conexiones concretas requieren fuente cartográfica validada. Nunca inventar que una vía conecta con otra por la similitud del nombre ni convertir este ejemplo en banco real sin documentación.

## Estado de esta PR
En esta PR no se ha implementado motor de ejercicios, tipos, editores, puntajes ni interfaces de mapas. Se guarda sólo el presente acuerdo de diseño para retomarlo cuando el usuario termine la inspección.
