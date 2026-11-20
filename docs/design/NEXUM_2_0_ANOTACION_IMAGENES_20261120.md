# NEXUM 2.0 — Anotaciones editables sobre imágenes
Fecha: 20/11/2026 · Estado: desarrollo aislado de Beta 2

## Objetivo aprobado
Tocar una imagen de test (enunciado o explicación/corrección), enumeración, tarjeta Anki o ejercicio, abrir un menú contextual y poder dibujar sobre ella con **rotulador fino**, **subrayador translúcido** y **paleta circular de colores**. El usuario puede deshacer, rehacer, borrar trazos, guardar o cancelar. No destruir ni transformar jamás los bytes de la imagen original.

## Arquitectura
- Fuente: URI de imagen existente; persiste en almacenamiento privado `question_images/` mediante el sistema actual de NEXUM.
- Capa: `ImageAnnotations` serializable por referencia/posición de la imagen **en el propio objeto de contenido**; no por ruta ni nombre de fichero.
- Modelos: `Question.statementAnnotations`, `Question.correctionAnnotations`, `StudyExercise.baseAnnotations`, `FlashcardContent.frontAnnotations` / `backAnnotations`, todos con valor por defecto vacío y lectura compatible de JSON anterior.
- Cada trazo: ID estable, herramienta `PEN` / `HIGHLIGHTER`, color ARGB de paleta, anchura normalizada del lado más corto de la imagen, opacidad editable, coordenadas normalizadas (0–1), límites de tamaño. El editor no rasteriza al guardar.
- Un mismo archivo físico puede ser compartido por objetos distintos: sus anotaciones nunca se mezclan. Un duplicado conserva la forma visual y puede recibir nuevos IDs.
- Al cambiar/eliminar imagen, las anotaciones asociadas se descartan **con aviso explícito** cuando existan, nunca se aplican a una imagen diferente.

## Representación
- Usar siempre `ContentScale.Fit`, calculando su rectángulo real mediante `fitImageToBox` de `StudyVisualImage`: excluir las bandas vacías (letterboxing) de los gestos.
- Dibujar la foto y, encima, la capa con `Canvas` en el sistema de coordenadas de la imagen.
- Rotulador fino: opacidad alta, 0.003–0.012 del lado más corto.
- Subrayador: opacidad aproximada 0.25–0.42, ancho 0.02–0.07 del lado más corto.
- Colores mediante círculos accesibles, con borde violeta solo sobre el color seleccionado. No aplicar neón a iconos de atrás/guardar/borrar.
- Gestos de pluma/táctil/ratón: trazo continuo; deshacer/rehacer por trazo. La vista previa debe soportar ampliar/zoom posteriormente sin desalinear el contenido.

## Flujos
1. Biblioteca › editar pregunta, ejercicio o tarjeta › tocar imagen › **Ver / Editar anotaciones / Cambiar imagen / Eliminar**.
2. Editor a pantalla completa › rotulador, subrayador, 6 colores, grosor, deshacer, rehacer, limpiar con confirmación, Guardar / Cancelar. Nunca escribe en el fichero de foto.
3. En entrenamiento/examen: **solo lectura** del trazado; tocar para ampliar/ver, pero no permitir editar la base de conocimiento durante una sesión puntuable.
4. En enumeración con marcadores, el modo **Poner números** debe ser explícito y distinto del modo **Anotar**, para no confundir las coordenadas de soluciones con dibujo libre.

## Persistencia y compatibilidad
- Los campos serializados viajan automáticamente en JSON, ZIP por temas y backup global NEXUM; las imágenes originales siguen archivadas como hasta ahora.
- Migración aditiva de esquema v6 a v7 cuando la función esté integrada; copia inmutable del JSON de v6 previa al primer guardado; no se permite instalar v6 sobre una base v7 sin restauración verificada.
- Ninguna anotación se incorpora a `correctAnswerId`, al cálculo de la nota, al calendario Anki ni a los eventos de repaso.
- Los duplicados de ejercicio deben comparar el **contenido visual** de los trazos ignorando IDs aleatorios, preservando dibujos distintos.

## QA y criterios de aceptación
- Trazo a varios tamaños de imagen y distintas relaciones de aspecto, letterboxing; verificar el mismo punto tras rotación, ampliar, guardar, relanzar la app y restaurar.
- Rotulador/subrayador, opacidad, paleta de colores, presión y dedo, undo/redo y cancelación.
- Un archivo usado en dos preguntas no hereda trazos de la otra; copia/duplicación preserva la anotación apropiada.
- Cambiar foto con trazos requiere confirmar; Cancelar no escribe ni cambia los datos de usuario.
- Test enunciado visible antes de responder, solución visible solo después cuando así esté configurado; overlay no filtra marcadores de respuesta en modo Examen.
- Backup ZIP exportado e importado vuelve a mostrar mismos trazos, mismo número de fotos y sin diferencias en originales.
- QA de Android real imprescindible antes de Beta 2; pasar unit tests, debug, preview `.neon`, release, lint y seguridad.

**No fusionar ni publicar automáticamente.** Esta rama queda aislada de la reparación de datos y de la release oficial.
