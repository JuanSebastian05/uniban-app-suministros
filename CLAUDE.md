# CLAUDE.md — uniban-app-suministros (Equipo 3, Proyecto Integrador I, UdeA)

Instrucciones para cualquier IA o persona que trabaje en este repo. Responde y documenta **en español**
(identificadores de código en inglés, como el resto del proyecto). Si este archivo y el repo se
contradicen, gana el repo: verifica y corrige este archivo.

## Antes de empezar (obligatorio)

1. Lee [`docs/avisos-equipo.md`](docs/avisos-equipo.md) y [`docs/estado-proyecto.md`](docs/estado-proyecto.md).
2. Revisa `git status`, la rama actual y el código pertinente antes de concluir que una nota documental sigue vigente.
3. No cambies de rama ni actualices desde un remoto sin comprobar primero si hay trabajo local que preservar.
   Sigue el flujo de ramas acordado por el usuario/equipo para la tarea actual.

## Qué es

App móvil Android **offline-first** para que pequeños productores de plátano vinculados a Unibán registren
suministros, inventario, consumos y actividades; sincroniza con un backend central. Somos el **Equipo 3**
del proyecto Plátano–Unibán. El Equipo 1 entrega aforamientos (por parcela y fecha) y el Equipo 2 hace
logística y recomendaciones. **La app solo habla con nuestro backend**; los otros equipos también.

| Capa | Tecnología |
| --- | --- |
| App (`android-app/`) | Kotlin, Jetpack Compose, MVVM + Repository, Room (SQLite), Retrofit, WorkManager. Paquete `co.edu.udea.uniban.suministros`. |
| Identidad | Firebase Authentication **solo para identidad**; la autorización y todos los datos viven en el backend. |
| Backend (`backend/`) | FastAPI, SQLAlchemy 2, Alembic, PostgreSQL (Neon), API REST `/api/v1`, pytest. |
| Despliegue | Render (`main`, `backend/`) + Neon. URL: `https://uniban-app-suministros.onrender.com/` (se duerme a los 15 min; la primera petición tarda ≈ 1 min). |

No agregues tecnologías fuera de esta lista sin acordarlo con el equipo (Cierre de decisiones v1.0).

## Cómo se trabaja

- **Ramas:** `feat/huNN-descripcion` o `fix/descripcion` desde `develop` → PR a `develop` → PR periódico
  `develop` → `main`. Los PR los revisa y mergea otra persona del equipo (hoy Juan José o Juan Sebastián).
- **Commits:** uno por HU (o por arreglo), mensaje `feat: HU_0X descripción`, `fix: …`, `docs: …`,
  que incluye **código + pruebas + `docs/huNN.md` + esquema de Room exportado**. Nunca `push --force`
  ni «Add files via upload» de GitHub. **No hagas commit ni push si el usuario no lo pidió.**
- **Documentación viva (en el mismo PR):** cada HU tiene `docs/huNN.md` (historia, criterios y cómo se
  cumplen, decisiones, modelo, API, archivos, demostración manual, pendiente). Además actualiza
  [`docs/estado-proyecto.md`](docs/estado-proyecto.md) (estado, bitácora) y, si afecta a otros,
  [`docs/avisos-equipo.md`](docs/avisos-equipo.md). Tu contexto es finito: lo que no esté escrito en el
  repo se pierde.
- **Pruebas:** nada se mergea con pruebas rojas. Toda HU nueva lleva pruebas y una demostración manual.
- **Verifica antes de afirmar:** di qué corriste y qué no. Compilar no es probar.

## Comandos (Windows, PowerShell)

```powershell
# Android (JDK 17 o 21, según el Gradle JDK configurado)
cd android-app
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"   # ajusta a tu JDK 17
.\gradlew.bat testDebugUnitTest          # todas las pruebas unitarias (≈ 1 min)
.\gradlew.bat assembleDebug lintDebug    # compila y revisa

# Backend
cd backend
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m pytest -q                      # SQLite en memoria, no necesita Neon
.venv\Scripts\python.exe -m alembic heads                  # debe mostrar UNA sola cabeza
```

### Verificar una migración sin tocar Neon (PostgreSQL temporal)

`migrations/env.py` solo corre contra una base real. Con PostgreSQL instalado, levanta uno desechable:

