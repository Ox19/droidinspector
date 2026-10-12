# Arquitectura

Audita la actividad de uso del propio dispositivo Android: lee los eventos que el
sistema guarda sobre qué apps se abrieron y cuándo (`UsageStatsManager`), los
convierte en sesiones con duración, agrega métricas del rango de fechas elegido y
deja el resultado en pantalla y en un PDF en la carpeta Descargas. Todo ocurre en
el dispositivo: la app no declara `INTERNET` y el PDF es el único artefacto.

## Nombre

**Arquitectura en capas con dependencias unidireccionales**, con tres patrones dentro:

| Patrón | Dónde | Qué aporta |
|---|---|---|
| Functional Core, Imperative Shell | `core/` es puro; `ui/` y `data/` hacen el I/O | El cálculo se prueba sin emulador ni mocks |
| MVVM | `ui/`: ViewModel con estado observable + Compose | Patrón de presentación recomendado por Google |
| Capas UI · Dominio · Datos | `ui/` · `core/` · `data/` | Alineado con la guía oficial de arquitectura de Android |

**No es Clean Architecture ni Hexagonal**, aunque comparte con ellas que el núcleo no
depende del framework: las dos exigen inversión de dependencias mediante interfaces
(puertos) definidas en el dominio, y aquí se descartan por haber una sola
implementación de cada fuente (ver *Fuera de alcance*).

## Estructura

```
app/src/main/java/com/example/droidinspector/
├── MainActivity.kt        # entrypoint: tema + pantalla raíz
├── core/                 # lógica de negocio, sin imports de android.*
│   ├── Models.kt          # AuditRange, UsageEvent, AppSession, AppUsage, AuditReport
│   ├── SessionBuilder.kt  # eventos sueltos → sesiones con duración
│   └── AuditAggregator.kt # sesiones → métricas del reporte
├── data/                 # I/O: sistema Android, PDF, archivos
│   ├── UsageStatsSource.kt  # eventos de uso y estado del permiso
│   ├── AppCatalog.kt        # nombre e icono por paquete (PackageManager)
│   └── PdfReportWriter.kt   # escribe el PDF vía PdfDocument + MediaStore
├── ui/                   # Compose + ViewModel: conecta core y data
│   ├── AuditViewModel.kt  # orquesta: carga con data, calcula con core
│   ├── AuditScreen.kt     # consentimiento → permiso → rango → dashboard
│   └── theme/             # Color, Theme, Type
└── shared/
    └── Config.kt          # retención de eventos, límites
```

## Capas

```mermaid
flowchart TD
    UI["ui/"] --> CORE["core/"]
    UI --> DATA["data/"]
    CORE --> SHARED["shared/"]
    DATA --> SHARED
```

- `core/` — lógica de negocio. No importa `data/` ni `android.*`. Recibe datos ya
  cargados, devuelve métricas. Es la capa con tests unitarios.
- `data/` — lectura y escritura (sistema → eventos, reporte → PDF). Sin reglas de negocio.
- `shared/` — valores: límites de retención, formatos. Sin lógica.
- `ui/` — los únicos módulos que conocen `core/` y `data/`. Los conectan. El
  `AuditViewModel` orquesta; los composables solo pintan estado.

## Flujo de la auditoría

```mermaid
sequenceDiagram
    participant UI as AuditScreen
    participant VM as AuditViewModel
    participant Usage as UsageStatsSource
    participant Catalog as AppCatalog
    participant Builder as SessionBuilder
    participant Agg as AuditAggregator
    participant Pdf as PdfReportWriter

    UI->>VM: consentimiento + rango de fechas
    VM->>Usage: hasPermission()
    VM->>Usage: events(range)
    Usage-->>VM: eventos crudos
    VM->>Builder: build(events, range)
    Builder-->>VM: sesiones
    VM->>Agg: aggregate(sessions, range)
    Agg-->>VM: AuditReport
    VM->>Catalog: labels(packageNames)
    Catalog-->>VM: nombre e icono
    VM-->>UI: estado del dashboard
    UI->>VM: exportar
    VM->>Pdf: write(report, labels)
    Pdf-->>VM: ruta en Descargas
```

Sin permiso de acceso de uso no hay lectura: la UI guía al usuario a Ajustes y reintenta.

## Decisiones

| Decisión | Motivo |
|---|---|
| Un solo módulo Gradle | El proyecto no justifica módulos por capa; las fronteras las impone el paquete |
| Sin interfaces de repositorio | Hay una sola implementación de cada fuente (ver *Fuera de alcance*) |
| Inyección manual por constructor | El grafo es pequeño; Hilt no se gana su lugar |
| Navegación por estado | Cuatro pantallas secuenciales; la librería de navegación no aporta |
| `PdfDocument` del framework | Evita una dependencia de PDF |
| `AuditReport` usa nombres de paquete | `core/` no conoce `PackageManager`; el nombre visible lo resuelve `data/` |
| Zona horaria inyectada en `AuditAggregator` | El reparto por día y hora se prueba sin depender de la zona de la máquina |

## Límite conocido

El sistema solo retiene los eventos finos de uso unos 7–10 días. El selector de rango
debe deshabilitar las fechas anteriores. Auditar periodos más largos exigiría guardar
instantáneas periódicas (fuera del MVP).

## Fuera de alcance

- I/O dentro de `core/`.
- Reglas de negocio dentro de `data/`.
- Interfaces/abstracciones para una sola implementación.
- Capas adicionales a las cuatro de arriba.
- Permiso `INTERNET` y cualquier envío de datos fuera del dispositivo.
