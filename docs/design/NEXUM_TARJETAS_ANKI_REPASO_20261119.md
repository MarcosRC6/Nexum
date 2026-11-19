# NEXUM — propuesta del editor de tarjetas y repetición espaciada (19/11/2026)

> **Estado:** PROPOSICIÓN DE DISEÑO para revisión del usuario, **NO implementada ni aprobada en todos sus detalles**. Se respetan los acuerdos existentes: tarjetas en la misma Biblioteca que los tests/ejercicios escritos; sin creación automática de tarjetas a partir de fallos; repetición espaciada **opcional y desactivada por defecto**; importación ZIP/JSON, exportación ZIP.

## 1. Tipos de tarjetas y editor específico (actualización 19/11/2026)

**Decisión del usuario:** una «Tarjeta Anki» no es exclusivamente anverso/reverso. Se incorporará **selector desplegable «Tipo de tarjeta»** dentro de Biblioteca > Tema > Añadir > «Tarjeta», y un editor adecuado a la modalidad seleccionada. Tipos acordados conceptualmente o propuestos según el estándar de Anki:

| Tipo de NEXUM | Enunciado/edición | Al revelar |
|---|---|---|
| **Básica** | Campo «Pregunta», campo «Respuesta» e imágenes opcionales | **Conservar la pregunta arriba y mostrar la respuesta DEBAJO**, sin sustituir ni voltear la tarjeta |
| **Bidireccional (invertida)** | Dos campos A y B y control para generar la dirección inversa | Dos tarjetas de repaso enlazadas al mismo contenido A→B y B→A, cada una con su propio progreso |
| **Completar huecos (Cloze)** | Texto con una o varias selecciones convertibles en huecos mediante «Ocultar selección» | El texto original sigue visible y el hueco seleccionado se rellena o muestra su contenido en la misma posición; explicación adicional debajo |
| **Respuesta escrita** | Pregunta + solución canónica, alias opcionales y un campo donde el alumno escribe | Pregunta arriba, comparación de lo introducido con la solución debajo, sin depender de autogenerar tarjetas por fallos |
| **Ocultación de imagen** | Imagen con máscaras rectangulares o de otras formas editables | La misma imagen permanece; se revela la zona oculta y la explicación puede aparecer debajo |

- Son modalidades internas del tipo «Tarjeta», no cinco pestañas ni cinco bibliotecas independientes. La interfaz de añadir/editar elige el tipo mediante desplegable y muestra solo los campos pertinentes.
- **Comportamiento visual transversal decidido:** «Mostrar respuesta» **NUNCA elimina ni reemplaza el enunciado**. La pregunta/texto/imagen original se mantiene arriba; debajo se despliega la solución y, si existen, explicación e imágenes asociadas. Para Cloze y ocultación de imagen, revelar en su lugar el hueco o máscara y añadir bajo la pregunta los detalles complementarios. Evitar salto de scroll que oculte la pregunta; en tarjetas largas permitir desplazamiento manteniendo el contexto. No mostrar contenido de solución antes del botón.
- Editor **Básica**: Pregunta, Respuesta, explicación opcional, foto de enunciado opcional y foto de respuesta opcional. El creador puede activar reversa o pasar a formato bidireccional; no se pierde el progreso por una conversión no confirmada.
- Editor **Bidireccional**: un registro de contenido (nota) con dos tarjetas derivadas, pero programaciones independientes si está activo el repaso espaciado. Permitir generar solo dirección directa/inversa; evitar crear dos entradas duplicadas en el banco.
- Editor **Cloze**: permitir seleccionar texto y pulsar «Crear hueco». Varios huecos con grupos/IDs estables permiten repasar una o varias omisiones, en sesiones separadas cuando proceda. Conservar texto completo estructurado, no guardar únicamente los puntos suspensivos. Distinguir hueco visible de solución almacenada para prevenir filtraciones. Pistas opcionales y siempre visibles solo si el autor lo permite.
- Editor **Respuesta escrita**: al estudiar, la persona teclea una solución, pulsa Enter/«Comprobar» y ve el texto escrito comparado con la referencia; la valoración de Anki sigue pudiendo ser personal para el planificador, salvo configuración explícita de evaluación objetiva en un examen. No confundir este formato de **una respuesta** con «Respuesta múltiple escrita», que solicita **varias soluciones distintas** y tiene baremo de errores.
- Editor **Ocultación de imagen**: importar imagen, poner/mover/borrar máscaras no destructivas con coordenadas normalizadas; modos sugeridos «Ocultar una»/«Ocultar todas» para elegir qué zonas se muestran en cada tarjeta. La imagen base nunca se sobrescribe; al revelar se destapa el área y se permite texto adicional debajo. No confundir estas máscaras con los números de solución del editor de respuesta múltiple.
- Todos los editores incluirán asignación de tema/grupo según organización existente, vista previa con pregunta y solución debajo, soporte opcional de imágenes, validación de campos, guardado recuperable de borrador, búsqueda y edición posterior, y exportación ZIP / importación ZIP o JSON según capacidades del formato.
- Si una nota Cloze o bidireccional produce varias tarjetas, cada **tarjeta derivada tiene un ID único y progreso individual**; el contenido común se edita una sola vez. La exportación y deduplicación se hacen sobre nota/contenido y tarjetas sin inventar nuevas fichas por fallos.
- **Compatibilidad importante:** esto se inspira en los tipos oficiales descritos en el manual de Anki, pero no compromete todavía la importación directa de archivos `.apkg` ni la reproducción exacta de sus plantillas personalizadas.

