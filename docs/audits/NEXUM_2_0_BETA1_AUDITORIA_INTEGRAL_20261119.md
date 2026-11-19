# Auditoría integral NEXUM 2.0 Beta 1 — 19/11/2026

**Objeto auditado:** NEXUM pre-release `v2.0-beta.1`, release firmada anclada al commit `99825fff8010df1ad92dffe01b119096a3dfeeee`. Repositorio `MarcosRC6/Nexum`, PR de implementación #29 (draft), rama `feature/exercise-engine-staged-20261119`. Rama estable `main` permanece v1.8. **No se han instalado ni ejecutado APK en un emulador o teléfono del asistente; los hallazgos de pantalla proceden de las cuatro capturas reales aportadas por el usuario y los demás de inspección estática del código.** El pipeline de compilación/pruebas/lint de publicación beta pasó; eso no equivale a certificación funcional completa.

## A. Veredicto y criterios

- **P0 / bloqueante:** exposición a pérdida o inconsistencia de datos, notas incorrectas/estados de examen, migración/restauración insegura, ruta principal indisponible.
- **P1 / alta:** tarea principal incompleta, edición/corrección defectuosa, regresión de experiencia, ergonomía con consecuencias materiales.
- **P2 / mejora:** claridad visual, pulido, indicadores, accesibilidad y detalles de interacción.
- **Estado global:** Beta 1 demuestra modelos nuevos, un corrector escrito básico y un editor/prototipo de estudio; **no satisface todavía el diseño de NEXUM 2.0**. Trabajar sobre la PR sin actualizar `main` ni publicar otra APK hasta verificar los P0 y las regresiones de datos.

## B. Hallazgos demostrables

### P0-01 · Los nuevos ejercicios no se integran en el motor de sesiones de NEXUM

**Evidencia:** `TestConfigScreen.kt:197-205,597,659-680` selecciona exclusivamente `topic.questions`; `TestSessionViewModel.kt:111-149` crea sesiones de `TestQuestionState` sobre `Question`. `TestTemplate` en `CoreModels.kt:68-87` no contiene mezcla de modalidades. `TopicDetailScreen.kt:309-316` abre ese configurador antiguo. Captura 3: tema con un ejercicio nuevo y «0 Preguntas»; al pulsar «Hacer Test» no hay preguntas tradicionales.

**Acción:** unificar la **selección y ejecución** por tipo, sin convertir preguntas clásicas destructivamente. Configurar «Solo test / Solo Enumerar / Solo tarjetas / Mixto», respetar filtros por tema/grupos/plantillas, historial, temporizador y reglas de corrección en Examen. Proponer `SessionItem` discriminado por modalidad con `attemptId` y snapshot, manteniendo intactos los tests/estadísticas históricos. Sin ejercicios objetivos, mostrar «Sin nota objetiva», no 0/10 o 10/10 falso. Añadir disponibilidad y recuentos por tipo. **No basta con otro botón Practicar como sesión separada.**

**Pruebas:** tema solo con un escrito; tema solo con tarjeta; tema con test+escrito+tarjeta; varios temas/grupos; plantillas antiguas; examen cronometrado; salir/restaurar; nota objetiva separada del repaso manual.

### P0-02 · Importación de ZIP multimodal y por varios temas no es transacción integral

**Evidencia:** `SettingsScreen.kt` importa temas en cola, invocando `importSingleTopicProcessed` por tema; `ImportManager.kt:392-460` confirma imágenes/tema por separado (`commitPendingImages`, `saveDatabase`). Los 15 temas del mismo ZIP pueden llegar a un resultado parcialmente aplicado. `ImportManager.kt:47-190` pre-valida JSON y recursos (mejora positiva), pero el commit se hace por cada entrada. `ImportManager.kt:463-485` valida referencias. El formato sigue siendo ZIP con `testario_database.json` de esquema heredado, sin manifiesto íntegro versionado específico de `nexum_library_bundle` ni correspondencia de conflictos para recursos de un lote.

