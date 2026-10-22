# Estado del proyecto

> Documento vivo. **Quien trabaje (persona o IA) lo actualiza en cada PR**: estado de las HU, ramas,
> pendientes y bitácora. Está pensado para retomar el trabajo sin conversaciones previas.
> Última actualización: **2026-10-21** (Simon, con Claude).

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
| HU_01 Iniciar sesión | Hecha | Recuperar la cuenta huérfana (P-02). |
| HU_02 Parcelas y ciclo activo | Hecha | — |
| HU_03 Inventario sin conexión | Hecha | — (el inventario inicial es de demostración: 6 insumos). |
| HU_04 Registrar entrada | Hecha | «Crear insumo» no existe; se cumple el criterio 4 eligiendo uno existente. |
| HU_05 Consumos y pérdidas | Hecha | AJUSTE (P-03) y SALIDA (compañero): ver A-05. |
| HU_06 Sincronizar | Hecha (solo envío) | El historial no se descarga del servidor (limitación). |
| HU_07 Historial | Hecha | Mostrar la actividad en cada movimiento: llega con HU_08. |
| HU_13 Registrarse | **Cerrada el 21/10** (documento único) | Ver [`hu13.md`](hu13.md); falta la cuenta huérfana (P-02). |
| HU_14 Registrar parcela | Hecha | Actualizar el MER y avisar al Equipo 1 (equipo, Juan José). |
| **HU_08 Actividad productiva** | **Por empezar (Simon)** | Después de P-01…P-04. Diseño en «HU_08». |
| HU_09–12, SALIDA | Compañero | — |

## Ramas (locales de Simon al 21/10/2026, aún no subidas)

| Rama | Contenido | Siguiente paso |
| --- | --- | --- |
| `fix/pruebas-android-y-backend` | Commit `fix: dejar en verde las pruebas…` (6 archivos). | PR a `develop`. |
| `fix/hu13-documento-unico` (sobre la anterior) | HU_13: documento único (backend, migración `0007`, app, pruebas) + `CLAUDE.md`, `estado-proyecto.md`, `avisos-equipo.md`, `hu13.md`. | PR a `develop` **después** del anterior; migrar Neon antes del merge a `main` (A-02). |
| `feat/hu08-actividad-productiva` | Vacía (creada desde `develop`). | Rehacer sobre `develop` cuando entren los dos PR anteriores. |

Pruebas al 21/10/2026 en `fix/hu13-documento-unico`: **backend 95, Android 113, todas en verde**; lint sin errores.

## Pendientes, en orden (antes de HU_08)

| # | Pendiente | Estado |
| --- | --- | --- |
| P-01 | HU_13: rechazar documento repetido | **Hecho** (rama `fix/hu13-documento-unico`) |
| P-02 | Robustez del registro: (a) tiempo de espera de 30 s frente al despertar de Render (≈ 1 min) en registro y parcelas; (b) cuenta huérfana (Firebase sin perfil → 403 para siempre): al iniciar sesión, si `GET /users/me` da 404, completar el perfil | Por hacer |
| P-03 | AJUSTE: registrar correcciones (positivas y negativas) con motivo; backend `SUPPORTED_TYPES`, app `MovementType.REGISTRABLE`, historial (`±`) y resumen RT_10. Coordinar con SALIDA (A-05) | Por hacer |
| P-04 | Documentación y calidad: README raíz y `docs/arquitectura/README.md` desactualizados (dicen «HU_04 con datos demo»); `hu01/03/06` con secciones «Pendiente» obsoletas; quitar «pendiente de compilar» de `hu02/05/07`; test de 403 por rol (RT_13: hoy solo existe PRODUCTOR); decidir si `.idea/` deja de versionarse | Por hacer |
| P-05 | HU_14: actualizar `docs/modelo-datos/MERv2.drawio` (aforamiento → `id_parcela`; `id_actividad` opcional en movimientos) y avisar al Equipo 1 que usarán el UUID de parcela y hectáreas | Del equipo (Juan José) |
| P-06 | Verificar manualmente en emulador/celular los criterios de HU_01–07 (modo avión, etc.): nunca se confirmaron | Por hacer (requiere `google-services.json`) |
| P-07 | Historial y saldos solo del dispositivo: al reinstalar o cambiar de teléfono se pierden (existe `GET /api/v1/inventory`, la app no lo usa) | Decisión pendiente |

## HU_08: diseño acordado (aún sin implementar)

- **Tipos de actividad** (mockup): Fertilización, Siembra, Cosecha, Aplicación de insumo, Control
  fitosanitario, Otra actividad. **Exige ciclo activo** en la parcela.
- **Formulario** (pestaña **Actividades** en la barra inferior): tipo, parcela (muestra su ciclo activo),
  fecha, observación opcional y bloque opcional **«Insumos usados»** (insumo + cantidad, varias filas). Todo
  se guarda en **una transacción de Room**: la actividad y un CONSUMO por cada insumo, con `activityId`,
  `parcelId` y el `cycleId` de la actividad; si algún insumo no alcanza, no se guarda nada.
- **Room versión 4:** tabla `activities` (id, parcelId, cycleId, type, date, observation, createdAt,
  updatedAt, syncStatus; **sin FK a parcels**) y `movements.activityId` nullable. `AutoMigration(3 → 4)`.
- **Backend:** migración `0008`: tabla `activities` y `movements.activity_id`; `POST /api/v1/activities`
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

- **2026-10-21 (Simon + Claude):** se reconstruyó el contexto del repo (flujo de ramas, Render + Neon, archivos
  privados). Línea base: 1 prueba del backend y 30 de Android en rojo desde HU_14, sin culpa del entorno →
  arregladas (`fix/pruebas-android-y-backend`). Análisis de pendientes de HU_01–07, 13 y 14 (tabla de
  arriba). HU_13: documento repetido ya se rechaza (backend + migración `0007` verificada en un PostgreSQL
  temporal + app; 16 pruebas nuevas del backend y 4 de la app). Se crearon `CLAUDE.md`, este archivo y `avisos-equipo.md`. Siguiente:
  P-02 (robustez del registro), luego P-03 (AJUSTE), P-04 (documentación) y HU_08.