## 2. Experiencia de estudio

- **Estudio libre (siempre disponible):** mostrar pregunta/texto/imagen y mantenerlos al revelar la solución **justo debajo**, o destapar el hueco/máscara en su lugar; luego avanzar. No exige repetir en un día determinado y no altera programación de repetición espaciada. Las tarjetas de la Biblioteca no quedan ocultas por no estar «vencidas».
- **Repaso programado (cuando se active):** seleccionar «Pendientes», revelar la solución debajo de la pregunta sin reemplazarla, calificar memoria con **«Otra vez», «Difícil», «Bien», «Fácil»**. Estos son los cuatro significados de los botones habituales de Anki: «Otra vez» si no se recordó; «Difícil» si se recordó con dificultad; «Bien» si se recordó con cierto esfuerzo; «Fácil» si se recordó sin esfuerzo. Mostrar debajo la próxima fecha/intervalo estimado de cada decisión.
- **No confundir valoración con nota objetiva**: elegir «Bien» en una tarjeta no equivale a acertar una pregunta test; un examen mixto debe mostrar las tarjetas sin incorporarlas automáticamente a una nota numérica objetiva, salvo que se defina un método de evaluación verificable. Debe permanecer visible su número como tarjetas estudiadas.
- **Nuevas / aprendiendo / pendientes / suspendidas:** filtros y recuentos comprensibles. Límite diario de nuevas y de repaso opcional, no bloqueo absoluto de estudio libre.
- **Uso en Entrenamiento / Examen:** Entrenamiento permite estudio libre y repaso programado. En Examen las tarjetas requieren decidir previamente si se incluyen como autoevaluadas y fuera de la nota global o si solo se admiten tarjetas con comprobación objetiva; no conceder automáticamente el 100 % por pulsar «Bien». No revelar el reverso mientras esté en fase de preguntas sin revisar.

## 3. Configuración optativa de repetición espaciada

- Interruptor general **«Repetición espaciada»**, desactivado por defecto. Sin activar no se genera calendario; activarlo no crea tarjetas nuevas ni inserta errores en una cola.
- **Ajustes simples y editables por el usuario:** pasos iniciales de aprendizaje y reaprendizaje (minutos, horas o días, secuencia válida); intervalo tras recuerdo correcto; multiplicador/modificador de dificultad; intervalo máximo; límite de tarjetas nuevas y pendientes al día; hora de cambio de jornada. Mostrar valores por defecto prudentes, explicación en español y botón «Restablecer».
- **Botones configurables:** los nombres recomendados son «Otra vez», «Difícil», «Bien», «Fácil»; se puede configurar cuánto influyen en la programación según el método elegido, con vista previa «Próximo repaso». No hacer corresponder un botón siempre a un número fijo de días cuando el método sea adaptativo.
- **Política de algoritmo por decidir:** primera alternativa, planificador determinista sencillo basado en intervalos editables, explicable y probado; segunda, compatibilidad futura con un planificador adaptativo FSRS, que requiere datos de historial, versiones de parámetros y pruebas específicas. **«Similar a Anki» no equivale a garantizar identidad de programación con Anki**, y no se promete importación .apkg ni sincronización con AnkiWeb.
- El último intervalo asignado, fecha prevista, valoraciones y estado de cada tarjeta deben guardarse **por tarjeta**. Cambios de configuración no recalifican retroactivamente intentos; cualquier «reprogramar todas» será una acción explícita con aviso/vista previa.
- Permitir pausar/desactivar sin borrar contenido ni historial, restablecer una tarjeta individual por acción expresamente confirmada, y estudiar voluntariamente antes de la fecha sin que ese estudio libre reprograme por accidente.