**Acción:** previsualizar y validar lote completo, preparar resolución de colisiones por todos los temas, staging, aplicar cambios en una transacción lógica con rollback de base **e imágenes**, informe final por tema: importados/idénticos/conflictos/descartados/errores. Permitir continuar parcialmente **solo** con selección explícita del usuario, nunca éxito ficticio. Preservar ZIP clásicos/JSON.

**Pruebas:** 15/15 temas, fallo en 15.º tras 14 válidos, falta foto, colisión de nombre+hash, nombres de temas repetidos y varios grupos, reintento idempotente.

### P0-03 · Restauración global no es atómica entre todos los subsistemas

**Evidencia:** `UnifiedDataManager.kt:145-211` valida entradas, luego extrae imágenes (línea 192), modifica temporizador (195), restaura DB (199), Plan (201), enlaces (204), ajustes (207) y sesión (210), retornando false si falla una fase posterior **sin** rollback global de las previas. `NexumNearbyArchive.kt` dispone de rescate para su propia sincronización, pero no corrige todas las entradas de restauración manual.

**Acción:** transacción de restore planificada con foto de estado previo y rollback de imágenes/DB/Plan/ajustes/sesión, informe verificable, prueba de migraciones v1/v3/v4 y restauración tras cortes deliberados. Mantener respaldo global ZIP previo fuera del dispositivo.

**Pruebas:** forzar fallo de lectura/escritura después de cada fase, comprobar byte a byte contenidos previos y relaciones internas.

### P0-04 · Una base futura/corrupta puede abrirse vacía en memoria y permitir escribir sobre ella

**Evidencia:** `MigrationManager.kt:11-17` rechaza una base de versión superior (correcto); `TestarioLocalStore.kt:33-69` captura cualquier fallo, copia recuperación y crea `TestarioDatabase()` vacío en memoria; `saveDatabase` sigue disponible en `:75-89`. Un usuario que abra una versión más antigua y luego use la app podría sobrescribir el archivo original. El respaldo pre-v4 añadido en Beta 1 es útil pero no sustituye bloqueo de lectura/escritura.

**Acción:** estado explícito **READ_ONLY_RECOVERY**, pantalla de error recuperable, sin crear/editar/importar/sobrescribir hasta que se seleccione una restauración verificada o migración soportada; no arrancar la biblioteca como nueva sin consentimiento. Pruebas v3->v4 y futuras v5->v4.

### P0-05 · Cambio destructivo de tipo o de fotografía sin verificación de contenido dependiente

**Evidencia:** `StudyExerciseEditorScreen.kt:121-132` permite cambiar `WRITTEN_MULTI`/Anki y `:86-101` guarda bajo el mismo ID, vaciando `solutions` o `flashcard` de la otra modalidad. `:68-85,230-255` permite reemplazar una fotografía mientras pueden quedar puntos ligados a la foto antigua, y `:278-281` borra todas las selecciones Cloze al editar cualquier texto, sin confirmación. En las tarjetas de ocultación, la sustitución de imagen puede dejar máscaras desalineadas.

**Acción:** bloquear cambio silencioso de modalidad en registros existentes; si se quiere convertir, asistente de conversión con previsualización, copia original recuperable y confirmación; reemplazar imagen recalcula/rehace o exige borrar puntos y máscaras; al editar texto conservar/corregir huecos por IDs/offsets o solicitar confirmación de cambio destructivo. Nunca dejar marcadores fantasma ni usar la misma ID con otro tipo sin migración explícita.

### P0-06 · Estudio normal puede modificar la agenda Anki sin separar intenciones

