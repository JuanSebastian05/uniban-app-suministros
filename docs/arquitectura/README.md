# Arquitectura: primera entrega

El alcance acordado es HU_01, HU_03, HU_04 y HU_06. Actualmente están implementados
el inicio de sesión (HU_01) y el registro local de entradas (HU_04), con lista y detalle
para demostrar el resultado.

## Flujo implementado

```text
UnibanApplication → InventoryRepository → InventoryDatabase (Room)
UnibanApplication → AuthRepository (FirebaseAuthRepository)
Compose → InventoryViewModel / EntryViewModel → InventoryRepository
Compose → LoginViewModel → AuthRepository → Firebase Auth
AppNavHost observa AuthRepository.sessionState y protege las rutas internas
```

La aplicación mantiene una instancia de cada repositorio y la base. La navegación crea los
ViewModel y les entrega el repositorio. La UI observa datos locales y envía acciones;
no conoce el DAO ni administra conexiones.

Firebase Auth solo aporta identidad (RT_05); la autorización la validará el backend (RT_13).
Sin sesión solo existe la ruta `login`; las pantallas internas viven en el grafo `main` y,
al cerrar sesión, la pila se vacía y el ViewModel de inventario se destruye.

| Ubicación | Responsabilidad |
| --- | --- |
| `ui/inventory` | Lista, detalle, formulario, confirmación y sus ViewModels. |
| `ui/auth` | Pantalla de login, `LoginViewModel` y componentes propios (logotipo, banner de error). |
| `ui/navigation` | Rutas, guard de sesión y creación de ViewModels. |
| `data/auth` | `AuthRepository`, implementación Firebase, estado de sesión y mapeo de errores. |
| `data/InventoryRepository` | Carga demo y transacción de registro de entrada. |
| `data/Quantity` | Validación, suma y presentación de cantidades con dos decimales. |
| `data/local` | Entidades, DAO, base Room y datos de demostración. |
| `core/network` | Configuración HTTP reservada para la conexión futura. |

Una entrada guarda el movimiento con UUID y estado `PENDING` y actualiza la
existencia en una misma transacción. Los saldos iniciales están separados de los
movimientos. El esquema Room se exporta en `android-app/app/schemas`; los cambios
posteriores requieren aumentar la versión y una migración que preserve los datos.

Se usan repositorios concretos e inyección manual. No se necesitan capas vacías de
dominio ni un framework de inyección para este alcance.

## Decisiones de esta demostración

- Un productor y una ubicación: **Inventario principal**.
- Catálogo local fijo de seis insumos, cargado una sola vez.
- Cantidades Float, hasta dos decimales y límite de 99.999,99.
- Solo movimientos de entrada; sin actividad productiva asociada.
- Los datos locales todavía no pertenecen a la cuenta: al cerrar sesión **no se borran** y
  cualquier cuenta ve el mismo productor demo. Asociarlos al `uid` queda para HU_03.

Ver [HU_01](../hu01.md) y [HU_04](../hu04.md) para detalles y pasos de demostración.

## Pendiente para HU_03 y HU_06

Asociar el inventario a la cuenta (`SessionState.Authenticated.uid`) y definir el tratamiento
de datos locales al cambiar de sesión. El token de Firebase se agregará a las llamadas HTTP con HU_06.

WorkManager coordinará el envío de movimientos mediante Retrofit a FastAPI `/api/v1`.
La base PostgreSQL deberá imponer unicidad por UUID para evitar duplicados, y el
backend validará identidad y permisos. Se definirán reintentos y recuperación de
registros `SYNCING` interrumpidos al implementar HU_06.

El backend conserva `api/v1`, `schemas`, `models` y `db`. Se incorporarán
migraciones al crear la primera tabla. Capas adicionales se extraerán cuando la
lógica implementada las justifique.
