# NEXUM — especificación propuesta: ejercicios, ZIP, imágenes, backups y sincronización
Fecha: 19/11/2026

> **ESTADO: DISEÑO, NO IMPLEMENTADO.** Este documento amplía [el acuerdo funcional de ejercicios](NEXUM_EJERCICIOS_RESPUESTA_MULTIPLE_DISENO_20261119.md). La especificación queda propuesta para implementación tras cerrar las decisiones funcionales. El siguiente formato es **v1 del paquete propuesto**, no la versión de la aplicación ni un formato ya aceptado por NEXUM.

## 1. Realidad del repositorio sobre la que se diseña

Revisión del código de `main` (SHA `9ca83d6557ba85e445820f49d2225c66caac48a9`, 19/11/2026):
- `CoreModels.kt`: `DatabaseSchema.CURRENT_VERSION = 3`. `TestarioDatabase.topics` contiene `Topic.questions: List<Question>`; `TopicGroup.topicIds` agrupa referencias a temas y **un tema puede pertenecer a varios grupos**. `Question` modela las alternativas de un test, con `isFlashcard` histórico e imágenes `explanationImageUri` y `correctionImageUri`.
- `ExportManager.kt` empaqueta `testario_database.json` y `question_images/`; la exportación de biblioteca retira estadísticas y favoritos. `UnifiedDataManager.kt` exporta/restaura un backup global cuyo manifiesto usa `format=testario_planario_global`, `version=1`.
- `ImportManager.kt` admite ZIP con una base de datos o múltiples archivos de temas JSON, valida los JSON antes de modificarlos y almacena las imágenes primero en caché. La acción `MERGE` trabaja **por tema de destino**, identificándolo hoy por nombre, con huellas de pregunta clásica.
- `ImageManager.kt` persiste archivos en `question_images`; puede copiar bytes de una imagen original a un destino `.jpg` **sin transcodificar el contenido**, por lo que el nuevo sistema debe preservar formato/MIME real.
- Los lectores antiguos usan `ignoreUnknownKeys`; si se incrustan formatos nuevos en un JSON que parecen saber leer podrían **omitir contenido nuevo silenciosamente**. Evitarlo con una identidad de paquete inequívoca y una estructura de `library.json` que no se confunda con `TestarioDatabase`.

## 2. Modelo interno evolutivo y compatibilidad

1. **No eliminar ni mutar destructivamente `Topic.questions`.** Conservar preguntas antiguas, historial, plantillas, correctAnswerId, isFlashcard y las dos imágenes independientes del test clásico.
2. Propuesta de migración interna: añadir a `Topic` una colección `exercises: List<Exercise>` con valor por defecto vacío, y una familia tipada `Exercise` para nuevos formatos `WRITTEN_MULTI` y `ANKI_CARD`. En interfaz, presentar la unión lógica `questions + exercises` como **Biblioteca común**; no duplicar ninguna pregunta migrando su registro.
3. El `Exercise` contiene ID permanente, tipo, enunciado, dificultad, fecha de creación/modificación, `revision`, ID del tema contenedor, datos propios del tipo y referencias a assets locales. La persistencia real debe usar el contenedor del tema como fuente de verdad o validar estrictamente cualquier `topicId` redundante.
4. `WRITTEN_MULTI`: soluciones canónicas con IDs permanentes, texto principal, alias explícitos, y cero o más marcadores por solución. Parámetros visuales opcionales y motor de puntuación configurable **en la sesión**. No mezclar respuesta textual con marcador: renombrar una solución no mueve ni rompe sus marcadores.
5. `ANKI_CARD` (nombre conceptual por ahora): **nota de contenido** versionada con `cardSubtype` (`BASIC`, `REVERSED`, `CLOZE`, `TYPE_ANSWER`, `IMAGE_OCCLUSION`), campos propios por subtipo (pregunta/respuesta, texto con huecos estructurados, solución escrita, máscaras sobre imagen); **tarjetas derivadas** con IDs estables por dirección, hueco o máscara y planificación de repetición espaciada independiente. Se almacena el texto original completo de Cloze, nunca solo un resultado oculto; las imágenes originales y máscaras son assets/capas separadas. El renderizado mantiene el enunciado arriba y agrega solución debajo al revelar; los huecos y máscaras además se destapan en su lugar. Los datos privados del planificador (próxima fecha, historial, intervalo) van en estado personal, no se comparten al exportar material.
6. Nueva migración de esquema cuando se implemente (posterior a v3). Abrir bases antiguas añade colecciones vacías sin tocar preguntas, estadísticas, grupos ni sesiones. Un formato más reciente no reconocido se rechaza con mensaje; no convertirlo a banco vacío.
7. **Semántica de grupos:** hoy un grupo agrupa **temas**, no ejercicios individualizados. En el editor puede elegirse un tema y visualizar/seleccionar sus grupos; para «asignar a un grupo» el usuario seleccionará un tema existente de dicho grupo o añadirá explícitamente el tema al grupo. Un mismo ejercicio no debe duplicarse por pertenecer su tema a varios grupos. Si se quiere pertenencia por ejercicio independiente de su tema, requerirá un cambio deliberado del modelo y su correspondiente UX/migración.

