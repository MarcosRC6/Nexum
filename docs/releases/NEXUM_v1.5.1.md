# NEXUM 1.5.1 — Corrección de incidencias comunicadas el 16/11/2026

## Nearby Connections: error 8032 en Enviar y Recibir

El APK v1.5 tenía `ACCESS_WIFI_STATE` y `CHANGE_WIFI_STATE` limitados a API 32 con `android:maxSdkVersion`, por lo que Android 13+ no otorgaba los permisos. Nearby SDK 19.3.0 podía fallar con `8032: MISSING_PERMISSION_ACCESS_WIFI_STATE`.

Solución: declarar ambos permisos **normales** sin límite máximo. Se mantienen los permisos peligrosos de Bluetooth y Wi-Fi cercano solicitados en tiempo de ejecución y la aplicación no requiere acceso a ubicación en Android 13+.

## Plan: plegar listas vacías

Se habilitan las flechas de «Temas» y «Objetivos diarios» aun cuando no existan elementos. Al plegar se ocultan las explicaciones de estado vacío y la ayuda complementaria; siguen visibles título, estado, flecha y botón «Añadir». No cambia la gestión ni los datos de temas u objetivos.

## Validación

- Regression test para manifest Android (protege contra limitar nuevamente los permisos normales).
- GitHub Actions: testDebugUnitTest, Debug APK, Preview APK, compilación Release, Android Lint y seguridad de publicación.
- Smoke real de transferencia en los dos teléfonos **pendiente** hasta instalar la nueva APK.
- `versionName=1.5.1`, `versionCode=11` y certificado oficial permanente de NEXUM.
- Crear copia de seguridad completa antes de actualizar.
