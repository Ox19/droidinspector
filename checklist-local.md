# Checklist de trazabilidad — DroidInspector

Estados: `[ ]` pendiente · `[~]` en curso · `[x]` hecho. Cada ítem: archivo/sección, evidencia, fecha.

## Decisiones
- 2026-10-05 · `minSdk=29` (piso, no versión exacta): `MediaStore.Downloads` sin permiso de almacenamiento y eventos de uso estables. Alternativa solo si un cliente lo exige: 26.
- 2026-10-05 · Repo **público** `Ox19/DroidInspector`; commits con `AlxOps` + noreply de GitHub (`--local`). Cambios al repo requieren aprobación del dueño (protección de rama en `main`).
- 2026-10-05 · `.gitignore` se ignora a sí mismo (pedido). Reglas de respaldo en [CLAUDE.md](.claude/CLAUDE.md).
- 2026-10-05 · Este checklist se versiona pese al nombre "local".

## Fase 0 — Repositorio y documentación
- [x] `git init` en `main` + rama `develop` · evidencia: commit `459d776` · 2026-10-05
- [x] Identidad local fijada (`git config --local`) · 2026-10-05
- [x] [.gitignore](.gitignore) completado (incluye a sí mismo, `.idea/`, `build/`, `*.pdf`, firmas) · 2026-10-05
- [x] Commit de plantilla como punto de retorno · `459d776` · 2026-10-05
- [x] [CLAUDE.md](.claude/CLAUDE.md), [.claude/](.claude/), [docs/PLAN.md](docs/PLAN.md), diagramas [flow](docs/flow.excalidraw) y [arquitectura](docs/architecture.drawio) · `29a816f` · 2026-10-05
- [x] Repo público https://github.com/Ox19/DroidInspector (ya existía vacío) y push de `main` y `develop` · `29a816f` · 2026-10-05
- [x] Protección de `main`: PR + 1 aprobación, sin force-push ni borrado; `enforce_admins` apagado para el dueño · 2026-10-05

## Fase 2 — MVP
- [ ] `minSdk=29` y actualizar `core-ktx`, `lifecycle-runtime-ktx`, `activity-compose` · [app/build.gradle.kts](app/build.gradle.kts), [libs.versions.toml](gradle/libs.versions.toml)
- [ ] Manifest: `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`, `allowBackup=false` · [AndroidManifest.xml](app/src/main/AndroidManifest.xml)
- [ ] `domain/`: modelos, `UsageRepository`, `ReportExporter`, `SessionBuilder`, `AuditAggregator`, `BuildAuditReport` + tests
- [ ] `data/`: `UsageStatsRepository`
- [ ] `ui/`: consentimiento → permiso → rango → dashboard
- [ ] `report/`: `PdfReportExporter` a Descargas

## Fase 3 — Opcional (no entra al MVP)
- [ ] Snapshots históricos (Room + WorkManager)
- [ ] Datos de red por app (`NetworkStatsManager`)
- [ ] Categorías de apps · exportar JSON/CSV

## Fase 4 — Verificación
- [ ] Tests unitarios en verde
- [ ] Comparar tiempos contra Bienestar digital en dispositivo real
- [ ] PDF abre desde Descargas · APK sin permiso `INTERNET` (`aapt dump permissions`)
- [ ] Probar en Android 10 y en la versión más reciente

## Bitácora
- 2026-10-05 · Plan aprobado; repo inicializado y plantilla commiteada (`459d776`).
- 2026-10-05 · Docs, reglas y diagramas (`29a816f`); push a GitHub y protección de `main`. Fase 0 cerrada.
