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
| 1 | Compañero de HU_09 (aforamientos) | Tu migración será la `0006`; la de HU_13 es la `0007`. Antes de tu PR, revisa `alembic heads` (ver A-01). | A-01 |
| 2 | Quien despliegue (Juan José / Juan Sebastián) | Aplicar `0007` en Neon **antes** de hacer merge a `main`. Si no, todas las rutas autenticadas dan 500. | A-02 |
| 3 | Compañero de HU_09–12 y Salidas | Hacer `git pull origin develop`: el PR de pruebas (#14) ya entró; sin él hay 30 pruebas de Android en rojo que no son tuyas. | A-03 (resuelto en `develop`) |
| 4 | Todos | Reglas nuevas para escribir pruebas (A-03) y para tocar archivos compartidos (A-04). | A-03, A-04 |
| 5 | Quien haga SALIDA | Coordinar con Simon: AJUSTE y SALIDA tocan los mismos archivos. | A-05 |
| 6 | Todos los que toquen autenticación o pruebas | `AuthRepository` tiene miembros nuevos; la espera HTTP ahora es de 90 s. Haz `git pull origin develop` y actualiza tus fakes. | A-06 |

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
- **AJUSTE:** lo hará Simon cuando cierre HU_13 y los pendientes de robustez.
- Ambos cambian `data/local/MovementType.kt`, `ui/inventory/EntryScreen.kt` / `EntryViewModel.kt`,
  `data/InventoryRepository.kt`, `backend/app/schemas/movements.py` y `services/movement_sync.py`.

**Qué hacer:** avisen en el grupo quién toca primero esos archivos; el segundo hace rebase sobre
`develop`. Definir juntos en qué se diferencia una SALIDA de un CONSUMO (pregunta abierta de
[`hu05.md`](hu05.md)) antes de programarla.

## A-04 · 2026-10-21 · Simon · Archivos que casi todos los cambios tocan

Para evitar conflictos al integrar, tengan presente que estos archivos los modifican varias HU:

| Archivo | Quién lo cambia |
| --- | --- |
| `android-app/.../data/local/InventoryDatabase.kt` (versión de Room y `AutoMigration`) | HU_08 (Simon) pasará de la versión 3 a la **4**. Cualquier otra HU que agregue tablas o columnas locales debe tomar la versión siguiente **después** de que entre la anterior, y subir el `N.json` de `app/schemas`. |
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
4. **No hagas merge con pruebas rojas.** Antes de abrir el PR:
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
| `0006` | HU_09 (aforamientos, compañero) | **Reservada**, aún no existe | `0005` |
| `0007` | HU_13 (documento único, Simon) | En PR | `0005` **provisional** |
| `0008` | HU_08 (actividades, Simon) | Por hacer | `0007` |

**Qué hacer:**

- Antes de crear una migración: `git pull origin develop`, `cd backend; .venv\Scripts\python.exe -m alembic heads`
  (debe mostrar **un** resultado) y toma el siguiente número libre.
- Si al hacer tu PR `develop` ya tiene otra migración que parte de la misma que la tuya, **cambia tu
  `down_revision`** al id de la última y renumera si hace falta. Quien entra segundo re-parenta.
- Al integrar `0006` y `0007`: la segunda en entrar pone `down_revision` = id de la primera (la `0007`
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
