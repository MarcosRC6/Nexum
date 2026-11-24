---
version: 1
slug: "ple-testario-ui-screen-dashboardscreen-kt-631c3297"
primary_target: "app/src/main/java/com/example/testario/ui/screen/DashboardScreen.kt"
related_targets: ["app/src/main/java/com/example/testario/ui/screen/TestScreen.kt","app/src/main/java/com/example/testario/ui/screen/Components.kt"]
---

# Surface brief: NEXUM Inicio, navegación y test (móvil + tablet)

Mode: Operate. Platform: android (Compose, Material 3), adaptive widths. Visual-only pass; no function changes.

## Audience and task
Quien estudia con preguntas abre la app varias veces al día: ve cuánto lleva hoy, empieza a estudiar o hace un test, y navega entre Inicio, Biblioteca, Test, Plan.

## Direction contract
THESIS: la app es un cuaderno de estudio, no un panel: una cifra grande y calmada de "hoy", superficies tonales sin marcos y un único acento de latón; rechaza la tarjeta con borde y el botón relleno de Android por defecto.
OWN-WORLD: tinta casi negra (0B0D0C, superficies 141917/1C2220), latón 0xFFD8B880 como único acento, verdigrís para acierto y terracota para fallo, serif para títulos y enunciados, sans ligera con cifras de ancho fijo para tiempos; formas de 14 a 26 dp, sin bordes ni sombras decorativas; iconos dibujados con un solo trazo.
STORY: el usuario entiende al instante cuánto ha estudiado hoy y cuánto falta, pulsa Iniciar estudio o entra a un test; en el test ve la pregunta como texto de lectura y las opciones como filas tonales.
FIRST VIEWPORT: Inicio móvil: cabecera NEXUM, cifra de tiempo de hoy a 56 sp en sans ligera con unidades pequeñas, barra de 24 tramos de latón, fila Iniciar estudio + Pausar, y la cápsula de navegación flotante abajo con el destino activo expandido; en tablet el carril lateral de 88 dp sustituye a la cápsula.
FORM: Cuaderno de latón aprobado por el usuario en el lienzo de diseño; seed: none (pinned) — "respecto al aspecto sí que me gusta".
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved
Sin capturas de la app real (no hay emulador en este entorno): la verificación visual la hace el usuario con el APK del CI.
