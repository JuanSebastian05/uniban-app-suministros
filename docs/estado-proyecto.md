# Estado del proyecto

> Actualizado el **26/10/2026** contra el código de esta copia del repositorio. Este documento distingue lo que el código implementa de lo que se ha confirmado sobre GitHub, Render y Neon.

## Resumen ejecutivo

La solución permite administrar inventario de forma local y sin conexión, registrar parcelas, ciclos y actividades, y sincronizar los datos con el backend. También están implementadas en el código ambas integraciones: se reciben aforamientos del Equipo 1 y se solicitan recomendaciones al Equipo 2.

El código local contiene migraciones de backend hasta **0011** y Room versión **7**. El usuario informó que la migración **0010 llegó a main** y que **SERVICE_API_KEY está configurada en Render**. Esta revisión no consultó GitHub, Render ni Neon; por eso no confirma qué versión está desplegada ni la revisión actual de la base de producción.

## Funcionalidad implementada en el código

| Área | Estado actual | Alcance y límites |
| --- | --- | --- |
| Identidad y perfiles | Implementada | Firebase Authentication; el backend mantiene el perfil del productor, exige autenticación en rutas de usuario y valida el rol PRODUCTOR. |
| Inventario | Implementado | Catálogo por productor, consulta offline, entradas, consumos, pérdidas, ajustes, saldo no negativo e historial. La app no permite registrar SALIDA. |
| Catálogo de empaque | Implementado en el código local de esta rama | cajas, carton, bolsas, categoría Empaque, unidad unidad, saldo inicial cero. El alta también se aplica a cuentas ya inicializadas sin borrar sus datos. |
| Parcelas y ciclos | Implementados | Registro conectado al backend, consulta y copia local. |
| Actividades productivas | Implementadas | Se guardan localmente y se sincronizan antes de los consumos asociados. |
| Sincronización | Implementada | Movimientos y actividades se envían con UUID idempotente. No hay descarga completa del inventario para reconstruirlo tras reinstalar o cambiar de teléfono. |
| Integración Equipo 1 | Implementada en el código | GET /api/v1/service/parcels y POST /api/v1/aforamientos, protegidos por X-API-Key; idempotencia por UUID. La app consulta el aforamiento más reciente. |
| HU_09 | Implementada en el código | Muestra el aforamiento más reciente de la parcela y conserva una copia local para consulta offline. |
| Integración Equipo 2 y HU_12 | Implementadas en el código | La app pide una recomendación a nuestro backend; este construye la solicitud, llama al servicio, guarda la respuesta y la app conserva la última para verla offline. |
| HU_10 Estado de sincronización | Parcial | Cuenta los registros no sincronizados y presenta estados individuales; falta hora de última sincronización y una vista central de errores. Ver [hu10.md](hu10.md). |
| HU_11 Buscar y filtrar insumos | Parcial | Busca por nombre localmente; falta filtro por categoría y combinación con la búsqueda. Ver [hu11.md](hu11.md). |

## Integraciones: situación y pendientes

### Equipo 1

- Contrato de código: GET /api/v1/service/parcels devuelve identificador, nombre y área; POST /api/v1/aforamientos recibe UUID, parcela, fecha, área aforada, plantas y confianza.
- La clave SERVICE_API_KEY se lee del entorno del backend. El usuario reportó que ya quedó configurada en Render.
- El usuario reportó que la migración 0010 ya está en main.
- Discrepancia pendiente: el acuerdo compartido muestra areaHa como número, pero la respuesta actual de parcelas del backend serializa areaHa como texto, por ejemplo "2.40". Confirmar si el Equipo 1 acepta ese formato o ajustar el contrato/código.
- La simulación local previa verificó el recorrido con datos ficticios; no equivale a una prueba real con el servicio desplegado ni con el Equipo 1.

### Equipo 2

- El backend tiene el cliente y las rutas autenticadas por Firebase para crear y consultar una recomendación.
- El formato acordado con el equipo aún no está cerrado. El contrato describe por separado lo que el código envía, lo que devuelve el servicio consultado y las decisiones pendientes.
- El usuario decidió incorporar cajas, carton y bolsas, con unidad unidad, nombres sin tildes y en minúscula. Filtrar el envío para que incluya únicamente esos productos sigue pendiente: el código actual envía todos los insumos disponibles del productor, incluidos los agrícolas.
- La solicitud actual incluye consumos de la parcela y pérdidas del productor de los últimos 90 días; confirmar esa ventana y el alcance de datos con el Equipo 2.
- El inventario que arma el backend incluye los registros existentes, incluso si su saldo es cero.

## Versiones del esquema

| Componente | Versión en el código | Observación |
| --- | --- | --- |
| Room | 7 | Incluye parcelas/ciclos, actividades, aforamientos y recomendaciones. Migraciones automáticas hasta 7. |
| Alembic | Cabeza 0011_recomendaciones | Cadena 0001–0005 y 0007–0011. No existe una revisión 0006 en esta copia. |
| Producción Neon | No verificada en esta revisión | No inferirla de que una migración esté en main. Consultar alembic_version antes de nuevas migraciones. |

## Verificaciones que siguen pendientes de evidencia

1. Confirmar la revisión desplegada en Neon y que Render esté ejecutando el código correspondiente.
2. Ejecutar una prueba conjunta del Equipo 1 contra las rutas desplegadas, incluyendo lectura de parcelas y reenvío idempotente de un aforamiento.
3. Probar una recomendación contra el servicio real del Equipo 2 y acordar formalmente el cuerpo y el filtro de empaques.
4. Hacer una demostración manual de los flujos principales en emulador o celular, con Firebase real y modo avión, y guardar los resultados.
5. Confirmar el MER actualizado con el equipo y revisar su presentación para el informe académico.

## Documentos de referencia

- Contratos: [Equipo 1](contrato-equipo1.md), [Equipo 2](contrato-equipo2.md).
- Instrucciones de operación: [Render y Neon](despliegue-render.md), [lista de migraciones](lista-verificacion-despliegue.md), [prueba manual](guia-prueba-celular.md).
- Detalles por funcionalidad: [HU_09](hu09.md), [HU_12](hu12.md), y las demás fichas enlazadas desde el README.
- Avisos históricos: [avisos-equipo.md]. Para el estado vigente, priorizar este documento y los contratos.
