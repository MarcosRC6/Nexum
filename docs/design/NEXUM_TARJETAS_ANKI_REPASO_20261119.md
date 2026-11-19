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

## 5. Referencias informativas y decisiones pendientes

Se revisó la documentación oficial de Anki (https://docs.ankiweb.net/manual/getting-started, https://docs.ankiweb.net/manual/editing, https://docs.ankiweb.net/manual/templates/fields, https://docs.ankiweb.net/studying.html y https://docs.ankiweb.net/deck-options.html): describe tipos Básica, inversa, respuesta escrita, Cloze y ocultación de imagen; el campo FrontSide en plantillas permite mantener el frente visible durante la respuesta; también define valoraciones Again/Hard/Good/Easy, aprendizaje/reaprendizaje e intervalos/FSRS. Es una **referencia de comportamiento**, no una afirmación de compatibilidad del formato o de que el motor de NEXUM vaya a incorporar FSRS de inmediato.

**Pendiente de validación de usuario:** modo de algoritmo para el primer lanzamiento (simple transparente frente a adaptativo), presentación de opciones avanzadas, y tratamiento exacto de las tarjetas autoevaluadas en exámenes mixtos con nota. El resto de este archivo es una propuesta consistente con los acuerdos ya adoptados.