**Evidencia:** `StudyExercisePracticeScreen.kt:281-306`: muestra «Otra vez/Difícil/Bien/Fácil» y llama a `recordAnkiReview` cuando el interruptor **global** `ankiReviewEnabled` está activo, aunque el usuario haya entrado en «Practicar ejercicios». Esto contradice la decisión: repaso programado solo en modo explícito, estudio libre sin efectos, examen nunca modifica calendario. Los `newCardsPerDay` y `reviewsPerDay` de `AnkiScheduleSettings` están declarados pero no se consumen en esa pantalla.

**Acción:** separar modo de sesión `FREE_STUDY` / `SCHEDULED_REVIEW` / `EXAM`; solo el segundo permite programar, con eventos idempotentes y filtros por fecha. Examen no llama nunca al programador. No mostrar un único interruptor global como sustituto del modo.

### P1-01 · Pantallas de creación sin distinción visual ni estructura

**Evidencia:** ambas opciones de `TopicDetailScreen.kt:294-305` usan `StudyExerciseEditorScreen` (`TestarioApp.kt:302-312`); esta pantalla presenta un chip de modalidad y campos en un `Column` largo (`StudyExerciseEditorScreen.kt:106-140`). La captura 1 muestra el modo Anki seleccionado, por lo que el contenido sí cambia; sin embargo, ambas rutas abren el mismo armazón poco guiado.

**Acción:** reutilizar componentes y tema de NEXUM, pero ofrecer encabezados/títulos y fases diferenciadas; entrada «Añadir > Test | Enumerar | Tarjeta» con el tipo elegido y bloquear cambios accidentales de datos. Diseño compacto por secciones y jerarquía equivalente a `QuestionEditorScreen.kt:152-204,399-426`.

### P1-02 · Alias automáticos seguros, sin casillas obligatorias

**Evidencia:** `StudyExerciseRules.kt:13-17,142-155` normaliza tildes, caja y separadores, pero solo acepta nombre completo o aliases escritos manualmente `CanonicalSolution.aliases`; editor obliga visualmente a campo alias `StudyExerciseEditorScreen.kt:159-165`.

**Diseño de reemplazo:** los aliases manuales dejan de ser necesarios; motor local **determinista y limitado**, sin respuesta aproximada por Levenshtein, sin coincidencias de subcadena, sin IA improvisada. 
- Normalizar caja, acentos, puntuación, espacios y variantes tipográficas.
- Expansiones delimitadas y verificables de prefijos: `C/` ↔ `Calle`, `Av.`/`Avda.` ↔ `Avenida`, `Pza.` ↔ `Plaza`, `Pº` ↔ `Paseo`; artículos y preposiciones solo bajo patrones configurados.
- Nombres de vías: aceptar la parte distintiva sin tipo de vía si resulta inequívoca, p. ej. «Thomas Edison» por «Calle Thomas Edison». Un único apellido o término («Edison») solo cuando identifica exactamente **una** respuesta canónica del ejercicio, cumple longitud/reglas de palabra distintiva y no es un genérico. Las soluciones de un mismo ejercicio deben participar **todas** en la detección de ambigüedad; si «Edison» identifica dos, no aceptar ninguna por esa abreviatura.
- No autogenerar cualquier palabra interior, iniciales arbitrarias, nombres incompletos equívocos o supuestos sin fundamento. Distinguible entre nombre canónico, variantes seguras y ambigüedad.
- Generar variantes **al comparar**, sin editar en silencio las respuestas canónicas; pantalla plegable opcional «Variantes admitidas» para inspección/opt-out, no tarea obligatoria del autor.
- Mantener un solo acierto por solución aunque el alumno introduzca nombre completo y abreviado; un error duplicado penaliza una vez. Comparación y puntuación auditables/reproducibles.

**Pruebas:** «Calle Thomas Edison»/«Thomas Edison»/«Edison» cuando único; «Avenida Cataluña»/«Av. Cataluña»; dos vías terminadas en Edison; «Mayor» con dos localizaciones; respuestas cortas de una sola palabra; tildes y abreviaturas no listadas; falso positivo de fragmento y tres respuestas similares.

