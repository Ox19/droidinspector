# DroidInspector

App Android (Kotlin + Jetpack Compose) que audita la **actividad de uso del propio dispositivo**: qué apps se usan, por cuánto tiempo y en qué horarios. Muestra el resultado en pantalla y lo exporta a PDF en la carpeta Descargas.

> Estado: en desarrollo temprano; todavía no hay funcionalidad de auditoría.

## Privacidad

- Todo se procesa **en el dispositivo**; la app no declara el permiso `INTERNET`.
- Requiere el acceso especial *Acceso a datos de uso*, que el usuario concede de forma explícita en Ajustes.
- El PDF es el único artefacto generado.

La arquitectura y sus límites están en [ARCHITECTURE.md](ARCHITECTURE.md).

## Requisitos

- Android 10 (API 29) o superior para ejecutar la app.
- Docker para compilar.

## Compilar y probar

El build corre dentro de un contenedor, no en la máquina de desarrollo: `settings.gradle.kts`
aborta cualquier invocación de Gradle fuera del contenedor o de CI.

```
docker compose run --rm prepare   # con red: resuelve dependencias y compila
docker compose run --rm verify    # sin red: lint, tests y APK
```

El APK queda en `app/build/outputs/apk/debug/`. Para probarlo en un emulador o
dispositivo: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Modo demostración

La pantalla de inicio incluye **"Ver reporte de demostración"**, que muestra un reporte con
**datos de muestra, no medidos** (editables en `ui/DemoData.kt`), para presentar cómo se ve la
app. La actividad de instalación/desinstalación de ejemplo aparece **solo en el reporte PDF** (no en pantalla) y **solo si la app objetivo está instalada** en el dispositivo.

**Importante:** la auditoría real **no** puede obtener desinstalaciones ni el historial de apps
ya borradas — Android no guarda ese dato para una app normal. El modo demostración sirve solo
para mostrar el formato; no debe presentarse como una medición del dispositivo.

## Contribuir

El desarrollo es *trunk-based*: ramas cortas hacia `main` mediante pull request. Todo cambio requiere CI en verde y la aprobación del dueño del repositorio.

## Seguridad

Para reportar una vulnerabilidad usa el [reporte privado](https://github.com/Ox19/DroidInspector/security/advisories/new); no abras un issue público.
