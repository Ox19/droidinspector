# DroidInspector — Auditoría de actividad del usuario en Android

## Contexto
Proyecto recién creado (plantilla Compose "Empty Activity", sin lógica; paquete `com.example.droidinspector`). Objetivo: app que se instala en el dispositivo de un **cliente**, lee la **actividad de uso** (qué apps, cuánto tiempo, cuándo) y genera una auditoría **en pantalla** y en **PDF en Descargas**. No es repo git ni tiene tests reales.

## Hallazgos del proyecto que condicionan el diseño
- `minSdk = 36` (Android 16+): deja fuera casi todos los dispositivos de clientes. **Decidido: `minSdk = 29` (Android 10)**; `targetSdk`/`compileSdk` se quedan en 37. Razón: `MediaStore.Downloads` guarda el PDF sin permiso de almacenamiento y la API de eventos de uso es estable desde 29.
- Versiones antiguas de `core-ktx`, `lifecycle-runtime-ktx`, `activity-compose`: actualizar al empezar.
- Sin permisos declarados en el manifest.

## Fase 1 — Diseño (propuesta)

### Fuente de datos (API de Android, sin dependencias)
- `UsageStatsManager.queryEvents()` → sesiones reales por app (ACTIVITY_RESUMED/PAUSED). Base de todo.
- `UsageStatsManager.queryUsageStats()` → totales diarios por app (cruce de control).
- `PackageManager` → nombre e icono de cada app, instalada/sistema.
- Opcional fase 3: `NetworkStatsManager` (datos móviles/Wi‑Fi por app).
- Permiso: `PACKAGE_USAGE_STATS` (acceso especial: el cliente lo activa en Ajustes, la app lo guía). `QUERY_ALL_PACKAGES` para listar nombres (OK al ser sideload; **no apto Play Store**).
- **Sin permiso INTERNET**: todo local, argumento de confianza ante el cliente.

### Límite importante
El sistema solo retiene eventos finos ~7–10 días. Para auditar periodos más largos habría que guardar snapshots periódicos (WorkManager + Room) → **opcional, fase 3**; la fase 1 audita lo que el sistema retenga.

### Métricas del reporte
- Resumen: rango auditado, dispositivo/versión de Android, tiempo total de pantalla, apps distintas usadas.
- Top apps por tiempo y por nº de aperturas.
- Uso por día y por franja horaria (heatmap simple).
- Primera/última actividad del día; sesiones más largas.
- Apps instaladas sin uso en el periodo.

### Arquitectura (módulo único, capas simples)
- `data/` — `UsageStatsSource` (envuelve las APIs del sistema).
- `domain/` — `SessionBuilder` (eventos → sesiones) y `AuditAggregator` (sesiones → métricas). Lógica pura, testeable.
- `ui/` — Compose: pantalla de consentimiento/permiso → selector de rango → dashboard → botón "Exportar PDF". Estado en un `ViewModel`; navegación por estado, sin librería de navegación.
- `report/` — `PdfReportWriter` con `android.graphics.pdf.PdfDocument` (framework, sin librería) y guardado vía `MediaStore.Downloads` (no requiere permiso de almacenamiento en API 29+).
- Patrón: repositorio + capa de agregación pura (separa Android del cálculo → testeable).

### Privacidad (clientes)
- Pantalla de consentimiento explícito antes de leer nada; el PDF lleva fecha, rango y dispositivo.
- Datos solo en memoria; el PDF es el único artefacto. Sin logs de nombres de apps.
- Manifest: `allowBackup=false` para que no se copie a la nube.

## Aclaración minSdk
`minSdk` es un **piso, no una versión exacta**: con 29 la app corre en Android 10, 11, 12… hasta 16. Hoy eso cubre prácticamente todos los equipos activos. Alternativa si algún cliente tiene equipos aún más viejos: `minSdk = 26` (Android 8) con una rama de compatibilidad pequeña (en <29 el PDF se guarda con el selector del sistema o con `WRITE_EXTERNAL_STORAGE` limitado a API ≤28). Se mantiene **29** salvo que aparezca un cliente concreto que lo requiera.

