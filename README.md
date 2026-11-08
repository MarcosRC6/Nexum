# NEXUM — Android

NEXUM integra **Testario** (biblioteca y tests) y **Planario** (planificación y objetivos diarios) en una aplicación Android. La aplicación permite crear y editar preguntas, realizar entrenamientos y exámenes, registrar sesiones de estudio y vincular tests a objetivos diarios.

## Estado de versiones

- Versión de producción del código en este repositorio antes del endurecimiento de seguridad: **1.2** (`versionCode 7`).
- Identificador Android oficial: `com.example.estudario`.
- La APK oficial utiliza una clave de firma privada que **no está incluida en el repositorio**.
- Las versiones oficiales se distribuyen desde [GitHub Releases](https://github.com/MarcosRC6/Nexum/releases); el actualizador integrado consulta la última Release publicada.

## Compilar

Requisitos: Android SDK, JDK 17 y el wrapper Gradle incluido.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Para Windows, usar `gradlew.bat` con los mismos argumentos. La compilación debug **no** lleva la firma oficial y no puede instalarse como actualización sobre la APK de producción sin desinstalarla. Nunca utilices una APK debug para reemplazar la instalación de producción con datos reales.

Las APK oficiales firmadas se generan únicamente desde el workflow [NEXUM - APK oficial firmada](.github/workflows/nexum-official-release.yml) en `main`, con secretos almacenados en GitHub Actions. Publicar una nueva Release exige `workflow_dispatch` y `publish_release=true`.

## Privacidad y seguridad

- El contenido de estudio que añade el usuario se guarda localmente en su instalación; **no se versionan** los datos personales, exportaciones ZIP ni imágenes importadas por los usuarios.
- Los secretos de firma y credenciales privadas nunca deben subirse al repositorio ni incluirse en Pull Requests, comentarios o registros.
- El workflow [publication security gate](.github/workflows/nexum-publication-security.yml) revisa los archivos versionados y el historial, pero no garantiza detectar todos los secretos posibles.
- Instrucciones para comunicar vulnerabilidades: [SECURITY.md](SECURITY.md).

## Condiciones de uso del código

Este repositorio se pone a disposición pública para consulta y transparencia. **No contiene una licencia de software de terceros concedida por el propietario**; publicar el código no equivale a conceder automáticamente derechos de redistribución o reutilización. Las dependencias mantienen sus propias licencias. Si se decide utilizar una licencia abierta, se añadirá mediante una decisión expresa del titular.

El archivo [LEEME_NEXUM_1.3.txt](LEEME_NEXUM_1.3.txt) procede de una fase anterior del desarrollo y **no representa la versión de producción actual**.