## Decisión funcional de formatos de archivo (19/11/2026)

**Aprobado por el usuario:** interfaz simplificada con **exportación únicamente ZIP** para cualquier biblioteca/tema/copia de seguridad; **importación automática ZIP y JSON** sin obligar a elegir el formato por separado. Los ZIP son obligatorios para material que incluya imágenes y marcadores, porque permiten transportar todos los assets y comprobar su integridad.
- Seguir aceptando bancos clásicos en JSON como **entrada**; conservar TXT/CSV de legado mediante detección automática si el lector existente los soporta, aunque no sean opciones destacadas ni formatos nuevos de exportación.
- Si se selecciona un JSON cuyo contenido refiere imágenes externas ausentes, informar claramente de que **no es autocontenido**; rechazar el ejercicio que dependa de esos ficheros o exigir un ZIP completo, sin importar un resultado mutilado.
- Un ZIP puede contener varios JSON antiguos de temas; la importación los cuenta y verifica **todos**. Para contenido nuevo se prefiere manifiesto de `nexum_library_bundle` y `library.json`, manteniendo el antiguo lector para los paquetes previos.
- Las exportaciones clásicas en JSON dejan de ofrecerse como opción pública en la nueva interfaz; **el lector JSON permanece**. No confundir esto con borrar clases de modelo, invalidar backups antiguos o eliminar de golpe rutas usadas internamente antes de migrarlas y probarlas.
- La exportación de temas sin imágenes sigue entregándose como ZIP. **No alterar ni vaciar la biblioteca** al no reconocer una entrada.
- **Estado:** decisión de UX acordada; implementación pendiente.

### Excepción específica: mazos nativos de Anki (.apkg)

- La decisión «exportar NEXUM solo a ZIP» se refiere a **exportaciones generales, bibliotecas mixtas y backups propios**. Por solicitud posterior del usuario, **añadir una opción explícita «Exportar para Anki (.apkg)»** al trabajar con tarjetas Anki. No es exportación JSON ni un ZIP de NEXUM renombrado: su estructura debe cumplir el formato real de Anki y validarse en Anki Desktop/AnkiDroid.
- **Importar** paquetes Anki `.apkg` (además de los ZIP/JSON ya previstos). El detector los identifica por contenido y usa un lector específico de notas, tarjetas, modelos, medios y mazos; **no** tratar todo `.apkg` como un paquete genérico de preguntas NEXUM. Reportar tipos no representables sin eliminar material silenciosamente.
- `.colpkg` corresponde a **colección completa de Anki**, distinta del paquete de mazo. Considerar importación avanzada en una fase posterior y con previsualización/aislamiento, sin reemplazar el banco ni convertirlo en restauración NEXUM.
- La correspondencia grupos/temas NEXUM → mazos/etiquetas Anki debe evitar duplicación: la pertenencia de un tema a varios grupos no implica exportar varias copias de una tarjeta.
- Los paquetes `.apkg` son **intercambio de contenido**; el backup global NEXUM incluye el progreso personal. No prometer traducción exacta de historiales/FSRS de Anki a la repetición espaciada opcional de NEXUM.
- Detalle de compatibilidad en [NEXUM_TARJETAS_ANKI_REPASO_20261119.md](NEXUM_TARJETAS_ANKI_REPASO_20261119.md).

## 3. Nuevo ZIP de biblioteca para compartir (`nexum_library_bundle` v1)

Estructura:

```text
nexum_biblioteca_YYYYMMDD.zip
├── manifest.json
├── library.json
└── assets/
    ├── <sha256>.png
    └── <sha256>.jpg
```