## Documentación versionada en `docs/`
- `docs/PLAN.md`: copia de este plan, siempre actualizada (fuente de verdad del diseño).
- `docs/flow.excalidraw`: flujo de usuario/datos: consentimiento → permiso de acceso de uso → selección de rango → lectura de eventos → construcción de sesiones → agregación → dashboard → exportar PDF a Descargas.
- `docs/architecture.drawio`: capas `ui / domain / data / report`, interfaces (`UsageRepository`, `ReportExporter`), dirección de dependencias y fronteras con el sistema Android (`UsageStatsManager`, `PackageManager`, `MediaStore`).
Los diagramas se generan en la Fase 0 y se actualizan cuando cambie el diseño (una desviación se corrige o se documenta, no se deja).

## Arquitectura por capas (escalable)
Un solo módulo Gradle al inicio, pero con **paquetes por capa y dependencias en una sola dirección** (`ui → domain ← data`; `report` depende de `domain`). Así, si crece, cada capa se separa en módulo sin reescribir.
- `domain/` (Kotlin puro, **sin imports de Android**): modelos (`AppSession`, `AuditReport`), casos de uso (`BuildAuditReport`), interfaz `UsageRepository` definida aquí donde se consume.
- `data/`: implementa `UsageRepository` con `UsageStatsManager`/`PackageManager`. Cambiar la fuente (p. ej. snapshots en Room) = nueva implementación, sin tocar domain ni ui.
- `report/`: interfaz `ReportExporter` en domain; `PdfReportExporter` aquí. Añadir JSON/CSV = otra implementación.
- `ui/`: Compose + ViewModel; solo habla con casos de uso.
- Inyección manual por constructor (un `AppContainer`); **sin Hilt** hasta que el grafo lo justifique.
- Dependencias de ejemplo a evitar hoy: Room, Hilt, Navigation → quedan como "opcionales" en el checklist.

## Reglas: usuario (macbook) vs proyecto
- **Nivel usuario** (`~/.claude/CLAUDE.md`, ya existe): rol, seguridad, git, estilo (identificadores en inglés, comentarios en español), disciplina de dependencias. **No se duplica.**
- **Nivel proyecto** (`DroidInspector/.claude/CLAUDE.md`, versionado): solo lo específico y que no está arriba: propósito, arquitectura de capas y regla de dependencias, paquete, `minSdk=29`, "sin permiso INTERNET", "datos de uso = sensibles, nunca al repo", comandos (`./gradlew test`, `assembleDebug`), y la regla de abrir `checklist-local.md` antes y después de cada paso. Referencia al global con una línea; en conflicto manda el del proyecto (convenciones existentes).

## Estructura de `.claude/` del proyecto
```
DroidInspector/
├── checklist-local.md         # trazabilidad (ver abajo; versionado)
└── .claude/
    ├── CLAUDE.md              # reglas del proyecto (versionado)
    ├── settings.json          # permisos compartidos: allow ./gradlew, git status/diff/log; deny leer local.properties/keystores (versionado)
    ├── settings.local.json    # preferencias personales (en .gitignore)
    └── rules/                 # reglas por tema, cargadas solo cuando aplican
        ├── architecture.md    # capas y dirección de dependencias
        └── privacy.md         # manejo de datos de clientes
```
Se crea mínimo: `commands/` y `agents/` **no** se crean hasta que haya un flujo repetido que lo justifique.

## `checklist-local.md` (trazabilidad)
Un archivo en la raíz, por fases (0 Repo → 1 Diseño → 2 MVP → 3 Opcional → 4 Verificación). Cada ítem: `[ ] / [~] / [x]`, **archivo y sección tocados (con link)**, **evidencia** (salida de test, hash de commit), **fecha**. Al final, una **bitácora** cronológica de una línea por avance y una lista de **decisiones** (p. ej. minSdk=29). Regla de uso: antes de cada paso se anuncia qué archivo/sección se toca; después se actualiza el checklist y se abre el archivo en VS Code.
Decisión: se **versiona** pese al nombre "local", porque la trazabilidad debe vivir en GitHub. No contiene datos sensibles.

## Política de commits y GitHub
- **Todo cambio termina en commit y push**; nada queda solo en disco. Un commit por paso lógico, título de una línea, el hash va a la evidencia del checklist.
- Ramas: `main` (estable) ← `develop` ← `feature/*`; PR de feature a develop. Sin `--force`, sin `--no-verify`, sin `--amend`; `git add` por ruta.
- El commit del checklist va junto al del cambio que documenta.

