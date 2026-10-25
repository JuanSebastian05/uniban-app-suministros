# Guía: integración con los Equipos 1 y 2 (para quien haga HU_09, HU_12 y la integración)

> Escrita el 25/10/2026 por Simon (Equipo 3). **Léela junto con** [`../CLAUDE.md`](../CLAUDE.md),
> [`avisos-equipo.md`](avisos-equipo.md) (sobre todo A-01, A-02, A-03, A-07) y [`estado-proyecto.md`](estado-proyecto.md).
> Puedes pasársela completa a tu asistente de IA.

## Quién hace qué

Reparto acordado el 21/10: **Simon** cierra HU_01–07, 13, 14 y HU_08 (ya hecho, PR #15 a #19); **el compañero de
HU_09–12 y SALIDA** hace esas historias y, por lo conversado, **la integración con los otros equipos** (contratos,
claves, rutas, pruebas conjuntas). Juan José pidió el 25/10 que **ambas integraciones se prioricen**. Si algo de esto
no es tuyo, avisa en el grupo para repartirlo.

## Qué se acordó (grupo del equipo, 22–25/10/2026)

| Equipo | Dirección | Qué |
| --- | --- | --- |
| **Equipo 1** (aforamiento) | Ellos → nosotros | Dos llamadas HTTP a **nuestro** backend (`https://uniban-app-suministros.onrender.com`) con la clave en la cabecera `X-API-Key` (nosotros se la damos): `GET /api/v1/service/parcels` y `POST /api/v1/aforamientos`. Ya respondieron que se acomodan. |
| **Equipo 2** (recomendaciones) | Nosotros → ellos | **Nuestro backend** llama `POST https://platano-logistica-api.onrender.com/api/v1/recomendar` (`Content-Type: application/json`; Swagger en `/docs`) y guarda lo que responde. Contrato: [`contrato-equipo2.md`](contrato-equipo2.md). |

La app Android **solo** habla con nuestro backend (RT_11).

## Parte A — Equipo 1 (lo que **expone** nuestro backend)

### A.1 Autenticación por clave de servicio

- Nueva variable de entorno `SERVICE_API_KEY` (en Render y en `backend/.env`; **nunca** en el repositorio, RT_14; agrega
  la línea vacía a `backend/.env.example`). Configurable en `app/core/config.py`.
- Dependencia `require_service_key` (por ejemplo en `app/api/deps.py`): lee `X-API-Key`, compara con
  `secrets.compare_digest` y responde **401** si falta o no coincide, y **503** si `SERVICE_API_KEY` no está configurada
  (nunca aceptar «clave vacía»). Estas rutas **no** usan el token de Firebase ni `get_current_user`.
- Generar la clave con `python -c "import secrets; print(secrets.token_urlsafe(32))"` y pasársela al Equipo 1 por un
  canal privado.

### A.2 `GET /api/v1/service/parcels`

Devuelve **todas** las parcelas (de todos los productores) con solo lo que el Equipo 1 necesita:

```json
[{ "id": "uuid", "name": "La Esperanza", "areaHa": 2.40 }]
```

No exponer productor, documento, ciclo ni nada más (privacidad). `areaHa` con dos decimales (usar el mismo formato que
`ParcelOut`). Reutiliza `app/services/parcels.py` (agrega una función que liste sin filtrar por productor).

### A.3 `POST /api/v1/aforamientos`

```json
{ "id": "uuid", "parcelId": "uuid", "date": "2026-10-29", "areaHa": 2.40, "estimatedPlants": 1850, "confidence": 0.87 }
```

- **Idempotente por `id`** (lo genera el Equipo 1): mismo `id` y mismo contenido → `200` sin duplicar; mismo `id` con otro
  contenido → `409`. Primera vez → `201`.
- Validar: `parcelId` existe (si no, `404` o `409` con mensaje claro), `date` AAAA-MM-DD, `areaHa > 0` (informativa, no se
  contrasta), `estimatedPlants` entero `>= 0`, `confidence` entre 0 y 1 (`422` si no).
- Tabla nueva `aforamientos`: `id UUID PK`, `parcel_id FK → parcels.id (índice)`, `date`, `area_ha NUMERIC(9,2)`,
  `estimated_plants INT`, `confidence NUMERIC(4,3)`, `received_at`. **Migración `0006`**: está reservada para esto
  (A-01); su `down_revision` debe ser la cabeza actual (`0009` si ya entraron los PR #17/#18), **no** `0005`. Verifica
  con `alembic heads` (una sola cabeza) y prueba la migración en un PostgreSQL temporal (receta en `CLAUDE.md`), **no** en
  Neon compartida.
- Sigue el patrón de `app/services/activities.py` + `app/api/v1/endpoints/activities.py` (servicio con excepción de
  conflicto, endpoint delgado, respuestas documentadas en el decorador).

### A.4 HU_09 en la app (consultar el aforamiento recibido)

Criterio (documento de HU): al abrir el detalle de una parcela se ve **el aforamiento más reciente** con fecha, área,
plantas estimadas y, si existe, confianza.

- Backend: `GET /api/v1/parcels/{id}/aforamiento` (token de Firebase, solo parcelas del productor) → el más reciente por
  `date`, o `404`/`null` si no hay. Agrégalo también al `GET /api/v1/parcels` si prefieres una sola llamada.
- App: guardar la copia en Room (tabla nueva **sin FK a `parcels`**, A-04; **Room versión 6**, la 5 ya es de HU_08;
  `AutoMigration(5 → 6)` y subir `6.json`) y mostrarla como una sección más de `ParcelDetailScreen` («Aforamiento»).
  Debe funcionar sin conexión con la última copia y decir «Sin aforamiento recibido» si no hay.
- Pruebas: servicio y endpoint (pytest), repositorio (Robolectric + Room) y pantalla (Compose). Los ViewModel se prueban en
  JVM puro (A-03, punto 4).

## Parte B — Equipo 2 (lo que **consume** nuestro backend)

### B.1 Cliente

- Configuración: `EQUIPO2_BASE_URL` (por defecto `https://platano-logistica-api.onrender.com`) en `config.py`.
  Si su servicio pide clave, también por variable de entorno.
- Servicio `app/services/recommendations.py`: arma el cuerpo de [`contrato-equipo2.md`](contrato-equipo2.md) desde nuestra
  base (parcela, ciclo activo, último aforamiento, inventario, consumos y pérdidas) y hace el `POST`.
- **Tiempo de espera de 90 s** y un reintento: su servicio también está en Render y se duerme (la primera llamada tarda
  ≈ 1 min). Un fallo del Equipo 2 **no** debe tumbar nuestro backend: devolver `502`/`503` con mensaje claro.
- Cliente HTTP: `httpx` (revisa `requirements.txt`; hoy se usa para las pruebas, agrégalo a las dependencias de
  ejecución si hace falta). **En las pruebas nunca llames al servicio real**: sustituye el cliente con `monkeypatch`.

### B.2 Qué responde (según su OpenAPI, `RespuestaRecomendacion`)

`parcela_id`, `periodo`, `volumen_racimos`, `fecha_limite_corte`, `cumple_ventana`, `version_metodo`, `item_plan`
(`fecha_corte`, `orden`, `carga_estimada_racimos`, `cumple_ventana`, `vehiculo_id`, `motivo`), `insumos[]`
(`insumo`, `fecha`, `cantidad_requerida`, `inventario_disponible`, `deficit`) y `metricas`
(`cumplimiento_ventana`, `uso_capacidad`). Su `SolicitudRecomendacion` **acepta nuestros nombres en camelCase**
(`producerId`, `parcel`, `aforamiento`, `inventory`, ...) además de los suyos; confirma en su `/docs` cuáles son
obligatorios antes de implementar (varios campos de aforamiento dependen del Equipo 1).

### B.3 Guardar y servir la respuesta

- Tabla `recommendations` (migración nueva, **siguiente número libre después de la 0006**; coordina por A-01): guarda por
  parcela y periodo la respuesta completa (`jsonb` o columnas), `generated_at` y `source_version`.
- `POST /api/v1/parcels/{id}/recommendation` (token de Firebase): pide una recomendación nueva al Equipo 2 y la guarda.
  `GET /api/v1/parcels/{id}/recommendation`: la última guardada.

### B.4 HU_12 en la app

Sección «Recomendaciones» en el detalle de la parcela: volumen estimado, fecha límite de corte, si cumple la ventana y los
insumos con su déficit. Sigue el mismo patrón de HU_09 (Room versión 7 si es otra tabla, sin FK a `parcels`, copia local).
Sin conexión muestra la última guardada.

## Orden sugerido

1. `SERVICE_API_KEY` + `require_service_key` + `GET /service/parcels` → **avisar al Equipo 1 ya** (es lo que más los
   desbloquea) y pasarles la clave.
2. `POST /aforamientos` + migración `0006` + pruebas.
3. Cliente del Equipo 2 + `POST /parcels/{id}/recommendation` (primero sin aforamiento real si el Equipo 1 aún no envía).
4. HU_09 y HU_12 en la app.
5. Pruebas conjuntas con ambos equipos y el diagrama entre los tres.

## Reglas que no se rompen

- Cero secretos en el repo; claves solo por variable de entorno (también en pruebas: usa valores falsos).
- Una sola cabeza de Alembic; migraciones probadas en un PostgreSQL temporal; **aplicar en Neon antes del merge a `main`** (A-02).
- Pruebas en verde antes de cada PR (`pytest` y `gradlew testDebugUnitTest`), un PR por parte, a `develop`, mergeado por otra persona.
- Actualiza `docs/estado-proyecto.md`, crea `docs/hu09.md` y `docs/hu12.md` (mismo formato que `hu08.md`) y
  el aviso correspondiente en `avisos-equipo.md`. Si cambia un contrato, actualiza [`contrato-equipo2.md`](contrato-equipo2.md)
  o crea `contrato-equipo1.md` con lo acordado arriba.

## Texto sugerido para tu asistente de IA

> Trabajas en el repo `uniban-app-suministros`. Lee `CLAUDE.md`, `docs/avisos-equipo.md`, `docs/estado-proyecto.md` y
> `docs/guia-integracion-equipos.md`. Implementa la Parte A (clave de servicio, `GET /api/v1/service/parcels`,
> `POST /api/v1/aforamientos` con migración 0006) siguiendo el patrón de `activities` en el backend, con pruebas, en una
> rama `feat/integracion-equipo1` que parta de `develop`. No toques Neon ni imprimas secretos. Al terminar, corre
> `pytest`, verifica la migración en un PostgreSQL temporal, actualiza la documentación y abre el PR a `develop`.