- `manifest.json`: `format: "nexum_library_bundle"`, `schemaVersion: 1`, fecha de creación, versión mínima lectora, recuentos por tipo y por tema, tabla de assets (nombre relativo, MIME, tamaño, SHA-256, anchura, altura), sumas de integridad. La app **no** identifica una copia de seguridad por el nombre del archivo.
- `library.json`: `schemaVersion: 1`, `collections: { topics: [...], groups: [...] }`. Dentro de un tema, las preguntas tipo test se guardan en `legacyQuestions` (equivalentes al modelo actual sin datos personales) y los formatos nuevos en `exercises`. Nunca guardar aquí rutas internas absolutas ni URI temporales de Android.
- La clave raíz `collections` es deliberada: el importador actual de JSON de temas no debe poder tratar `library.json` como un `TestarioDatabase` y cargar una biblioteca **vacía o parcial**. Se construirá un lector específico que reconozca primero el manifiesto. Los paquetes nuevos no contendrán `testario_database.json`.
- `assets/<sha256>.<ext>`: originales legibles identificados por huella de contenido. Reutilizar un mismo archivo si es compartido por varias preguntas. Los nombres internos reales de Android se asignarán en importación; nunca usar el nombre de archivo del ZIP como ruta final sin validación.
- Ningún paquete de **biblioteca compartida** incorpora ajustes privados, rachas, estadísticas, historial de sesiones, respuestas del alumno, calendario personal Anki, cronómetro ni tokens de sincronización.
- Para compatibilidad, seguir **ofreciendo una exportación clásica en ZIP** de tests cuando el usuario lo solicite y el material sea enteramente compatible; **no ofrecer JSON como exportación en la interfaz nueva**, ni convertir silenciosamente ejercicios escritos o tarjetas nuevas a tests. En una exportación clásica que excluya formatos, mostrar recuentos exactos antes de continuar.

### Fragmento ilustrativo de `library.json` (no constituye un ZIP listo para importar)

```json
{
  "schemaVersion": 1,
  "collections": {
    "topics": [
      {
        "id": "topic-ejemplo",
        "name": "Ejercicios de práctica",
        "legacyQuestions": [],
        "exercises": [
          {
            "id": "exercise-ejemplo",
            "revision": 1,
            "type": "WRITTEN_MULTI",
            "prompt": "Enumera las conexiones de la vía representada.",
            "baseImageAssetId": "asset-ejemplo",
            "orientation": null,
            "solutions": [
              {
                "id": "solution-1",
                "name": "Respuesta de ejemplo",
                "aliases": ["Nombre alternativo de ejemplo"],
                "markers": [
                  {"x": 0.27, "y": 0.40, "order": 1, "side": "ABOVE"}
                ]
              }
            ]
          }
        ]
      }
    ],
    "groups": []
  }
}
```

La imagen `asset-ejemplo` **debe** tener su entrada real en `manifest.json` y su fichero correspondiente en `assets/`. Esta sección ilustra el vínculo, no datos sobre calles reales.

## 4. Reglas de imágenes y marcadores

- **Una imagen base para el enunciado y una capa de corrección separada** para `WRITTEN_MULTI`. No exportar una imagen ya anotada que revele la solución como si fuera la base; en modo Examen ocultar totalmente la capa, incluidos los marcadores si su sola existencia da pistas.
- `Importar imagen`: conservar bytes y MIME real (PNG/JPEG/WebP si el dispositivo/decodificador es compatible), con extensión coherente y dimensiones detectadas, corrección definida de orientación EXIF. Nunca etiquetar arbitrariamente bytes PNG/WebP como JPEG.
- `Crear esquema`: almacenar una representación estructurada editable de una línea recta, color/posición y **flecha opcional**; generar una imagen base neutral solo para renderizar o exportar. No dibujar calles laterales, cruces o números en el enunciado. La capa de solución contiene los puntos creados por el autor.
- Cada marcador referencia `solutionId`, tiene `x,y` normalizados a `[0,1]` en el sistema de coordenadas **de la imagen orientada**, orden espacial opcional y lado respecto de la representación o sentido definido. `order` es metadato de corrección; no se usa para dar pistas durante el examen. Múltiples marcadores para una misma solución cuentan como un solo ítem salvo enunciado/rúbrica explícita.
- Marcar como **invalidación previa a guardar**: puntos huérfanos, soluciones vacías, alias normalizados que apuntan a soluciones diferentes, coordenadas fuera de `[0,1]`, imagen ausente, tamaño 0, formato no decodificable o capas sin base. No corregir automáticamente la cartografía.
- Cuando se edita, gira o recorta físicamente una imagen, recalcular las coordenadas desde la matriz de transformación o confirmar/rehacer marcadores; **nunca** conservar puntos desplazados sin avisar. Zoom/pan UI no modifica las coordenadas persistidas.
- Los archivos con hashes iguales pueden compartir contenido; borrar/mover una pregunta no debe eliminar la imagen si sigue referenciada por otros ejercicios. Limpiar imágenes huérfanas solo con barrido de referencias y política conservadora/recuperable.

