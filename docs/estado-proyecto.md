# Estado del proyecto

> Documento vivo. **Quien trabaje (persona o IA) lo actualiza en cada PR**: estado de las HU, ramas,
> pendientes y bitácora. Está pensado para retomar el trabajo sin conversaciones previas.
> Última actualización: **2026-10-25**, tras migrar Neon a `0009` y pasar `develop` a `main` (Simon, con Claude).

## Cómo retomar (para una IA o una persona nueva)

1. Lee [`CLAUDE.md`](../CLAUDE.md) y [`avisos-equipo.md`](avisos-equipo.md).
2. `git fetch --all`, `git branch --show-current`, `git log --oneline -5`, `git status`.
3. Mira «Ramas» y «Pendientes» abajo: retoma el primer pendiente **no hecho** de la lista priorizada.
4. Corre las pruebas (comandos en `CLAUDE.md`) para saber en qué punto estás antes de cambiar nada.

## Alcance acordado (semana del 21/10/2026)

- **Simon:** cerrar los pendientes de HU_01–07, 13 y 14 y **después** desarrollar **HU_08** completa.
- **Compañero(s):** HU_09, HU_10, HU_11, HU_12, el tipo de movimiento SALIDA y, por confirmar, la
  integración con los Equipos 1 y 2 (contratos, despliegue, aforamiento, recomendaciones, catálogo de
  cajas/cartón/bolsas, pruebas conjuntas y diagrama entre los tres equipos).

## Estado por historia de usuario

| HU | Estado | Qué falta |
| --- | --- | --- |
| HU_01 Iniciar sesión | Hecha | — (cuenta huérfana resuelta con P-02; falta comprobarla a mano en un celular, P-06). |
| HU_02 Parcelas y ciclo activo | Hecha | — |
| HU_03 Inventario sin conexión | Hecha | — (el inventario inicial es de demostración: 6 insumos). |
| HU_04 Registrar entrada | Hecha | «Crear insumo» no existe; se cumple el criterio 4 eligiendo uno existente. |
| HU_05 Consumos y pérdidas | Hecha (+ AJUSTE el 24/10, P-03) | SALIDA (compañero): ver A-05. |
| HU_06 Sincronizar | Hecha (solo envío) | El historial no se descarga del servidor (limitación). |
| HU_07 Historial | Hecha | Mostrar la actividad en cada movimiento: llega con HU_08. |
| HU_13 Registrarse | **Cerrada el 21/10** (documento único + robustez, P-02) | Ver [`hu13.md`](hu13.md). |
| HU_14 Registrar parcela | Hecha | Actualizar el MER y avisar al Equipo 1 (equipo, Juan José). |
| **HU_08 Actividad productiva** | **Implementada el 24/10 (Simon)**, en PR | Ver [`hu08.md`](hu08.md). Verificada en emulador (25/10); migración `0009` ya aplicada en Neon y desplegada en `main`. |
| **Integración Equipo 1 (backend)** | **Implementada el 26/10**, rama `feat/integracion-equipo1` | Clave de servicio, `GET /service/parcels`, `POST /aforamientos` y migración `0010`. Ver [`contrato-equipo1.md`](contrato-equipo1.md) y A-08. Falta: configurar `SERVICE_API_KEY` en Render y aplicar `0010` en Neon (Simon). |
| **HU_09 Consultar aforamiento** | **Implementada el 26/10**, rama `feat/hu09-aforamiento` (parte de `feat/integracion-equipo1`; mezclar después) | Ver [`hu09.md`](hu09.md). Room v6, `GET /parcels` con `aforamiento` y `GET /parcels/{id}/aforamiento`. Sin probar en emulador; depende de `0010` en Neon. |
| **Integración Equipo 2 (backend)** | **Implementada el 26/10**, rama `feat/integracion-equipo2` (apilada sobre `feat/hu09-aforamiento`) | Cliente del Equipo 2, `POST/GET /parcels/{id}/recommendation` y migración `0011`. Ver [`contrato-equipo2.md`](contrato-equipo2.md) y A-09. Falta aplicar `0011` en Neon. |
| HU_10–12, SALIDA | Compañero | — |

## Producción (25/10/2026)

- **Neon: migración `0009` (cabeza).** Se ensayó antes en una rama de desarrollo de Neon (sin errores), se creó una
  rama de respaldo y luego se aplicó en la base compartida (`0005` → `0009`).