## Escalabilidad (criterios fijos)
Capas con dependencias unidireccionales, interfaces en domain, lógica pura testeable, nuevas fuentes/exportadores como implementaciones adicionales, y todo "futuro" separado como opcional en el checklist. Los tests se añaden donde la lógica puede fallar en silencio (`SessionBuilder`, `AuditAggregator`).

## Fase 0 — Repositorio Git/GitHub (antes de escribir código)
Hoy no es repo git y no existe remoto. Punto de retorno primero: lo primero es versionar la plantilla limpia.
1. `git init` en la raíz con rama `main`; luego `develop` (flujo feature → develop → main, a confirmar).
2. Identidad: verificar `git config user.email` y fijar `user.name`/`user.email` **con `--local`** (proyecto de auditorías a clientes; no mezclar con personal).
3. Ya existe un `.gitignore` de la plantilla: **revisarlo y completarlo** antes del primer commit (sin duplicar). Por pedido, **incluirá una línea `.gitignore` a sí mismo**. Trade-off: el archivo no viaja a GitHub, así que otro clon (u otra máquina) no hereda las reglas y podría subir basura o secretos; mitigación: las reglas clave también se documentan en `CLAUDE.md` y el checklist. Debe ir antes del primer `git add` (si ya está trackeado, la línea no surte efecto). Asegurar: `build/`, `.claude/settings.local.json`, `.gradle/`, `.idea/`, `local.properties`, `*.jks`/`*.keystore`, `*.apk`/`*.aab`, y `*.pdf`/`*.json` de auditorías de prueba (datos de uso son sensibles; nunca al repo).
4. Crear `.claude/` (con `CLAUDE.md`), `checklist-local.md` y `docs/` (`PLAN.md`, `flow.excalidraw`, `architecture.drawio`) (según secciones de arriba) y hacer el primer commit con la plantilla tal cual más esos archivos (archivos agregados por ruta, sin `git add .`; revisar `git status` antes) → queda como punto de retorno.
5. Crear repo en GitHub **privado** (`gh repo create`, acción externa: confirmo cuenta/org, nombre y visibilidad contigo antes de ejecutarlo) y push de `main` y `develop`. Verificar que `gh auth status` corresponde a la cuenta correcta.
6. Firma de release: keystore fuera del repo, credenciales por variables/`local.properties`, jamás commiteadas.
Commits cortos, una línea; un commit por paso lógico del MVP.

## Fases
0. **Repo** (arriba) → plantilla versionada.
1. **Diseño** (esta) → aprobación.
2. **MVP**: permiso + sesiones + top apps + dashboard + PDF. Ajuste de `minSdk`/versiones.
3. **Opcional**: snapshots históricos (Room+WorkManager), red por app, categorías.
4. **Verificación**: ver abajo.

## Dependencias nuevas
Solo `lifecycle-viewmodel-compose` (estado de UI sobrevive a rotación; ya es del ecosistema AndroidX). Nada más en el MVP. Tests: unit tests solo para `SessionBuilder`/`AuditAggregator` (la lógica que puede fallar en silencio); sin linters/CI por ahora.

## Archivos críticos
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (minSdk, versiones)
- `app/src/main/AndroidManifest.xml` (permisos, allowBackup)
- `app/src/main/java/com/example/droidinspector/MainActivity.kt` (se reemplaza `Greeting`)
- Nuevos: `data/UsageStatsSource.kt`, `domain/SessionBuilder.kt`, `domain/AuditAggregator.kt`, `report/PdfReportWriter.kt`, `ui/*`

## Verificación
- Unit tests de `SessionBuilder` con eventos sintéticos (cruce de medianoche, app sin PAUSED, solapes).
- Emulador/dispositivo real: conceder acceso de uso, generar actividad conocida (abrir 2–3 apps tiempos medidos) y comparar contra Ajustes → Bienestar digital.
- Generar PDF y abrirlo desde Descargas; confirmar que no hay permiso INTERNET en el APK (`aapt dump permissions`).
- Probar en Android 10 y en la versión más reciente.