### P1-03 · Vista previa incompleta y sin garantía contra revelar respuestas

**Evidencia:** `StudyExerciseEditorScreen.kt:357-367` solo representa texto+errores; no fotografía, lienzo, números de solución, prueba de respuesta ni reverso de tarjeta. Captura 1 y 4.

**Acción:** vista previa «Como alumno / Corrección», con la **misma** representación que Entrenamiento, imagen visible, puntos exclusivamente al corregir, pregunta persistente arriba y solución debajo. Permitir prueba local sin crear estadísticas/eventos.

### P1-04 · «Crear imagen»: lienzo libre editable, no solo línea

**Evidencia:** `StudyExerciseModels.kt:62-68` únicamente permite inicio/fin de línea y flecha; `StudyExerciseEditorScreen.kt:181-228` dibuja una línea recta fija. Captura 2 muestra demasiados marcadores solapados.

**Nuevo alcance solicitado:** reemplazar la opción por **«Crear imagen»**. Abrir lienzo para dedo, lápiz táctil o ratón, herramientas **Dibujar / Borrar / Deshacer / Rehacer / Limpiar con confirmación**, grosor/color y zoom si la pantalla lo exige. Guardar trazos editables mediante puntos/coordenadas normalizadas; generar base rasterizable para ZIP/Anki si procede. **Modo «Poner soluciones» separado de «Dibujar»** para evitar trazos confundidos con puntos. No incorporar flechas/direcciones automáticas ni cruzamientos que den pistas. Fotos importadas usan el mismo editor de marcadores, sin destruir el original.

### P1-05 · Marcadores: tocar, seleccionar, mover, renumerar y borrar

**Evidencia:** `StudyExerciseEditorScreen.kt:197-243` toca la superficie y agrega un punto nuevo al ID seleccionado; solo existe «Deshacer último punto» `:167-172`, no `onTapMarker` ni borrar uno concreto. `selectedSolutionId` puede arrancar `null` en una creación nueva `:49-51`, haciendo que el toque no agregue nada hasta seleccionar explícitamente un radio. Orden se calcula como máximo+1, sin renumeración.

**Acción:** primera solución seleccionada por defecto, un punto = ID estable+ID de solución+orden estable, hit-testing con radio adaptativo, toque sobre marcador abre «Editar / Mover / Eliminar», gestos separados de «Añadir punto», soporte de deshacer y renumerar a voluntad sin recontar respuestas. Confirmar borrados con consecuencias; evitar números solapados mediante desplazamiento de etiqueta o alerta de colisión y zoom; el usuario ve qué respuesta corresponde a cada marcador. Un nombre canónico con dos marcadores sigue siendo un único acierto.

### P1-06 · Corrección escrita de cada respuesta y cada número

**Evidencia:** `StudyExercisePracticeScreen.kt:166-208` muestra la lista de respuestas del alumno sin estado y, después de puntuación, otra lista de «Soluciones correctas». `StudyExerciseRules.WrittenResult` contiene `correctIds`/`incorrectInputs`, pero la UI no los usa para emparejar líneas del alumno.

**Nuevo flujo solicitado:** tras «Comprobar», **conservar la lista del alumno en su lugar**. Acierto verde (número/valor), fallo rojo (número/valor), y a la derecha la forma canónica correcta en verde **solo si existe correspondencia demostrable**. En enumeraciones **sin orden** no asignar arbitrariamente un error a una solución omitida: mostrar abajo «Faltaban: ...» como entradas canónicas verdes; los números/puntos de la imagen se colorean según la solución canónica acertada/omitida, sin colocar un segundo marcador ni inventar relación con una respuesta errónea. Si es un ejercicio posicional con orden exigido, sí se puede emparejar por posición tras configurar esa regla de forma explícita. Mantener el baremo editable y explicación separada de intento objetivo.

