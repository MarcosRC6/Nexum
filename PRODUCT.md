# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users
Cualquier persona que estudie mucho material con preguntas: la usa su autor para preparar oposiciones, pero sirve igual para carreras, certificaciones o idiomas. Estudia en móvil y en tablet a partes iguales, en sesiones largas y repetidas.

## Product Purpose
NEXUM integra Testario (biblioteca de temas y preguntas, tests, repaso de fallos, estadísticas) y Planario (plan, calendario, objetivo diario y cronómetro de estudio) en una app Android local, sin cuenta. El éxito es que el usuario abra la app, vea cuánto lleva hoy y empiece a estudiar o a hacer un test sin pensar.

## Positioning
Una sola app que junta el banco de preguntas y el plan de estudio, con los datos guardados en el dispositivo y copias de seguridad propias.

## Operating Context
Se abre varias veces al día, a menudo con poco tiempo. Los tests y el cronómetro se usan con concentración. El contenido (temas, preguntas, imágenes) lo crea o importa el propio usuario.

## Capabilities and Constraints
- Kotlin + Jetpack Compose + Material 3; paquetes `com.example.testario` y `com.example.planario`; el applicationId `com.example.estudario` no puede cambiar.
- Este rediseño es solo visual: no se añaden ni quitan funciones ni se cambian textos de acción.
- Copia de seguridad, importación de temas, widgets de pantalla de inicio y sincronización con un NEXUM cercano deben seguir funcionando.
- Versión estable de referencia: 2.0; los rediseños grandes viven en ramas no oficiales.

## Brand Commitments
Nombre NEXUM; logotipo de sello con una N en inglete (`NexumLogoMark`); el usuario aprobó el concepto "Cuaderno de latón" (tinta casi negra con latón como acento) tras verlo en un lienzo de diseño, con versión móvil, tablet y widgets.

## Evidence on Hand
Lienzo de diseño publicado como artefacto (móvil: Inicio, Biblioteca, Test, Plan, sistema visual; tablet: Inicio, Biblioteca, Test, Plan; widgets). No hay capturas de la app real ni emulador disponible en este entorno.

## Product Principles
- La app responde primero a "¿cuánto llevo hoy y qué toca ahora?".
- Estudiar y hacer tests exige calma visual: poco borde, poco adorno, cifras legibles.
- Móvil y tablet son ciudadanos de primera, no una ampliación del otro.
- Ningún cambio visual puede poner en riesgo los datos del usuario.

## Accessibility & Inclusion
Contraste mínimo 4,5:1 en texto; objetivos táctiles de 48 dp; el texto sigue el tamaño de fuente del sistema.