```powershell
$bin = "C:\Program Files\PostgreSQL\17\bin"; $d = "$env:TEMP\pgtemp"
& "$bin\initdb.exe" -D $d -U postgres -A trust -E UTF8 --locale=C
Start-Process "$bin\pg_ctl.exe" -ArgumentList "-D `"$d`" -o `"-p 5433`" -l `"$d.log`" start" -WindowStyle Hidden
& "$bin\psql.exe" -h localhost -p 5433 -U postgres -c "CREATE DATABASE uniban_check;"
$env:DATABASE_URL = "postgresql+psycopg://postgres@localhost:5433/uniban_check"
cd backend; .venv\Scripts\python.exe -m alembic upgrade head
.venv\Scripts\python.exe -m alembic check        # sin diferencias entre modelos y base
.venv\Scripts\python.exe -m alembic downgrade -1  # y vuelve a subir: debe ser reversible
# al terminar: pg_ctl stop -D $d  y borra la carpeta
```

## Reglas que no se rompen

- **Cero secretos en el repo** (es público): `backend/.env`, `android-app/app/google-services.json` y
  `android-app/local.properties` están en `.gitignore`. Nunca imprimas ni pegues sus valores.
- **No pruebes migraciones contra la base compartida de Neon.** Ensáyalas en una base temporal o rama de desarrollo;
  para producción consulta el procedimiento vigente en `docs/lista-verificacion-despliegue.md`.
- **Movimientos de inventario:** solo de inserción; las correcciones son AJUSTE; la existencia nunca queda
  negativa; se actualizan en la misma transacción (RT_10). Todo registro nace en el dispositivo con UUID y
  `PENDING`; el backend es idempotente por UUID.
- **Migraciones:** una sola cabeza de Alembic y numeración coordinada con el estado real de la base. El esquema de Room se versiona
  (`AutoMigration` + `app/schemas/.../N.json` en el mismo commit).
- **Tablas locales nuevas sin clave foránea hacia `parcels`** (`ParcelRepository.refresh()` borra y reinserta).
- **Pruebas Robolectric:** `@Config(sdk = [28], manifest = Config.NONE, application = Application::class)`; las de
  pantalla agregan `qualifiers = "w411dp-h891dp"` (ver A-03).
- La UI nunca conoce la red ni el DAO (RT_08): `Compose → ViewModel → Repository → Room/Retrofit`.

## Mapa de la documentación

| Qué | Dónde |
| --- | --- |
| Estado, pendientes, bitácora, por dónde retomar | [`docs/estado-proyecto.md`](docs/estado-proyecto.md) |
| Avisos entre compañeros (migraciones, archivos compartidos, despliegue) | [`docs/avisos-equipo.md`](docs/avisos-equipo.md) |
| Historias de usuario y criterios | [`docs/requisitos/HU_y_Requisitos_Tecnicos_Equipo3.pdf`](docs/requisitos/HU_y_Requisitos_Tecnicos_Equipo3.pdf) |
| Guía de cada HU (qué se hizo y cómo demostrarlo) | `docs/hu01.md` … `docs/hu14.md` |
| Modelo de datos (MER) y mockups | `docs/modelo-datos/MERv2.drawio`, `docs/mockups/MockupsFigma.zip` |
| Despliegue en Render + Neon | [`docs/despliegue-render.md`](docs/despliegue-render.md) |
| Arquitectura | [`docs/arquitectura/README.md`](docs/arquitectura/README.md) |
| Integración con los Equipos 1 y 2 | [`docs/guia-integracion-equipos.md`](docs/guia-integracion-equipos.md) y contratos individuales |

## Trampas conocidas

- Una prueba de ViewModel con Robolectric + Room real + `viewModelScope` deja colgadas las pruebas de Compose que corren
  después (solo se nota en la corrida completa: `AppNotIdleException`, ≈ 10 min). Prueba los ViewModel en JVM puro con
  un repositorio falso (ver `ActivityFormTest`) y `testDebugUnitTest` completo antes de abrir el PR.

- Room genera `app/schemas/.../N.json` al compilar: **súbelo** junto con el cambio de esquema.
- `google-services.json` ausente: la app compila pero no permite iniciar sesión (en debug hay «modo demo»).
- Render se duerme (≈ 1 min en despertar): la app espera hasta 90 s (`NetworkModule.READ_TIMEOUT_SECONDS`) y manda un
  `GET /health` al abrirse (`NetworkModule.warmUp`). No bajes ese tiempo ni lo uses como excusa para reintentos ciegos.
- `AuthRepository` tiene `profileState`/`refreshProfile`/`completeProfile` (cuenta de Firebase sin perfil en el
  backend → pantalla «Complete su perfil»). Cualquier fake o implementación nueva debe cubrirlos (`FakeAuthRepository`).
- El shell `bash` de algunas máquinas Windows no trae `ls`/`head`: usa PowerShell.
- `.idea/` tiene archivos versionados desde antes del `.gitignore`; no los modifiques por accidente.