**Pruebas:** 2 aciertos+1 error+1 omitida; 3 entradas erróneas y 2 soluciones; cero errores/nota 10; abreviatura aceptada; respuestas en orden diferente; un punto con varias respuestas canónicas (prohibir relación inválida); varias posiciones de una solución.

### P1-07 · «Siguiente ejercicio» circular sin reiniciar estado

**Evidencia:** `StudyExercisePracticeScreen.kt:209-212` avanza `(index + 1) % written.size`; cuando hay uno, índice no cambia; `rememberSaveable(tab,index)` mantiene respuesta, `completed=true`, ID de intento. Situación visible «Ejercicio 1 de 1». Problema equivalente en Anki `:299-300` y su evento de repaso cuando tamaño=1.

**Acción:** botones **«Repetir»** (nueva ID y borrador limpio), **«Terminar»** (cierra sesión), «Siguiente» solo si hay un siguiente verdadero. Si se desea ciclo infinito debe ser explícito y generar nuevo intento.

### P1-08 · Menú de tres puntos y acciones de contenido uniformes

**Evidencia:** `TopicDetailScreen.kt:521-546` representa nuevo ejercicio con botones Editar/Practicar. Preguntas tradicionales usan `QuestionCard` con `DropdownMenu` Editar/Duplicar/Eliminar y confirmación `:919-1092`; `StudyExerciseRepository.kt:31-42` ya tiene delete pero no ruta UI; duplicado de modelo disponible en `StudyExerciseMerge.duplicate` sin acceso desde Biblioteca.

**Acción:** una misma tarjeta visual, título y metadatos, **⋮ > Editar / Duplicar / Mover / Eliminar** con confirmación y limpieza conservadora de recursos, selección múltiple, accesibilidad y acciones consistentes. El botón «Practicar» por tarjeta no debe añadir ruido a Biblioteca (tampoco iniciar toda la colección cuando se pulsa desde un elemento concreto).

### P1-09 · Filtrar por tipo, contadores y búsqueda unificados

**Evidencia:** `TopicDetailScreen.kt:254-258` muestra stats de `topic.questions` exclusivamente; `:172-181` aplica búsqueda separada a ejercicios, sin un filtro de modalidad y los oculta cuando se activa «Solo marcadas»/dificultad; `:482-495` informa recuento de solo `filteredQuestions`; `:510-550` renderiza ambas colecciones sin integración de selección. `GroupsScreen.kt:407-437` y `StatsScreen.kt:79-85,162-188` también solo cuentan `Question`.

**Acción:** filtro desplegable **Tipo: Todos / Test / Enumerar / Tarjetas**, búsqueda combinada por enunciado+respuestas+tipo, recuentos por tipo sin confundir «preguntas test» y «ejercicios»; estadísticas distinguen intentos, aciertos, tarjetas repasadas, preguntas test falladas y progreso por grupo. No añadir pestañas inferiores: mantener las **4** actuales.

### P1-10 · Reducir la oferta de Anki visible y hacer los huecos accesibles

**Cambio de alcance solicitado tras usar Beta 1:** la superficie de autoría se simplificará a **«Tarjeta» básica**, con pregunta arriba y respuesta debajo, imagen independiente por lado y **opción de ocultar 1 o más palabras/frases seleccionadas**. Botón claro «Marcar fragmento» que convierta selección en hueco visible dentro del editor y opción «Desmarcar» sobre cada uno. Para usuarios de teclado/comandos, admitir una sintaxis textual opcional (p. ej. `{{palabra o frase}}`) con vista previa y escape para texto literal; almacenar internamente IDs/posiciones estructuradas, no confiar solo en índices frágiles.

