# NEXUM — propuesta del editor de tarjetas y repetición espaciada (19/11/2026)

> **Estado:** PROPOSICIÓN DE DISEÑO para revisión del usuario, **NO implementada ni aprobada en todos sus detalles**. Se respetan los acuerdos existentes: tarjetas en la misma Biblioteca que los tests/ejercicios escritos; sin creación automática de tarjetas a partir de fallos; repetición espaciada **opcional y desactivada por defecto**; importación ZIP/JSON, exportación ZIP.

## 1. Editor de «Tarjeta Anki»

Al pulsar Biblioteca > Tema > Añadir > «Tarjeta», abrir un editor específico:
1. **Anverso:** texto/pregunta; imagen opcional.
2. **Reverso:** solución/explicación; imagen opcional. Conservar las imágenes de cada cara de forma independiente (no confundir con la capa de corrección de ejercicios escritos).
3. **Clasificación:** tema existente o nuevo, y sus grupos mediante la organización por temas vigente; búsqueda, favoritos si procede, etiquetas optativas. Evitar copiar físicamente una tarjeta si su tema pertenece a varios grupos.
4. **Vista previa:** mostrar anverso, «Mostrar respuesta», reverso; comprobar tipografía, imágenes, accesibilidad y ausencia de pistas.
5. **Guardar borrador / Guardar tarjeta**, editar, duplicar o mover. No publicar una tarjeta sin contenido útil en ambas caras; permitir respuestas breves, texto claro y nombres no ambiguos. No crear tarjetas por equivocaciones de otros ejercicios.

## 2. Experiencia de estudio

- **Estudio libre (siempre disponible):** mostrar anverso, revelar reverso y avanzar. No exige repetir en un día determinado y no altera programación de repetición espaciada. Las tarjetas de la Biblioteca no quedan ocultas por no estar «vencidas».
- **Repaso programado (cuando se active):** seleccionar «Pendientes», revelar reverso, calificar memoria con **«Otra vez», «Difícil», «Bien», «Fácil»**. Estos son los cuatro significados de los botones habituales de Anki: «Otra vez» si no se recordó; «Difícil» si se recordó con dificultad; «Bien» si se recordó con cierto esfuerzo; «Fácil» si se recordó sin esfuerzo. Mostrar debajo la próxima fecha/intervalo estimado de cada decisión.
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

Se revisó la documentación oficial de Anki (https://docs.ankiweb.net/studying.html y https://docs.ankiweb.net/deck-options.html): define valoraciones Again/Hard/Good/Easy, pasos de aprendizaje y reaprendizaje, y ajustes de intervalos/FSRS. Es una **referencia de comportamiento**, no una afirmación de compatibilidad del formato o de que el motor de NEXUM vaya a incorporar FSRS de inmediato.

**Pendiente de validación de usuario:** modo de algoritmo para el primer lanzamiento (simple transparente frente a adaptativo), presentación de opciones avanzadas, y tratamiento exacto de las tarjetas autoevaluadas en exámenes mixtos con nota. El resto de este archivo es una propuesta consistente con los acuerdos ya adoptados.
