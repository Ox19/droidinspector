# Arquitectura por capas

- `domain/`: modelos, casos de uso e interfaces (`UsageRepository`, `ReportExporter`). Sin imports de `android.*`.
- `data/`: implementa `UsageRepository` con `UsageStatsManager` y `PackageManager`.
- `report/`: implementa `ReportExporter` (PDF con `PdfDocument`, guardado por `MediaStore.Downloads`).
- `ui/`: Compose + ViewModel; solo consume casos de uso. Inyección manual por constructor (`AppContainer`); sin Hilt ni Navigation hasta que se justifique.
- Nueva fuente de datos o formato de salida = nueva implementación de la interfaz, sin tocar `domain/` ni `ui/`.
- Lógica que puede fallar en silencio (`SessionBuilder`, `AuditAggregator`) lleva test unitario.
