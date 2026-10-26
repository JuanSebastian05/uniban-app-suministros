# Avisos para el equipo

> **Léelo antes de empezar a trabajar** (y pásaselo a tu IA junto con [`CLAUDE.md`](../CLAUDE.md) y
> [`estado-proyecto.md`](estado-proyecto.md)). Aquí van los cambios y acuerdos que afectan a más de una
> persona. Cada aviso trae fecha, quién lo origina y **qué tienes que hacer tú**.
>
> **Regla para quien agregue un aviso:** añádelo arriba (el más reciente primero), con fecha, autor y
> acción requerida; no borres los anteriores, márcalos «Resuelto» cuando dejen de aplicar. Si tu PR
> cambia algo de lo que aquí se dice, actualiza este archivo en el mismo PR.

## Resumen: acciones pendientes de otros

| # | Quién | Qué | Aviso |
| --- | --- | --- | --- |
| 1 | Compañero de HU_09 (aforamientos) | Tu migración de aforamientos será la **`0010`** (down_revision `0009`); la `0006` ya no se usa (A-01). Antes de tu PR, revisa `alembic heads`. | A-01 |
| 2 | Quien despliegue (Juan José / Juan Sebastián) | Aplicar `0007` en Neon **antes** de hacer merge a `main`. Si no, todas las rutas autenticadas dan 500. | A-02 |
| 3 | Compañero de HU_09–12 y Salidas | Hacer `git pull origin develop`: el PR de pruebas (#14) ya entró; sin él hay 30 pruebas de Android en rojo que no son tuyas. | A-03 (resuelto en `develop`) |
| 4 | Todos | Reglas nuevas para escribir pruebas (A-03) y para tocar archivos compartidos (A-04). | A-03, A-04 |
| 5 | Quien haga SALIDA | Coordinar con Simon: AJUSTE y SALIDA tocan los mismos archivos. | A-05 |
| 6 | Todos los que toquen autenticación o pruebas | `AuthRepository` tiene miembros nuevos; la espera HTTP ahora es de 90 s. Haz `git pull origin develop` y actualiza tus fakes. | A-06 |
| 8 | Simon (Render y Neon) | Generar `SERVICE_API_KEY` y configurarla en Render; ensayar y aplicar la migración **`0010`** en Neon **antes** del merge a `main`; entregar la clave al Equipo 1 por un canal privado. | A-08 |
| 7 | Compañero de HU_09–12 y SALIDA | Ya existen AJUSTE (Room v4, `0008`) y HU_08 (Room v5, `0009`, pestaña Actividades): parte de esas ramas y no reutilices esos números. HU_10 debe leer también el estado de las actividades. | A-07 |

---

## A-08 · 2026-10-26 · Integración con el Equipo 1 (backend): clave de servicio y aforamientos

Rama `feat/integracion-equipo1` (PR a `develop`). Lo que cambia y te afecta:

- **Variable nueva `SERVICE_API_KEY`** (Render y tu `backend/.env`; línea vacía en `.env.example`). Sin ella, las rutas
  de servicio responden `503`. Nunca va en el repositorio.
- **Rutas nuevas para el Equipo 1** (cabecera `X-API-Key`, sin Firebase): `GET /api/v1/service/parcels` y
  `POST /api/v1/aforamientos`. Contrato en [`contrato-equipo1.md`](contrato-equipo1.md).
- **Migración `0010`** (tabla `aforamientos`, `down_revision` `0009`). Verificada en un PostgreSQL temporal
  (upgrade, `alembic check`, downgrade y upgrade); **no aplicada en Neon**. Procedimiento: rama de ensayo → compartida →
  merge a `main` (A-02).
- Backend: 146 → 213 pruebas en verde.
- **Qué hacer:** Simon, generar y configurar la clave, aplicar `0010` y entregar la clave al Equipo 1. Quien haga la
  migración de `recommendations`: parte de la `0010` (será la `0011`). HU_09 (app, Room v6) se entrega en otro PR.

## A-07 · 2026-10-24 · Simon · Qué dejan HU_08 y AJUSTE para HU_09–12 y SALIDA

Cuando entren los PR de ajustes (#17) y de HU_08, esto ya existe y te afecta:

- **Base:** Room **v5** (`activities`, `movements.activityId`, `movements.adjustDirection`) y Alembic **`0009`** (cabeza).
  La migración de aforamientos será la `0010` (la `0006` quedó sin usar: ver A-01).
- **Pestañas:** `MainTab` ya tiene Parcelas, Inventario y **Actividades** (`MainBottomBar.kt`). HU_09 y HU_12 agregan
  secciones al detalle de la parcela (`ParcelDetailScreen.kt`, que ahora tiene «Actividades» e «Insumos consumidos»).
- **Sincronización (HU_10):** `MovementSyncRepository` envía primero las actividades (`ActivityApi`) y luego los
  movimientos; los estados `PENDING/SYNCING/SYNCED/ERROR` están en **dos tablas** (`movements` y `activities`) y
  `InventoryDao.observePendingCount` ya suma ambas. Para «última sincronización» y «errores visibles» consulta las dos
  (`ActivityDao.findSyncable` y `InventoryDao.findSyncable`).
- **SALIDA:** el backend ya tiene `_subtracts()` y `SUPPORTED_TYPES`; en la app agrega el tipo a
  `MovementType.REGISTRABLE` y a `subtracts()`. El historial (`MovementHistory.summary`) ya la resta en «± Otros».
- **Integración con los Equipos 1 y 2** (prioridad pedida el 25/10): guía completa en [`guia-integracion-equipos.md`](guia-integracion-equipos.md); contrato del Equipo 2 en [`contrato-equipo2.md`](contrato-equipo2.md).

---

## A-06 · 2026-10-21 · Simon · `AuthRepository` creció y la app despierta el servidor (P-02)

- **Contrato nuevo.** `AuthRepository` ahora exige `profileState: StateFlow<ProfileState>`,
  `suspend fun refreshProfile()` y `suspend fun completeProfile(name, document, phone)`. Si tu rama tiene un fake
  propio que implementa la interfaz, **no compilará** hasta que lo completes; usa `FakeAuthRepository` de
  `src/test` (campos `profileOnRefresh`, `onCompleteProfile`, `refreshProfileCalls`, `completeProfileCalls`).
- **Navegación.** `AppNavHost` ya no solo mira la sesión: si el backend responde `404` a `GET /users/me`
  (cuenta de Firebase sin perfil), el `SessionGuard` lleva a la ruta `AppDestinations.COMPLETE_PROFILE`. Si
  agregas rutas o cambias el guard, respeta esa condición. Un perfil «desconocido» (sin red, servidor dormido)
  **nunca** bloquea la app: no lo cambies a bloqueante, rompería HU_01 (uso sin conexión).
- **Red.** `NetworkModule` espera hasta **90 s** la respuesta (Render tarda ≈ 1 min en despertar) y
  `UnibanApplication` manda un `GET /health` al abrir. Un endpoint nuevo que el productor espere en pantalla
  debe mostrar el aviso `R.string.server_wakeup_hint` mientras guarda. Las llamadas del `SyncWorker` no necesitan
  cambios.
- **Sin migración** ni cambios en el backend.

---

## A-05 · 2026-10-21 · Simon · SALIDA y AJUSTE tocan los mismos archivos

HU_05 (criterios 3 y 5) y HU_07 (criterios 1 y 3) dan por hecho que existen **AJUSTE** (correcciones) y
**SALIDA**, pero hoy ninguno se puede registrar: la app solo ofrece Entrada, Consumo y Pérdida
(`MovementType.REGISTRABLE`) y el backend rechaza otros tipos con 409 (`SUPPORTED_TYPES` en
`services/movement_sync.py`).

- **SALIDA:** la hace el compañero (está en el plan de la semana).
- **AJUSTE:** **ya está hecho** (24/10, rama `feat/ajuste-movimientos`, ver [`hu05.md`](hu05.md#ajustes-ajuste-24102026)).
  Para SALIDA: el backend ya tiene `SUPPORTED_TYPES`, `_subtracts()` y el campo `adjustDirection` como ejemplo de un
  tipo nuevo; en la app, agrega el tipo a `MovementType.REGISTRABLE` (ya incluye AJUSTE) y revisa `subtracts()`.
  **Tu rama debe partir de la de ajustes** (o de `develop` cuando entre) porque ambos tocan los mismos archivos y
  Room ya está en la versión 4: **no uses la 4 para otra cosa**.
- Ambos cambian `data/local/MovementType.kt`, `ui/inventory/EntryScreen.kt` / `EntryViewModel.kt`,
  `data/InventoryRepository.kt`, `backend/app/schemas/movements.py` y `services/movement_sync.py`.

**Qué hacer:** avisen en el grupo quién toca primero esos archivos; el segundo hace rebase sobre
`develop`. Definir juntos en qué se diferencia una SALIDA de un CONSUMO (pregunta abierta de
[`hu05.md`](hu05.md)) antes de programarla.

## A-04 · 2026-10-21 · Simon · Archivos que casi todos los cambios tocan

Para evitar conflictos al integrar, tengan presente que estos archivos los modifican varias HU:

| Archivo | Quién lo cambia |
| --- | --- |
| `android-app/.../data/local/InventoryDatabase.kt` (versión de Room y `AutoMigration`) | Los ajustes (Simon) ya usan la versión **4** y HU_08 (Simon) la **5**: **la siguiente libre es la 6** (toma la versión después de que entren estos PR). Cualquier otra HU que agregue tablas o columnas locales debe tomar la versión siguiente **después** de que entre la anterior, y subir el `N.json` de `app/schemas`. |
| `ui/navigation/AppNavHost.kt`, `AppDestinations.kt`, `MainBottomBar.kt` | HU_08 agrega la pestaña **Actividades**; HU_09 y HU_12 agregan secciones y rutas. |
| `ui/parcels/ParcelDetailScreen.kt` | HU_08 (actividades), HU_09 (aforamiento), HU_12 (recomendaciones): cada una es una sección nueva. |
| `data/local/InventoryDao.kt` → `observePendingCount` | HU_10 (estado de sincronización) y HU_08 (las actividades también quedan pendientes): el contador debe sumar movimientos **y** actividades. |
| `sync/SyncWorker.kt`, `MovementSyncRepository.kt` | HU_08 sincroniza actividades **antes** que los movimientos; HU_10 lee los estados. |
| `backend/migrations/versions/` | Ver A-01. |

**Qué hacer:** PR pequeños y frecuentes, `git pull origin develop` antes de empezar y antes de abrir el PR.
Regla que ya rompió una HU: **una tabla local nueva no debe tener clave foránea hacia `parcels`**:
`ParcelRepository.refresh()` ejecuta `deleteAllParcels()` y una FK haría fallar ese refresco en silencio
(o borraría en cascada datos pendientes). Los movimientos guardan `parcelId` y `cycleId` como columnas
simples por esa razón.

## A-03 · 2026-10-21 · Simon · Las pruebas estaban rojas; reglas nuevas

Al correr todo por primera vez desde HU_14 había **1 prueba del backend y 30 de Android en rojo** que
ningún PR había confirmado (los `hu0X.md` decían «pendiente de gradlew testDebugUnitTest»). Causas y
reglas:

1. Toda clase Robolectric que use Room o el contexto debe llevar
   `@Config(sdk = [28], manifest = Config.NONE, application = Application::class)`. Sin
   `application = Application::class`, Robolectric arranca `UnibanApplication`, que llama a WorkManager,
   y falla.
2. Las pruebas de pantalla (Compose) llevan además `qualifiers = "w411dp-h891dp"`; con la pantalla
   pequeña por defecto, los botones quedan fuera de vista.
3. Si cambias un texto de un botón, busca las pruebas que lo usan (`EntryFlowTest` buscaba
   «+ Registrar entrada» desde antes de HU_05).
4. **ViewModel con `viewModelScope`:** pruébalo en JVM puro (sin Robolectric), con un repositorio falso y
   `Dispatchers.setMain(UnconfinedTestDispatcher())`, como `RegisterViewModelTest` y `ActivityFormTest`. Probarlo con
   Robolectric + Room real dejó colgadas (Compose «nunca queda ocioso», 60 s por prueba) las pruebas de pantalla que
   corrían después (24/10/2026). Si una prueba de pantalla se cuelga solo en la corrida completa, sospecha de esto.
5. **No hagas merge con pruebas rojas.** Antes de abrir el PR:
   `cd android-app; .\gradlew.bat testDebugUnitTest` y `cd backend; .venv\Scripts\python.exe -m pytest -q`.

**Qué hacer:** el PR `fix: dejar en verde las pruebas…` (#14) **ya está en `develop`** (resuelto el
21/10/2026): haz `git pull origin develop` y esas pruebas dejan de fallar en tu rama. Si no lo haces, fallarán
sin que sea culpa tuya.

## A-02 · 2026-10-21 · Simon · Orden de despliegue: migrar Neon **antes** del merge a `main`

Render redespliega con cada push a `main` y **no ejecuta migraciones**. Desde HU_13 el modelo `User`
lee la columna `users.document_key`: si el código llega a Render antes que la migración `0007`, **todas**
las rutas autenticadas (`get_current_user`) responden 500, no solo el registro.

**Qué hacer (quien haga el merge a `main`):**

1. Aplicar la migración en una **rama de desarrollo de Neon** y comprobar (`alembic upgrade head`).
2. Aplicarla en la base compartida (`alembic upgrade head` con la `DATABASE_URL` real).
3. Solo entonces hacer merge a `main` y esperar el redespliegue; probar `/health` y un registro.

Esto aplica a **toda** migración nueva, no solo a la `0007`.

## A-01 · 2026-10-21 · Simon · Numeración de migraciones de Alembic

`alembic upgrade head` falla con «Multiple head revisions» si dos migraciones parten de la misma.

| Id | HU | Estado | `down_revision` |
| --- | --- | --- | --- |
| `0005` | HU_05 | En `develop` | `0004` |
| `0006` | — | **Descartada** (ver nota): no se usa | — |
| `0010` | Aforamientos (Equipo 1 + HU_09) | En `feat/integracion-equipo1` (PR a `develop`); **sin aplicar en Neon** | `0009` |
| `0011` | `recommendations` (HU_12) | Por hacer | `0010` |
| `0007` | HU_13 (documento único, Simon) | En Neon (25/10) | `0005` **provisional** |
| `0008` | Ajustes de inventario (Simon, 24/10) | En Neon (25/10) | `0007` |
| `0009` | HU_08 (actividades, Simon, 24/10) | En Neon (25/10) | `0008` |

**Qué hacer:**

- Antes de crear una migración: `git pull origin develop`, `cd backend; .venv\Scripts\python.exe -m alembic heads`
  (debe mostrar **un** resultado) y toma el siguiente número libre.
- Si al hacer tu PR `develop` ya tiene otra migración que parte de la misma que la tuya, **cambia tu
  `down_revision`** al id de la última y renumera si hace falta. Quien entra segundo re-parenta.
- **Nota (25/10):** la `0006` se descarta porque Neon pasará de la `0005` a la `0009`; una migración intermedia nueva nunca se ejecutaría allí. Las migraciones nuevas parten de la cabeza actual (`0010` en adelante).
- (Histórico) Al integrar `0006` y `0007`: la segunda en entrar pone `down_revision` = id de la primera (la `0007`
  hoy apunta a `0005` a propósito, porque la `0006` todavía no existe). Los ids no tienen que ser
  contiguos.
- Cómo probar una migración sin tocar Neon: ver «Verificar una migración» en [`CLAUDE.md`](../CLAUDE.md).
- `migrations/env.py` solo funciona contra una base real (no tiene modo offline).

## Decisiones de producto cerradas (21/10/2026, Simon)

- **HU_08:** los tipos de actividad son los del mockup (Fertilización, Siembra, Cosecha, Aplicación de
  insumo, Control fitosanitario, Otra actividad) y la actividad **exige un ciclo activo** en la parcela
  (la HU lo dice: «elijo parcela y ciclo activo»; y el MER cuelga ACTIVIDAD de CICLO).
- **HU_13:** el documento se compara sin mayúsculas, puntos, espacios ni guiones. Las cuentas antiguas
  con documento repetido no se tocan (ver [`hu13.md`](hu13.md)).
- **Pendientes antes de HU_08:** primero se cierra lo pendiente de HU_01–07, 13 y 14 (ver
  [`estado-proyecto.md`](estado-proyecto.md)).

## Archivos privados y cuidado con los git anidados

- `backend/.env` (la `DATABASE_URL` de Neon es un secreto), `android-app/app/google-services.json` y
  `android-app/local.properties` **nunca** se suben (están en `.gitignore`). Se comparten por la carpeta
  privada del equipo; ver `LEEME_archivos_privados.txt`.
- No corras migraciones contra la base compartida de Neon para probar: usa una rama de desarrollo de Neon
  o un PostgreSQL temporal.
- Si tu carpeta de trabajo contiene este repo **dentro de otra carpeta que también es un repo git**,
  no hagas `git add .` en la carpeta de afuera: podrías rastrear archivos privados.