- Una tarjeta puede contener varios fragmentos marcados (N).
- Ajuste por tarjeta/sesión: **preguntar 1, 2, N o aleatorio dentro de límites**; nunca esconder automáticamente todas si la configuración pide una.
- Al revelar, mantener enunciado arriba, reponer solo los fragmentos preguntados y mostrar soluciones/explicación debajo.
- No eliminar modelos ni registros de tarjetas avanzadas creadas en Beta 1; **conservar lectura y edición de legado** mediante compatibilidad/migración. Ocultar opciones avanzadas nuevas si ya no interesa diseñarlas en 2.0; diferir `.apkg` completo sin inventar compatibilidad. Si se implementa .apkg, empezar por Basic/Cloze de formato real e informar sobre los tipos no compatibles.

**Evidencia actual:** `StudyExerciseEditorScreen.kt:256-355` propone cinco modalidades y `:285-299` el flujo `Cloze`; `:279-281` pierde todos los huecos si cambia cualquier texto. `StudyExercisePracticeScreen.kt:218-279` una tarjeta Cloze genera tarjetas por hueco y no permite selección de 1/2 fragmentos de un total N. **No se ha implementado .apkg**.

### P1-11 · El almacenamiento de nuevas respuestas y revisiones no está ligado a todas las estadísticas ni a retención razonable

**Evidencia:** `LibraryViewModel.kt:95-131` añade `ExerciseAttempt` y `AnkiReviewEvent` a listas globales sin límites, compactación ni capa de consulta de estadísticas; `StatsScreen.kt` únicamente usa preguntas test. `TestarioLocalStore.kt:75-104` serializa la base completa para cada guardado asíncrono. Puede crecer en tamaño/latencia tras muchos repasos.

**Acción:** snapshots de sesión, agregación incremental, política de retención explícita sin borrar historial que el usuario necesite, estructuras indexables y tests de volumen (decenas de miles de eventos). No sumar intentos duplicados.

### P1-12 · Guardado de imagen en el hilo principal y orientación EXIF sin política completa

**Evidencia:** `StudyExerciseEditorScreen.kt:374-388` llama directamente a `libraryViewModel.saveStudyExercise`; `LibraryViewModel.kt:73-84` ejecuta `StudyImageStore.persist`, copia y SHA-256 de hasta 32MiB y decodificación de dimensiones, sin `Dispatchers.IO`. `StudyVisualImage.kt:68-86` toma dimensiones sin orientación EXIF; Coil puede mostrarla rotada según metadatos, de modo que la transformación de puntos puede no coincidir.

**Acción:** persistencia IO con estado «Guardando…», cancelación segura, controles de error y verificación EXIF/matriz de rotación, coordenadas normalizadas sobre imagen ya orientada; probar fotos retrato/apaisado, cámara y capturas.

### P1-13 · Filtros y plantillas de repetición espaciada todavía no respetan todos los ajustes declarados

**Evidencia:** `AnkiScheduleSettings` incluye límites diarios; `StudyExercisePracticeScreen.kt` enumera todas las tarjetas y no filtra vencidas o nuevas. Editor no permite cambiar intervalos ni muestra fecha estimada de cada botón. `AnkiReviewEngine` usa planificador determinista, no FSRS, lo cual es aceptable si se explica.

**Acción:** Repaso libre/Programado explícito, parámetros editables de forma sencilla, nuevos/vencidos/aprendiendo/suspendidos, límites, progreso por tarjeta derivada, información del siguiente repaso y política de zonas horarias.

### P1-14 · Portabilidad Anki y sincronización de ejercicios incompletas

**Evidencia:** no hay lector/escritor `.apkg` en el código beta; `ImportManager.kt:338-390` sincroniza solo preguntas tradicionales mediante `QuestionSyncMatcher`; `NexumNearbyArchive` transporta backup global, que reemplaza estado y no concilia cambios concurrentes de notas en dos móviles. Los widgets de test rápido y el configurador de examen también usan `Question` solamente.

**Acción:** no publicitar sincronización bidireccional de nuevos tipos mientras no exista. Intercambio .apkg real con imágenes, IDs y compatibilidad Anki/AnkiDroid, sin importar plantillas ejecutables arbitrarias; conciliación con conflictos y borrados, diferenciar «restaurar» de «fusionar».

