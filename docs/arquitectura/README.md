# Arquitectura: primera entrega

El alcance acordado es HU_01, HU_03, HU_04 y HU_06. Actualmente está implementado
el registro local de entradas (HU_04), con lista y detalle para demostrar el resultado.

## Flujo implementado

```text
UnibanApplication → InventoryRepository → InventoryDatabase (Room)
Compose → InventoryViewModel / EntryViewModel → InventoryRepository
```

La aplicación mantiene una instancia del repositorio y la base. La navegación crea los
ViewModel y les entrega el repositorio. La UI observa datos locales y envía acciones;
no conoce el DAO ni administra conexiones.

| Ubicación | Responsabilidad |
| --- | --- |
| `ui/inventory` | Lista, detalle, formulario, confirmación y sus ViewModels. |
| `ui/navigation` | Rutas y creación de ViewModels. |
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
- Inventario visible sin login mientras se implementa HU_01.

Ver [HU_04](../hu04.md) para detalles y pasos de demostración.

## Pendiente para HU_01 y HU_06

Firebase Authentication gestionará la identidad. Habrá que asociar el inventario
a la cuenta y definir el tratamiento de datos locales al cerrar o cambiar sesión.

WorkManager coordinará el envío de movimientos mediante Retrofit a FastAPI `/api/v1`.
La base PostgreSQL deberá imponer unicidad por UUID para evitar duplicados, y el
backend validará identidad y permisos. Se definirán reintentos y recuperación de
registros `SYNCING` interrumpidos al implementar HU_06.

El backend conserva `api/v1`, `schemas`, `models` y `db`. Se incorporarán
migraciones al crear la primera tabla. Capas adicionales se extraerán cuando la
lógica implementada las justifique.
