# Privacidad de datos de clientes

- Consentimiento explícito antes de leer cualquier dato de uso.
- Nada de nombres de apps, paquetes ni tiempos en logs, commits, issues ni capturas del repo.
- Auditorías de prueba (PDF) fuera del repo; el `.gitignore` ya excluye `*.pdf` y `audits/`.
- `allowBackup=false` y sin permiso `INTERNET`: los datos no salen del dispositivo salvo el PDF que el usuario comparte.
- El repo es **público**: ningún secreto, keystore, ruta personal ni dato de cliente.
