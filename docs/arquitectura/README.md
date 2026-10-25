# Arquitectura de la app

La app es **offline-first**: todo se escribe primero en Room (con UUID y estado `PENDING`) y un
trabajo de WorkManager lo envía al backend FastAPI cuando hay red. La app solo habla con nuestro backend
(RT_11); los Equipos 1 y 2 también.

Estado de las historias y de las ramas: [`../estado-proyecto.md`](../estado-proyecto.md). Cada historia tiene
su guía en `docs/huNN.md`.

## Flujo

```text
Compose → ViewModel → Repository → Room (siempre)  ──►  WorkManager (SyncWorker) → Retrofit → FastAPI /api/v1 → PostgreSQL (Neon)
Compose → LoginViewModel / RegisterViewModel / CompleteProfileViewModel → AuthRepository → Firebase Auth (+ POST /users/register)
AppNavHost observa AuthRepository.sessionState y profileState (SessionGuard) y protege las rutas internas
```

La UI no conoce el DAO ni la red (RT_08). La navegación crea los ViewModel y les entrega el repositorio de la
cuenta con sesión abierta: cada cuenta tiene **su propia base Room** (`inventory-<uid>.db`), así que nunca ve los
datos de otra.

## Paquetes

| Ubicación | Responsabilidad |
| --- | --- |
| `ui/auth` | Login, registro, completar perfil (cuenta sin perfil en el backend) y sus ViewModel. |
| `ui/inventory` | Inventario, detalle, formulario de movimientos (entrada, consumo, pérdida, ajuste) e historial. |
| `ui/parcels` | Lista, detalle y registro de parcelas (con sus actividades y consumos). |
| `ui/activities` | Formulario de actividad productiva y la pestaña Actividades (HU_08). |
| `ui/navigation` | Rutas, pestañas, `SessionGuard` y creación de ViewModel. |
| `data/auth` | `AuthRepository`, implementación Firebase, estado de sesión y de perfil, mapeo de errores. |
| `data` | `InventoryRepository` (movimientos, actividades, historial), `ParcelRepository`, `MovementSyncRepository`, `Quantity`. |
| `data/local` | Entidades, DAO y base Room (versión 5; esquemas exportados en `android-app/app/schemas`). |
| `data/remote` | Interfaces Retrofit (`MovementApi`, `ActivityApi`, `ParcelApi`, `UserApi`). |
| `sync` | `SyncScheduler` y `SyncWorker` (WorkManager). |
| `core/network` | `NetworkModule`: cliente HTTP (espera de 90 s por el servidor dormido) y calentamiento al abrir. |

## Reglas que sostienen el diseño

- **Movimientos solo de inserción** (RT_07); las correcciones son ajustes. La existencia se actualiza en la misma
  transacción que el movimiento (RT_10) y nunca queda negativa.
- **Sincronización idempotente por UUID**: reenviar no duplica. Las actividades viajan antes que sus consumos.
- **Autenticación y autorización separadas** (RT_05, RT_13): Firebase solo aporta identidad; el backend valida el
  token, que la cuenta tenga perfil y que su rol sea PRODUCTOR.
- **Tablas locales sin clave foránea hacia `parcels`**: `ParcelRepository.refresh()` borra y reinserta las parcelas.
- Cada cambio del esquema local sube la versión de Room, con `AutoMigration` y su `N.json`.
- Repositorios concretos e inyección manual; no se agregan capas vacías ni un framework de inyección.

## Backend

`api/v1` (rutas), `schemas` (contratos camelCase), `services` (reglas), `models` y `db`, con migraciones de Alembic
(una sola cabeza). Ver [`../../backend/README.md`](../../backend/README.md) y, para el despliegue,
[`../despliegue-render.md`](../despliegue-render.md).