## 4. Integridad, eventos y copia

- Un evento de repaso programado tiene **ID estable**, timestamp, ID de tarjeta, valoración, algoritmo/versión, intervalo anterior/siguiente y firma de dispositivo cuando proceda. No aplicar doble calificación por dos toques, rotación, cambio de tema, interrupción o reintento de sincronización.
- La copia **global ZIP** incluye tarjetas, imágenes, historial de valoraciones, ajustes y fechas; la **exportación didáctica ZIP** incluye tarjetas e imágenes pero **no** el calendario personal de estudio. Se puede importar biblioteca en ZIP y JSON, con validación de tipos; un JSON sin imágenes incrustadas debe declararlas como referencias ausentes o rechazarse con explicación, no perderlas silenciosamente.
- Manejar cambios de huso horario y fecha, horario de verano, atrasos acumulados, reinicios de móvil, registros corruptos y conflictos de revisión desde dos dispositivos. Un contenido eliminado no debe volver por accidente desde otro dispositivo.
- Ninguna valoración puede crear automáticamente tarjetas a partir de preguntas falladas: esa función está **retirada del plan**.

## 5. Intercambio nativo con Anki Desktop y AnkiDroid — aprobado como objetivo (19/11/2026)

**Decisión del usuario:** facilitar la **importación y exportación de tarjetas** usando los propios paquetes de Anki, no solo convertirlas a un ZIP exclusivo de NEXUM. Formato inicial prioritario: **paquete de mazo `.apkg`** (con notas, tipos de nota, tarjetas y recursos multimedia). La opción es específica de tarjetas y no convierte automáticamente los ejercicios escritos o los tests de NEXUM en tarjetas.

### Interfaz propuesta

- **Importar > «Mazo de Anki (.apkg)»**. Detectar también el formato por contenido, sin confundirlo con un ZIP de NEXUM. Previsualizar mazos, cantidad de notas y tarjetas, tipos, imágenes/sonidos, duplicados y elementos potencialmente incompatibles. Elegir tema/grupo de destino; por defecto cada mazo puede crear un tema, ofreciendo mapa editable mazo→tema antes de importar.
- **Exportar > «Para Anki (.apkg)»**, disponible al seleccionar tarjetas, temas o grupos con tarjetas. Incluir **medios** (imágenes y demás adjuntos admitidos), plantillas/tipos de tarjeta y nombres de mazo. Importar el archivo generado debe funcionar en **Anki Desktop y AnkiDroid**. Antes de crear el archivo avisar del número de tarjetas exportables y de las no compatibles (no omitirlas sin consentimiento).
- **Exportar > «ZIP de NEXUM»** sigue siendo la opción predeterminada para todos los contenidos y copias completas. `.apkg` es una **excepción voluntaria específica para interoperar con Anki** al acuerdo «exportación ZIP exclusivamente»: es un formato de paquete comprimido propio de Anki, con extensión `.apkg`, no un JSON ni un ZIP de NEXUM renombrado.
- Se puede admitir `.colpkg` como opción **avanzada de importación de una colección completa**, más adelante y solo tras implementar un lector seguro: debe avisar de diferencias entre una colección completa y un mazo compartible. No tratar `.colpkg` como un backup NEXUM ni permitir sustituciones/destrucciones implícitas.
- **Sin sincronización directa con AnkiWeb en esta fase:** un intercambio `.apkg` no es sincronización en tiempo real ni implica acceso a la cuenta Anki.

### Preservación de tipos y datos