## 5. Importación segura y fiable de lotes

1. **Detectar tipo por contenido**: distinguir copia global, backup antiguo, ZIP clásico, ZIP `nexum_library_bundle` y JSON/TXT/CSV clásico. Si manifiesto de formato nuevo no soportado: **rechazar con explicación**, no entrar en el importador genérico.
2. **Previsualizar primero**: número de temas, grupos, tests, tarjetas, ejercicios escritos, respuestas, imágenes, nuevos, idénticos, conflictos, fallos y espacio estimado. Un ZIP con 15 temas **no puede concluir con éxito si solo importó 4**; mostrar exactamente los 15 y su resultado.
3. Descomprimir en **zona temporal aislada** con límites por fichero, totales y cantidad de entradas, detección de ZIP bomb, duplicados de entrada, Zip Slip, rutas absolutas, enlaces simbólicos y nombres maliciosos; verificar MIME real, decodificación segura, dimensiones y SHA-256 antes de escribir el banco.
4. Validar **todos los ejercicios y sus vínculos**: esquema y versión, IDs no repetidos, alias compatibles, `solutionId` existentes, marcadores, integridad de grupos/temas, imágenes y tipos admitidos; no interpretar campos requeridos desconocidos como vacíos.
5. **Plan de fusión antes de aplicar**: por defecto preservar sin sobrescritura. Reglas: `mismo ID + misma revisión + mismo contenido` → omitir; `mismo ID + contenido diferente` → conflicto visible (conservar actual, importar copia con nuevos IDs o elegir versión tras vista previa); `ID diferente + huella semántica equivalente en el tema destino` → marcar posible duplicado, no eliminar conocimiento distinto por parecido. Nombre de tema igual no basta para identificarlo: preguntar fusionar/renombrar/crear.
6. Si hay que regenerar IDs para **copia importada**, remapear **de manera transaccional** IDs de temas, ejercicios, soluciones, marcadores, grupos y referencias de recursos. El tema puede formar parte de varios grupos; no duplicar los ejercicios por ese motivo.
7. **Commit atómico o recuperable**: staging + diario de transacción para metadatos/imágenes, comprobación final, y rollback completo si falla la escritura/validación. No confirmar éxito parcial ni perder el estado anterior de la biblioteca. Para archivos inválidos, ofrecer cancelar el lote entero o reimportar únicamente entradas previamente validadas mediante selección explícita; nunca saltarse errores sin avisar.
8. **Informe final verificable** por tema y por tipo: importados, omitidos por duplicado, conflictos, descartados por decisión del usuario, errores de validación, imágenes y sumas de control. Un reintento del mismo ZIP con la misma decisión debe ser **idempotente**.
9. **No importar estadísticas personales** desde ZIP de biblioteca. Los datos personales solo entran en operación explícita de restauración/sincronización, claramente diferenciada de «Importar ejercicios».

## 6. Copias globales completas y restauración

- La copia global actual usa `format=testario_planario_global` y `version=1`. Cuando el nuevo modelo esté incorporado, proponer **manifest global v2**, sin confundirlo con `schemaVersion=1` del paquete de biblioteca.
- La copia global v2 incluye contenidos clásicos + nuevos, todos los assets referenciados, organización por grupos, plantillas, estadísticas e historial, borradores/sesiones activas recuperables, configuración Anki + programación por tarjeta, Planario, reloj/temporizador y opciones de NEXUM que deban persistir.
- **La restauración es distinta de la fusión**: mostrar advertencia, fecha, dispositivos/versiones y lo que reemplazará. Validar todo antes de modificar, preparar copia previa recuperable, conservar datos anteriores ante fallo, y restituir imágenes + DB + estado de Planario/ajustes de forma coherente. No usar secuencias en que una parte se escriba correctamente y otra falle sin compensación.
- Los backups v1 existentes continúan siendo **restaurables por la nueva versión** (migrando sus modelos); no reescribir los backups originales. Si una versión antigua de la app abre un backup v2 no entendido, debe rechazarlo con mensaje, no vaciar campos desconocidos.
- Al **compartir** un ZIP de biblioteca no incluir el calendario Anki personal; en **backup personal completo** sí conservarlo. Historial y cuotas de sesion se respaldan como datos personales, nunca en el contenido didáctico compartido.

## 7. Sincronización: propuesta de arquitectura y conflictos

