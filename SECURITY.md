# Seguridad de NEXUM

## Notificar un problema

Si detectas una vulnerabilidad, una filtración de datos o una credencial, **no publiques el hallazgo en una incidencia o Pull Request públicos**.

Utiliza la opción de informe privado de vulnerabilidades (*Private vulnerability reporting*) del repositorio si está habilitada. Si no aparece esa opción, contacta directamente con el propietario del repositorio por un canal privado antes de divulgar detalles.

No adjuntes claves, contraseñas, copias de seguridad, datos personales ni trazas sin revisar en comentarios públicos.

## Modelo de distribución

- Solo las compilaciones oficiales de `main`, firmadas con la clave permanente, pueden actualizar instalaciones de producción.
- La clave y las contraseñas de firma se gestionan con secretos de GitHub Actions. No deben aparecer en archivos, historial, logs ni artefactos.
- El workflow de firma solo se ejecuta en `main`, y la publicación de una Release requiere autorización manual.
- Las Pull Requests no deben poder publicar APK oficiales ni acceder a secretos de firma.
- Los datos de estudio, resultados y copias de seguridad pertenecen al usuario y no deben formar parte del repositorio.

## Controles previos a la publicación

```sh
python3 scripts/check_publication_safety.py
```

Este control automatizado examina nombres de archivos Git, archivos de texto versionados y adiciones históricas. No sustituye la revisión de archivos binarios, metadatos EXIF, permisos del repositorio, logs antiguos de Actions y formatos de credenciales no reconocidos.

Si se detecta un secreto comprometido, debe revocarse o rotarse de forma segura. Borrarlo únicamente del commit más reciente **no elimina** su exposición en el historial público.