- **Básica**, **Bidireccional**, **Cloze**, **Respuesta escrita** y **Ocultación de imagen** deben mapearse a tipos y plantillas de Anki **realmente compatibles**. En cloze, preservar grupos de huecos y texto original; en bidireccionales, mantener una nota fuente con tarjetas derivadas; en ocultación, máscaras/imagen y comportamiento de revelar. Si una variedad avanzada no puede representarse fielmente, **advertir y ofrecer exportar solo tipos compatibles**, no degradarla silenciosamente a tarjeta básica.
- **Notas ≠ tarjetas:** una nota de Anki puede generar varias tarjetas. Al importar, conservar la relación nota–tarjetas y sus IDs/GUID estables cuando sea posible; al exportar y reimportar, detectar coincidencias con IDs de origen, contenido y tipo para reducir duplicados. La edición posterior deberá diferenciar cambio de una nota y alteración de una tarjeta derivada.
- Multimedia (imágenes, sonido) debe viajar dentro del paquete; verificar referencias y hashes. Las plantillas HTML/CSS de mazos de terceros requieren tratamiento seguro (evitar ejecutar scripts arbitrarios, URLs peligrosas, acceso a archivos locales, o contenido ejecutable no fiable). En plantillas no compatibles, importar contenidos con vista previa y mapeo explícitos, sin prometer renderizar cualquier complemento o JavaScript de Anki.
- **Grupos de NEXUM ≠ mazos de Anki:** en NEXUM los grupos referencian temas y un tema puede estar en varios grupos; un mazo Anki forma una jerarquía de mazos/submazos. Exportar cada tarjeta **una sola vez** en un mazo principal y representar otras pertenencias mediante etiquetas o metadatos visibles; ofrecer una previsualización de la correspondencia, sin duplicar tarjetas por pertenencia a varios grupos.
- **Progreso de estudio:** en importación de `.apkg`, **por defecto solo contenido**, sin sobrescribir calendarios personales de NEXUM. Anki permite incluir información de programación en sus paquetes, pero transferir de forma exacta calendarios e historial de Anki a un algoritmo distinto de NEXUM **no está garantizado**. Si posteriormente se ofrece importar/exportar progreso, se condicionará a compatibilidad demostrada, consentimiento expreso y una tabla de conversión documentada; nunca restablecer o modificar repeticiones automáticamente.
- Copia global NEXUM sigue siendo ZIP propio para recuperar completamente configuraciones, imágenes y progreso NEXUM. `.apkg` es **intercambio didáctico con Anki**, no alternativa a ese backup.

### Pruebas exigidas

- Importar `.apkg` reales exportados desde **Anki Desktop y AnkiDroid** con imágenes, cloze, notas bidireccionales y marcas; comprobar el informe de importación y los medios.
- Exportar desde NEXUM, abrir en **Anki Desktop** y **AnkiDroid**, estudiar y revelar pregunta arriba/respuesta debajo sin errores; comprobar texto, acentos, medios y huecos.
- Round-trip NEXUM → Anki → NEXUM sin duplicados de notas ni tarjetas derivadas, respetando que ediciones simultáneas pueden requerir resolver conflictos.
- Mazo con `HTML/CSS/JavaScript` personalizado, imagen no encontrada, tipo de nota raro, archivo manipulado/truncado, nombres duplicados, tamaño excesivo, IDs repetidos o scheduling incompleto: **rechazo o degradación explícita autorizada** sin perder el banco.
- No mostrar opción de «exportar .apkg» para ejercicios que no puedan representarse como tarjetas; nunca exportar silenciosamente menos elementos de los seleccionados.

### Fuentes oficiales verificadas

- Manual Anki, [Exporting](https://docs.ankiweb.net/exporting.html): paquetes `.apkg` y `.colpkg`, medios y scheduling.
- Manual Anki, [Packaged Decks](https://docs.ankiweb.net/importing/packaged-decks.html): importación de notas/mazos/tipos y tratamiento de duplicados.
- [Manual de AnkiDroid](https://docs.ankidroid.org/manual.html): admite importar/exportar `.apkg`, con diferencias entre paquete de mazo y de colección.

**Estado:** interoperabilidad nativa **aprobada como objetivo**, aún no implementada. Los detalles del mapeo de tipos avanzados y calendarios deben probarse con paquetes reales, nunca declararse compatibles sin validación.

## 6. Referencias informativas y decisiones pendientes

Se revisó la documentación oficial de Anki (https://docs.ankiweb.net/manual/getting-started, https://docs.ankiweb.net/manual/editing, https://docs.ankiweb.net/manual/templates/fields, https://docs.ankiweb.net/studying.html y https://docs.ankiweb.net/deck-options.html): describe tipos Básica, inversa, respuesta escrita, Cloze y ocultación de imagen; el campo FrontSide en plantillas permite mantener el frente visible durante la respuesta; también define valoraciones Again/Hard/Good/Easy, aprendizaje/reaprendizaje e intervalos/FSRS. Es una **referencia de comportamiento**, no una afirmación de compatibilidad del formato o de que el motor de NEXUM vaya a incorporar FSRS de inmediato.

**Pendiente de validación de usuario:** modo de algoritmo para el primer lanzamiento (simple transparente frente a adaptativo), presentación de opciones avanzadas, tratamiento exacto de las tarjetas autoevaluadas en exámenes mixtos con nota, y decisiones de compatibilidad avanzada de paquetes Anki. La interoperabilidad de mazos `.apkg` sí está aceptada como objetivo, condicionada a implementación y pruebas reales. El resto de este archivo es una propuesta consistente con los acuerdos ya adoptados.