- Sincronización es un **proceso de conciliación**, no importar siempre un backup sobre el otro. Guardar IDs estables, `revision`/reloj lógico, fecha, identidad del dispositivo que modificó, hash de contenido, IDs de evento `attemptId` y marcas de borrado (tombstones) para propagación de eliminaciones.
- Descargar los deltas en staging, validar assets y datos, comparar versiones. Si dos dispositivos modifican **campos diferentes** de un mismo ejercicio y hay base común demostrable, ofrecer merge seguro; si editan la misma solución/imagen o la misma rúbrica, presentar conflicto con alternativas «conservar móvil», «conservar otro dispositivo», «guardar ambas copias». **Nunca elegir por última hora de reloj local** como único criterio.
- Los eventos de respuesta/repaso deben ser idempotentes por ID. No sumar estadísticas dos veces al reintentar; no multiplicar intervalos de tarjetas Anki al reproducir una misma revisión. Calendario Anki sujeto a reglas de conciliación específicas cuando ambos dispositivos repasan sin conexión.
- Solo transferir imágenes referenciadas faltantes o cuyo hash no coincide; nunca compartir rutas absolutas locales. Reintentos y cortes de conexión deben recuperar sin dejar duplicados; cifrado y controles de autorización dependen del medio de sincronización adoptado.
- **No se promete nube ni sincronización automática entre cuentas** en esta especificación: el proveedor, modo de transporte y UX serán decisiones posteriores. El diseño deja el almacenamiento preparado para sincronización por archivo o conectores futuros.

## 8. Pruebas de aceptación obligatorias antes de publicar

1. Migrar una instalación con temas, grupos, plantillas, tests clásicos, flashcards históricas, exámenes e imágenes; las preguntas antiguas conservan exactamente sus IDs y sus respuestas, sin cambio de estadísticas.
2. Exportar/importar un tema **mixto** con tests, **cinco variedades de tarjetas Anki** (básica, bidireccional, huecos, respuesta escrita y ocultación de imagen), 3 ejercicios escritos (algunos con imágenes y otros sin ellas); notas, IDs de tarjetas derivadas, alias, máscaras, posiciones y referencias intactos. Al revelar, pregunta visible arriba y respuesta debajo; en Cloze/máscara, hueco/área revelado en su sitio.
3. Crear esquema de una **línea sin pistas**, colocar 15 marcadores en corrección, guardar, cerrar/reabrir, exportar, importar y renderizar con pantalla distinta sin desplazamientos.
4. Importar un ZIP con 15 JSON de temas; reportar **15/15** cuando todos son válidos, o mostrar errores concretos y **ningún éxito ficticio** cuando no lo son.
5. Dos archivos con mismo nombre distinto contenido; mismo ID con modificaciones diferentes; mismo contenido bajo IDs diferentes; mismo tema en varios grupos; duplicados reales vs preguntas parecidas con dato distinto.
6. Eliminar/mover/duplicar ejercicios con assets compartidos sin borrar imágenes utilizadas en otros; comprobar referencias antes/después del backup.
7. ZIP malicioso (traversal, duplicados, tamaño descomprimido desproporcionado, JSON truncado, hashes falsos, imágenes no decodificables, marcadores fuera de rango, referencias huérfanas): rechazar y verificar que no cambió **ningún** dato anterior.
8. Cortar una restauración y una sincronización antes de finalizar, forzar reintentos; mismo resultado final que un único intento completo o rollback consistente, sin eventos de examen dobles.
9. Importar JSON/ZIP antiguos de NEXUM; restaurar backup global v1 en versión nueva; negar expresamente paquetes de formato nuevo no compatible en lectores antiguos, sin absorción parcial.
10. Exportar ZIP didáctico y verificar que **no incluye** datos de progreso, cookies, claves, rutas absolutas, cronómetro ni calendario personal.
11. Verificar que la **repetición espaciada opcional** no crea preguntas/tarjetas automáticamente y que su calendario sí sobrevive a una copia global.
12. Hacer validación Android: unit tests, lint, build, auditoría de seguridad, instalación y smoke real de importación/exportación en dispositivo.

## 9. Estado y cuestiones funcionales que quedan abiertas

**Propuesta de diseño preparada, sin código desplegado.** Esta especificación no autoriza implantar automáticamente el nuevo formato, ni fusionar/publicar PRs existentes.

Quedan por precisar antes de ejecutar: experiencia detallada de asignar una tarjeta a un grupo cuando los grupos agrupan temas, criterios visuales del lienzo «Crear esquema», formato final Kotlin/JSON y ruta de migración tras v3, política UX de conflictos de importación, y medio de sincronización real (archivo, red local o servicio conectado). Ningún punto pendiente debe resolverse sacrificando datos o alterando pruebas de examen.