### P1-15 · Cambios no guardados, borradores y error del editor

**Evidencia:** la mayor parte de estados de `StudyExerciseEditorScreen.kt:44-67` usa `remember` (no `rememberSaveable` ni estado de edición persistido), la acción «Borrador» exige pulsación manual. Cambios de tema, rotación, cierre de proceso o interrupción pueden perder trabajo inédito. El botón Guardar solo recorre `runCatching`; el usuario puede cambiar a otra modalidad por un toque y perder datos de la anterior.

**Acción:** guardado de borradores cada cambio con debounce y copia segura, confirmación de salida si hay cambios, recuperación por ID de sesión, bloqueo mientras se guarda y validación/no pérdida de texto y fotos ante cambio de orientación.

### P2-01 · Interfaz visual no alineada con la Biblioteca existente

**Evidencia:** capturas 1 y 4: botones grandes con borde brillante repetido, pantallas largas, falta estructura compacta; captura 3: tarjeta nueva con botones destacados rompiendo el patrón `⋮`. `QuestionEditorScreen` ya posee `EditorSectionCard`, radios de dificultad y espacios coherentes.

**Acción:** usar un único sistema visual para tarjetas y ejercicios: cabecera breve, secciones compactas, iconos secundarios discretos, acento neón solo para selección/acción principal, espaciado y tipografía consistentes, pantallas estrechas, teclado y animaciones; no introducir quinta pestaña.

### P2-02 · Etiqueta corta y clara para el ejercicio escrito

**Propuesta UX:** «**Enumerar**» (subtítulo al crear: «Escribe tantas respuestas correctas como recuerdes»). Mantener identificador interno `WRITTEN_MULTI` para compatibilidad; evitar «Respuesta escrita» porque puede confundirse con un Anki de una respuesta. Alternativa «Lista de respuestas» para menús con más espacio.

### P2-03 · La pantalla de práctica da una sensación ajena a los tests principales

**Evidencia:** dos chips internos «Escritos / Anki», baremo repetido en cada ejercicio, scroll de estado y botón «Siguiente» incluso con uno solo. Captura 2: marcadores superpuestos alrededor del extremo derecho, y la corrección requiere leer una lista duplicada.

**Acción:** configuración de sesión antes de practicar, usar baremo existente de test y plantillas, preservar teclado al añadir varias entradas, chips editables, «Comprobar» principal, colores de respuesta tras entrega y resumen compacto, acceso a una vista de revisión común.

### P2-04 · Accesibilidad y pruebas de UI ausentes

Se requieren descripciones de controles, targets ≥48dp, soporte teclado/lector de pantalla, zoom de imagen y alto contraste; tests de Compose/UI instrumentados. Las actuales pruebas unitarias (`StudyExerciseRulesTest`, `AnkiReviewEngineTest`, `StudyVisualImageFitTest`) cubren cálculo parcial, pero **no** demuestran que «Añadir Anki» inicie el editor apropiado, que el borrado de marcador funcione, que una foto rotada quede alineada o que un ZIP/restauración completo se revierta.

## C. Riesgos pendientes de confirmar con pruebas en dispositivo

