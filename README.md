# DroidInspector

App Android (Kotlin + Jetpack Compose) que audita la **actividad de uso del propio dispositivo**: qué apps se usan, por cuánto tiempo y en qué horarios. Muestra el resultado en pantalla y lo exporta a PDF en la carpeta Descargas.

> Estado: en desarrollo temprano; todavía no hay funcionalidad de auditoría.

## Privacidad

- Todo se procesa **en el dispositivo**; la app no declara el permiso `INTERNET`.
- Requiere el acceso especial *Acceso a datos de uso*, que el usuario concede de forma explícita en Ajustes.
- El PDF es el único artefacto generado.

## Requisitos

- Android 10 (API 29) o superior.
- Android Studio con JDK 25 (lo resuelve Gradle) para compilar.

## Compilar y probar

```
./gradlew assembleDebug
./gradlew lintDebug testDebugUnitTest
```

## Contribuir

El desarrollo es *trunk-based*: ramas cortas hacia `main` mediante pull request. Todo cambio requiere CI en verde y la aprobación del dueño del repositorio.

## Seguridad

Para reportar una vulnerabilidad usa el [reporte privado](https://github.com/Ox19/DroidInspector/security/advisories/new); no abras un issue público.