- **`main` = `develop`** (merge del PR #22). Render redesplegó y sirve `/api/v1/activities` (comprobado en `/openapi.json`).
- **Primer despliegue:** Render quedó «Live» pero con el código anterior hasta que quien administra el servicio lanzó
  un *Manual Deploy*. Tras un merge a `main`, comprobar siempre que `/openapi.json` liste las rutas nuevas.
- **Siguiente migración: `0010`** (aforamientos; ya escrita en `feat/integracion-equipo1`, pendiente de aplicar), con el mismo procedimiento: rama de ensayo → compartida → `main`
  ([`lista-verificacion-despliegue.md`](lista-verificacion-despliegue.md)).

## Ramas y PR (de Simon, al 21/10/2026; los PR #15 a #19 ya están mezclados en `develop`)

| Rama | PR | Contenido | Siguiente paso |
| --- | --- | --- | --- |
| `fix/pruebas-android-y-backend` | **#14, mergeado** en `develop` | Pruebas de Android y backend en verde. | — |
| `fix/hu13-documento-unico` | [#15](https://github.com/JuanSebastian05/uniban-app-suministros/pull/15), abierto | HU_13: documento único (backend, migración `0007`, app, pruebas) + `CLAUDE.md`, `estado-proyecto.md`, `avisos-equipo.md`, `hu13.md`. | Lo revisa/mergea otra persona; **migrar Neon antes del merge a `main`** (A-02). |
| `fix/robustez-registro-y-perfil` (apilada sobre `fix/hu13-documento-unico`) | [#16](https://github.com/JuanSebastian05/uniban-app-suministros/pull/16), abierto | P-02: cuenta huérfana («Complete su perfil») y tiempo de espera frente a Render. Solo app + docs, **sin migración**. | Mergear **después** del #15 (o juntos; el #16 contiene al #15). |
| `feat/ajuste-movimientos` (apilada sobre `fix/robustez-registro-y-perfil`) | [#17](https://github.com/JuanSebastian05/uniban-app-suministros/pull/17), abierto | P-03: AJUSTE (backend, migración `0008`, Room v4, app, pruebas, `hu05.md`). Backend 108 y Android 158 pruebas en verde. | Mergear **después** del #16. |
| `feat/hu08-actividad-productiva` (apilada sobre `feat/ajuste-movimientos`) | [#18](https://github.com/JuanSebastian05/uniban-app-suministros/pull/18), abierto | HU_08: backend (`0009`, `POST /activities`), Room v5, formulario, pestaña Actividades, sincronización y `hu08.md`. Backend 136 y Android 210 pruebas en verde. | Mergear **después** del #17. |

Pruebas al 21/10/2026 en `fix/robustez-registro-y-perfil`: **backend 95** (sin cambios en esta rama, corrido
en la rama del #15) y **Android 143, todas en verde**; `assembleDebug` y `lintDebug` sin errores.
No se probó en emulador ni en celular (P-06).

## Pendientes, en orden (antes de HU_08)

| # | Pendiente | Estado |
| --- | --- | --- |
| P-01 | HU_13: rechazar documento repetido | **Hecho** (rama `fix/hu13-documento-unico`) |
| P-02 | Robustez del registro: (a) tiempo de espera de 30 s frente al despertar de Render (≈ 1 min) en registro y parcelas; (b) cuenta huérfana (Firebase sin perfil → 403 para siempre): al iniciar sesión, si `GET /users/me` da 404, completar el perfil | **Hecho** (rama `fix/robustez-registro-y-perfil`, PR #16): lectura de 90 s, `GET /health` al abrir, aviso «puede tardar hasta un minuto» y pantalla «Complete su perfil». Ver [`hu01.md`](hu01.md#cuenta-sin-perfil-cuenta-huérfana-p-02) |
| P-03 | AJUSTE: registrar correcciones (positivas y negativas) con motivo; backend `SUPPORTED_TYPES`, app `MovementType.REGISTRABLE`, historial (`±`) y resumen RT_10. Coordinar con SALIDA (A-05) | **Hecho** (rama `feat/ajuste-movimientos`): migración `0008`, Room v4, sentido AUMENTO/DISMINUCION. Ver [`hu05.md`](hu05.md#ajustes-ajuste-24102026) |
| P-04 | **Hecho el 24/10** (rama `docs/p04-documentacion-y-rt13`): README raíz y `arquitectura/README.md` reescritos, «pendiente de compilar» quitado, pendientes de `hu03`/`hu06` al día, y RT_13 con prueba de 403 por rol (`tests/test_roles.py`, el backend rechaza roles distintos de PRODUCTOR). `.idea/` se deja como está (decisión: no se toca lo versionado por el equipo). Texto original: documentación y calidad: README raíz y `docs/arquitectura/README.md` desactualizados (dicen «HU_04 con datos demo»); `hu01/03/06` con secciones «Pendiente» obsoletas; quitar «pendiente de compilar» de `hu02/05/07`; test de 403 por rol (RT_13: hoy solo existe PRODUCTOR); decidir si `.idea/` deja de versionarse | Por hacer |
| P-05 | HU_14: actualizar `docs/modelo-datos/MERv2.drawio` (aforamiento → `id_parcela`; `id_actividad` opcional en movimientos) y avisar al Equipo 1 que usarán el UUID de parcela y hectáreas | Del equipo (Juan José) |
| P-06 | Verificar manualmente en emulador/celular los criterios de HU_01–07 (modo avión, etc.): nunca se confirmaron | Por hacer (requiere `google-services.json`) |
| P-07 | Historial y saldos solo del dispositivo: al reinstalar o cambiar de teléfono se pierden (existe `GET /api/v1/inventory`, la app no lo usa) | Decisión pendiente |

## HU_08: diseño acordado (implementado el 24/10/2026; el resultado está en [`hu08.md`](hu08.md))

- **Tipos de actividad** (mockup): Fertilización, Siembra, Cosecha, Aplicación de insumo, Control
  fitosanitario, Otra actividad. **Exige ciclo activo** en la parcela.
- **Formulario** (pestaña **Actividades** en la barra inferior): tipo, parcela (muestra su ciclo activo),
  fecha, observación opcional y bloque opcional **«Insumos usados»** (insumo + cantidad, varias filas). Todo
  se guarda en **una transacción de Room**: la actividad y un CONSUMO por cada insumo, con `activityId`,
  `parcelId` y el `cycleId` de la actividad; si algún insumo no alcanza, no se guarda nada.
- **Room versión 5** (la 4 la tomaron los ajustes): tabla `activities` (id, parcelId, cycleId, type, date, observation, createdAt,
  updatedAt, syncStatus; **sin FK a parcels**) y `movements.activityId` nullable. `AutoMigration(4 → 5)`.
- **Backend:** migración `0009` (la `0008` son los ajustes): tabla `activities` y `movements.activity_id`; `POST /api/v1/activities`
  idempotente por UUID (201/200/409/422); un CONSUMO con `activityId` exige que la actividad exista, sea del
  productor y coincidan parcela y ciclo.
- **Sincronización:** primero las actividades, luego los movimientos; un movimiento cuya actividad aún no
  está `SYNCED` espera en `PENDING`. El contador de pendientes suma ambos.
- **Visibilidad (criterio 4):** sección «Actividades» en el detalle de la parcela y «Actividad: …» en cada
  tarjeta del historial de los insumos asociados.
- **Fuera de alcance:** editar o borrar actividades; `GET /activities` para el Equipo 2.

## Decisiones tomadas (resumen; el detalle está en cada `hu0X.md` y en `avisos-equipo.md`)

- 21/10/2026: HU_08 usa los tipos del mockup y exige ciclo activo.
- 21/10/2026: documento único comparado sin mayúsculas, puntos, espacios ni guiones; las cuentas antiguas
  repetidas no se tocan (`hu13.md`).
- 21/10/2026: migraciones `0006` (aforamientos, compañero), `0007` (HU_13), `0008` (HU_08) (A-01).
- 15/10/2026 (HU_14): el stock pertenece al productor (su ubicación), el consumo se asocia a parcela y ciclo;
  las parcelas se crean con conexión (`hu14.md`).
- 15/10/2026 (HU_05): el stock nunca queda negativo; SALIDA y AJUSTE quedaron para después (`hu05.md`).

## Bitácora

- **2026-10-26 (Claude), integración Equipo 2 (backend):** cliente HTTP (90 s, un reintento; sin respuesta → 503, respuesta
  inválida o rechazo → 502), solicitud en formato del contrato más campos directos, `POST/GET /parcels/{id}/recommendation`,
  tabla `recommendations` (migración `0011`, JSONB en PostgreSQL) y `httpx` como dependencia de ejecución. Sin aforamiento no se
  llama al Equipo 2 (409). Backend 226 → 271 pruebas (ninguna llama a la red). `0011` y las consultas verificadas en un
  PostgreSQL temporal; no se tocó Neon. El OpenAPI del Equipo 2 no pide autenticación ni campos obligatorios: ver brechas en
  `contrato-equipo2.md`.

- **2026-10-26 (Claude), HU_09:** el detalle de la parcela muestra el aforamiento más reciente del Equipo 1 (fecha, «Área
  aforada», plantas, confianza) con estados consultando / datos / «Sin aforamiento recibido» / error, y funciona sin
  conexión con la última copia. Backend: campo `aforamiento` en `GET /parcels` y `GET /parcels/{id}/aforamiento`
  (404 sin aforamientos o de otro productor). App: Room v6 (`parcel_aforamientos`, sin FK a `parcels`). Backend 213 → 226,
  app 210 → 241. Sin probar en emulador.

- **2026-10-26 (Claude), integración Equipo 1 (backend):** `SERVICE_API_KEY` y `require_service_key` (401 sin clave o con
  clave incorrecta, 503 sin configurar), `GET /api/v1/service/parcels` (solo id, nombre y área de todas las parcelas),
  `POST /api/v1/aforamientos` (idempotente por `id`: 201 / 200 / 409; 404 si la parcela no existe; 422 al validar) y
  migración `0010`. Backend 146 → 213 pruebas. `0010` verificada en un PostgreSQL temporal (upgrade, `alembic check`,
  restricciones, downgrade y upgrade); no se tocó Neon. Supuestos documentados en `contrato-equipo1.md` (redondeo,
  `confidence` opcional, 404 por parcela inexistente). Pendiente: configurar la clave en Render, aplicar `0010` en Neon y
  HU_09 en la app.

- **2026-10-24 (Simon + Claude), HU_08:** actividad productiva completa (formulario con «Insumos usados» en una sola
  transacción, ciclo activo obligatorio, sección en el detalle de la parcela, pestaña Actividades, «Actividad: …» en el
  historial, sincronización actividades → consumos). Migración `0009`, Room v5. Backend 108 → 136, app 158 → 210. Un
  cuelgue de las pruebas de Compose en la corrida completa se resolvió probando el ViewModel en JVM puro (A-03).
  Quedan: verificación manual, aplicar `0007`–`0009` en Neon y los PR por revisar (#15 → #16 → #17 → HU_08).

- **2026-10-24 (Simon + Claude), P-03:** AJUSTE (correcciones con sentido aumentar/disminuir y motivo obligatorio).
  Backend 95 → 108 pruebas, app 143 → 158; migración `0008`, Room v4. HU_08 pasa a `0009` y Room v5. El Equipo 2
  confirmó su contrato (`POST …/api/v1/recomendar`; JSON con productor, parcela, aforamiento, inventario,
  consumos y pérdidas): lo implementa el compañero de HU_12 (los campos `aforamiento.*` dependen del Equipo 1).

- **2026-10-21 (Simon + Claude):** se reconstruyó el contexto del repo (flujo de ramas, Render + Neon, archivos
  privados). Línea base: 1 prueba del backend y 30 de Android en rojo desde HU_14, sin culpa del entorno →
  arregladas (`fix/pruebas-android-y-backend`). Análisis de pendientes de HU_01–07, 13 y 14 (tabla de
  arriba). HU_13: documento repetido ya se rechaza (backend + migración `0007` verificada en un PostgreSQL
  temporal + app; 16 pruebas nuevas del backend y 4 de la app). Se crearon `CLAUDE.md`, este archivo y `avisos-equipo.md`.
  PR #14 (pruebas) y #15 (HU_13) abiertos; el #14 ya fue mergeado por otra persona.
- **2026-10-21 (Simon + Claude), P-02:** cuenta huérfana y servidor dormido. La app pregunta `GET /users/me` al
  abrir sesión (no bloquea: sin red queda «desconocido»); un `404` confirmado lleva a «Complete su perfil», que
  reutiliza `POST /users/register`. Lectura HTTP de 90 s, `GET /health` al abrir la app y aviso en registro,
  perfil y parcela. `AuthRepository` ganó `profileState`/`refreshProfile`/`completeProfile` (A-06). 30 pruebas
  nuevas de Android (113 → 143); backend sin cambios. Sin verificación manual en dispositivo. Siguiente: P-03
  (AJUSTE), P-04 (documentación y RT_13), luego HU_08.