1. Instalación sobre 1.8 + v3→v4 y posterior reinicio sin pérdida de número de temas, preguntas, respuestas, favoritos, etiquetas, grupos, plantillas, imágenes, intentos, cronómetro y planificación.
2. Reanudación de borrador después de llamada, rotación, proceso terminado por Android y salida accidental.
3. Selector de palabra/hueco con teclado Gboard, menú contextual de Android, Unicode/surrogates, acentos, emojis y cambios de posición en el texto.
4. Dibujo con dedo/estilográfica, borrador, zoom, captura o fotografía de distinta relación de aspecto, imagen EXIF rotada, marcadores superpuestos y cambio de tamaño de pantalla.
5. Examen completo solo escrito, solo test, solo tarjetas y mixto con timer/salida/resumen/notas.
6. Entrada repetida bajo equivalencias, alias ambiguos en dos soluciones y penalización editable; nunca otorgar 10 tras un error penalizado salvo si el baremo elegido permite 0 de penalización.
7. Importar/exportar 15 temas ZIP, reintentar, simular fallo a mitad; comparar antes/después de biblioteca, multimedia y estado personal.
8. Restaurar backup global de 1.8 en Beta 2 y backup Beta 1 en Beta 2; rollback explícito y rechazo de backup futuro por lector antiguo.
9. Mil tarjetas y miles de intentos para verificar rendimiento y ausencia de congelaciones.
10. Sincronización entre teléfonos con cambios simultáneos y adjuntos repetidos, no copiar/mezclar silenciosamente versiones incompatibles.

## D. Orden de ejecución propuesto para Beta 2 (sin crear otra release en esta auditoría)

1. **Rescate / migración / transacciones (P0):** impedir escritura sobre base incompatible; backup previo; restauración/ZIP por lote con rollback y manifiesto; tests de integridad. Hacer esta fase antes de pedir que se importen bancos reales.
2. **Motor común (P0):** nuevo contrato de ejercicio en sesión, modos mixtos, temporizador, guardar y reanudar, puntuación objetiva/manual, plantillas y selección por tipo; preservar preguntas históricas.
3. **Editor unificado por lenguaje visual, con flujos distintos (P1):** autoría Enumerar con autoalias y foto/lienzo, marcadores editables; autoría Tarjeta simplificada con fragmentos, selección de cuántos ocultar y solución debajo.
4. **Corrección y biblioteca (P1):** colores inline, números correctos/omitidos; tarjeta de lista con `⋮`; filtros tipo; contadores; eliminar botones de práctica redundantes.
5. **Portabilidad / repaso (P1):** importación ZIP robusta; estudio libre/programado separado; .apkg real básico/Cloze cuando pase pruebas en Anki Desktop y AnkiDroid; posponer variantes avanzadas no solicitadas.
6. **QA y publicación:** regresión Android real, escenarios de fallos, unit/instrumentation, release firmado misma app id, `versionCode` incrementado; pre-release solo tras PASS y backup verificable.

## E. Criterios de aceptación transversales

- **Cero datos perdidos:** comparar manifiestos (IDs, cantidad por tipo, hashes de imágenes, sesiones, ajustes) entre estado previo y migrado, y resultado de import/export/restore.
- **Cero omisiones silenciosas:** cada ZIP informa recuentos por tema/tipo; rechazar por defecto entradas futuras/no compatibles.
- **Corrección justa:** exactitud y equivalencias seguras, sin falsas coincidencias ni castigo doble por duplicados; puntuación reproducible y snapshot.
- **UI fiel:** vista de pregunta sin soluciones ni pistas; mostrar respuesta debajo sin borrar la pregunta; números solo al corregir, en lugares correctos.
- **Compatibilidad progresiva:** archivos antiguos funcionan; nueva simplificación no borra `FlashcardKind` ni notas ya creadas.
- **Coherencia de interfaz:** cuatro pestañas inferiores, tarjetas con menú de tres puntos, filtros integrados, acción principal claramente destacada, sin ruido visual.
- **No confundir métricas:** preguntas test, ejercicios Enumerar, tarjetas, intentos y revisiones cada uno con contadores significativos; la nota solo incluye ejercicios objetivos.

**Resultado:** exhaustivo a nivel de código estático y las capturas proporcionadas, **no equivalente a una prueba física completa ni a una revisión formal de seguridad de todo el sistema Android/Planario**. Cada sospecha que requiere reproducción se etiqueta como prueba pendiente. Las reparaciones de esta lista **no se han implementado en este documento**.
