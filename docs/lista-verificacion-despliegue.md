# Lista de verificación: de `develop` a producción (Render + Neon)

> **Ejecutada el 25/10/2026:** Neon quedó en `0009` y `develop` se mezcló a `main` (PR #22). La lista se conserva como procedimiento para las próximas migraciones (`0010`).
>
> Estado antes de ejecutarla, al **25/10/2026**: los PR #15 a #19 ya están mezclados en `develop` (HU_13 documento único, cuentas sin perfil,
> ajustes, HU_08 y documentación). **`main` sigue en el merge del PR #13 y Neon en la migración `0005`.** Esta lista lleva
> lo que está en `develop` a producción sin romperla. Quien la ejecuta necesita la `DATABASE_URL` real de Neon (secreto:
> no se pega en chats, commits ni capturas).

## Qué cambia en producción

| Capa | Cambio | Riesgo |
| --- | --- | --- |
| Neon | Migraciones `0007` (documento único), `0008` (sentido de ajustes) y `0009` (actividades) | Medio: si el código llega antes, **todas** las rutas autenticadas dan 500. |
| Render | Código nuevo (`/activities`, ajustes, rol PRODUCTOR, documento único) | Bajo si Neon ya está migrada. Sin variables de entorno nuevas. |
| App | Room v5 (migración automática), pestaña Actividades, ajustes | La instalación **sobre** la anterior conserva los datos. |

La migración `0007` rellena `users.document_key`: la cuenta más antigua con un documento repetido conserva la clave y las
demás quedan en `NULL` (no se borra ni se bloquea a nadie). `0008` y `0009` solo agregan columnas/tablas nulas o nuevas.
**El esquema nuevo es compatible hacia atrás**: si hubiera que volver el código de Render a la versión anterior, seguiría
funcionando sobre la base ya migrada. Por eso migrar primero es seguro.

## 1. Antes de migrar (en tu equipo)

- [ ] `git switch develop; git pull origin develop; git status` limpio y `git log --oneline -3` muestra el merge del #19.
- [ ] Pruebas del backend: `cd backend; .venv\Scripts\python.exe -m pytest -q` → **146 pruebas** en verde.
- [ ] `.venv\Scripts\python.exe -m alembic heads` → **una sola cabeza: `0009`**.
- [ ] Pruebas de la app: `cd android-app; .\gradlew.bat testDebugUnitTest` → **210 pruebas** en verde (≈ 1–6 min).
- [ ] Prueba manual en celular con [`guia-prueba-celular.md`](guia-prueba-celular.md) contra una **base temporal** (no Neon).
      Cualquier fallo se corrige en `develop` **antes** de tocar producción.

## 2. Ensayo en una rama de desarrollo de Neon

En la consola de Neon: crea una rama nueva a partir de la principal (es una copia instantánea con los datos reales).

```powershell
cd backend
$env:DATABASE_URL = "<cadena de la RAMA DE DESARROLLO, con postgresql+psycopg:// y sslmode=require>"
.venv\Scripts\python.exe -m alembic current      # debe decir 0005
.venv\Scripts\python.exe -m alembic upgrade head  # sube 0007, 0008, 0009
.venv\Scripts\python.exe -m alembic current      # debe decir 0009 (head)
.venv\Scripts\python.exe -m alembic check        # "No new upgrade operations detected"
```

- [ ] `alembic current` pasó de `0005` a `0009`, sin errores.
- [ ] En el SQL Editor de la rama: `SELECT COUNT(*) FROM users WHERE document_key IS NULL;` — si es mayor que 0 hay cuentas
      antiguas con documento repetido (es esperado y no rompe nada).
- [ ] Opcional: apunta un backend local a esa rama (`uvicorn`) y haz un registro y un movimiento desde la app.
- [ ] Borra la variable: `Remove-Item Env:DATABASE_URL`.

## 3. Migrar la base compartida (producción)

- [ ] Avisa en el grupo: «voy a migrar Neon, no toquen la base 5 minutos».
- [ ] Opcional pero recomendado: crea una **rama de respaldo** en Neon antes (punto de restauración).
- [ ] Repite los cuatro comandos del paso 2 con la `DATABASE_URL` de la **rama principal**.
- [ ] `alembic current` → `0009 (head)`.
- [ ] `SELECT version_num FROM alembic_version;` → `0009`.
- [ ] Borra la variable de entorno al terminar.

Si algo falla a mitad: `alembic downgrade 0005` es reversible (cada migración tiene su bajada); si no basta, restaura la
rama de respaldo. No sigas con el paso 4.

## 4. Pasar a `main` y desplegar

Solo con Neon en `0009`:

- [ ] Abre el PR **`develop` → `main`** (título «Develop», como los anteriores) y verifica en GitHub que no hay conflictos.
- [ ] Mézclalo. Render redespliega solo (tarda varios minutos).
- [ ] Mira el *Deploy log* de Render hasta «Live» y que no haya errores de arranque.

## 5. Verificación posterior

- [ ] `https://uniban-app-suministros.onrender.com/health` → `{"status":"ok"}` (la primera vez tarda ≈ 1 min).
- [ ] `https://uniban-app-suministros.onrender.com/docs` lista `/activities` y `/movements`.
- [ ] Desde la app apuntando a Render: iniciar sesión, registrar una parcela, un consumo y una actividad; el contador de
      pendientes llega a 0.
- [ ] Neon: `SELECT COUNT(*) FROM activities;` y `SELECT COUNT(*) FROM movements WHERE activity_id IS NOT NULL;`.
- [ ] Si `/openapi.json` no lista las rutas nuevas, Render sigue con el código anterior: pide un *Manual Deploy* a quien administra el servicio.
- [ ] Aviso al grupo: producción actualizada. Actualiza [`estado-proyecto.md`](estado-proyecto.md) (Neon en `0009`, `main` al día)
      y [`despliegue-render.md`](despliegue-render.md) (donde dice «migración `0005`»).

## 6. Después

- La integración con los Equipos 1 y 2 ([`guia-integracion-equipos.md`](guia-integracion-equipos.md)) usa la migración
  **`0010`** para aforamientos (la `0006` se descartó: ver A-01). El compañero parte de `develop`.
- Cada migración futura repite esta lista: **ensayo en rama de Neon → compartida → `main`**.
