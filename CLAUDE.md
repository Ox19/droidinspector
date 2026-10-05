# DroidInspector

App Android (Kotlin + Compose) que se instala en el dispositivo de un cliente, lee su actividad de uso (apps, tiempos, horarios) y genera una auditoría en pantalla y en PDF (carpeta Descargas). Complementa `~/.claude/CLAUDE.md`; ante conflicto manda este archivo.

## Fuentes de verdad
- Diseño: `docs/PLAN.md` · flujo: `docs/flow.excalidraw` · arquitectura: `docs/architecture.drawio`
- Trazabilidad: `checklist-local.md` — antes de cada paso, di qué archivo/sección tocas; después, actualiza estado, evidencia (hash/test), fecha y bitácora.

## Reglas del proyecto
- Paquete `com.example.droidinspector`; `minSdk=29`, `targetSdk/compileSdk=37`.
- Capas con dependencias en una sola dirección: `ui → domain ← data`, `report → domain`. `domain/` es Kotlin puro (sin imports de Android); las interfaces viven allí.
- **Sin permiso `INTERNET`.** Los datos de uso son sensibles: solo en memoria, el PDF es el único artefacto, nunca al repo ni a logs.
- Dependencias nuevas solo con justificación escrita en el checklist (ver global).
- Todo cambio termina en commit y push; ramas `main` ← `develop` ← `feature/*`. `git add` por ruta.
- `.gitignore` se ignora a sí mismo (no viaja al remoto): si se clona en otra máquina, recrearlo con las reglas del checklist (build/, .idea/, local.properties, `*.jks`, `*.keystore`, `*.apk`, `*.aab`, `*.pdf`, `audits/`, `.claude/settings.local.json`).

## Comandos
- `./gradlew testDebugUnitTest` · `./gradlew assembleDebug`

Reglas por tema en `.claude/rules/`.
