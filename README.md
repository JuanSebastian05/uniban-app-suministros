# Uniban App Suministros

Repositorio para la realización del proyecto **Uniban – Proyecto Integrador 1 de la Universidad de Antioquia (UdeA)**.

> **Estado actual: HU_01 (inicio de sesión y registro), HU_02 (consulta de parcelas), HU_03, HU_04, HU_05 (consumos y pérdidas), HU_06, HU_07 (historial) y HU_14 (registro de parcelas) implementadas.**
> La primera entrega se limita a **HU_01, HU_03, HU_04 y HU_06**.

## Objetivo

Aplicación móvil Android **offline-first** para la gestión y trazabilidad de suministros agrícolas
de pequeños productores de plátano vinculados a Unibán (Equipo 3). La app funciona sin conexión
sobre una base local y sincroniza con un backend central cuando hay red.

## Stack

| Capa | Tecnologías |
|------|-------------|
| App móvil | Kotlin, Jetpack Compose, Material 3, Navigation Compose, MVVM (ViewModel), Repository, Room (SQLite), Retrofit, WorkManager |
| Identidad | Firebase Authentication (solo autenticación) |
| Backend | Python, FastAPI, SQLAlchemy 2, OpenAPI automático, PyTest |
| Base de datos central | PostgreSQL |
| Repositorio | Git / GitHub |

## Arquitectura general prevista

```text
Compose (UI) → ViewModel → InventoryRepository → Room (local) / Retrofit (remoto)
                                                     ↓
                                            FastAPI (/api/v1) → PostgreSQL
WorkManager: coordinador de sincronización y reintentos de HU_06.
Firebase Auth: solo identidad.
```

La app **no** se comunica directamente con los Equipos 1 y 2: toda integración pasa por el backend FastAPI.
Detalle en [`docs/arquitectura/README.md`](docs/arquitectura/README.md).

## Estructura del repositorio

```text
.
├── android-app/                 # Proyecto Android (abrir esta carpeta en Android Studio)
│   ├── app/src/main/java/co/edu/udea/uniban/suministros/
│   │   ├── core/network/        # Cliente HTTP base
│   │   ├── data/                # InventoryRepository, Quantity, auth (Firebase) y local (Room)
│   │   ├── ui/                  # navigation | theme | auth | inventory
│   │   ├── MainActivity.kt
│   │   └── UnibanApplication.kt # Instancia compartida del repositorio
│   ├── gradle/libs.versions.toml
│   └── local.properties.example
├── backend/                     # API FastAPI
│   ├── app/                     # main.py, api/v1, core, db, models, schemas
│   ├── tests/
│   ├── requirements.txt
│   └── .env.example
├── docs/
│   ├── arquitectura/README.md
│   ├── avances/AnteproyectoInicial-PI1-Equipo3-Uniban.pdf
│   ├── mockups/MockupsFigma.zip
│   ├── modelo-datos/MERv2.drawio
│   └── requisitos/HU_y_Requisitos_Tecnicos_Equipo3.pdf
├── .gitignore
└── README.md
```

## Cómo levantar la app Android

**Requisitos:** Android Studio, JDK 17 o 21 (seleccionar uno compatible en Gradle JDK),
Android SDK 35, emulador o dispositivo con Android 8.0 (API 26) o superior.

1. En Android Studio: **File → Open** → seleccionar la carpeta `android-app/`.
2. (Opcional) Copiar `android-app/local.properties.example` a `android-app/local.properties` y ajustar `api.baseUrl`.
   Por defecto se usa `http://10.0.2.2:8000/` (backend local visto desde el emulador).
   Para usar el backend desplegado: `api.baseUrl=https://uniban-app-suministros.onrender.com/` (ver [`docs/despliegue-render.md`](docs/despliegue-render.md)).
3. Para iniciar sesión, colocar `google-services.json` (consola de Firebase) en `android-app/app/`.
   Las cuentas se crean desde la app con **Crear cuenta** (requiere el backend con `FIREBASE_PROJECT_ID`
   y la migración `0002`); ver [`docs/hu01.md`](docs/hu01.md#registro-de-usuarios). El proyecto compila sin el archivo, que **no se sube** al repositorio.
   Sin él, el build **debug** muestra el aviso de Firebase sin configurar y el botón
   «Continuar en modo demo», que no existe en release.
4. Sincronizar Gradle y ejecutar la configuración `app`. La primera pantalla es el **inicio de sesión**;
   tras autenticarse (o entrar en modo demo) se abre **Inventario** con seis insumos demo.
   Con una sesión previa, la app abre directo en Inventario aun sin conexión. La barra inferior
   cambia entre **Parcelas** e **Inventario**.

Por línea de comandos (desde `android-app/`): `./gradlew assembleDebug` y `./gradlew testDebugUnitTest`
(en Windows: `gradlew.bat assembleDebug`).

## Cómo levantar el backend

**Requisitos:** Python 3.11+, PostgreSQL 15+ (solo cuando existan endpoints que usen la base).

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate            # Windows  (Linux/Mac: source .venv/bin/activate)
pip install -r requirements.txt
copy .env.example .env            # Linux/Mac: cp .env.example .env  → editar valores
uvicorn app.main:app --reload
```

- `GET http://localhost:8000/health` → `{"status": "ok"}` (endpoint técnico, no es una HU)
- Documentación OpenAPI: `http://localhost:8000/docs`
- Pruebas: `pytest`
- Tablas: `alembic upgrade head` (hasta la migración `0005`: parcelas y ciclos de HU_14, consumos y pérdidas de HU_05).

Más detalles en [`backend/README.md`](backend/README.md).

## Backend desplegado (Render)

El backend de pruebas está publicado en **https://uniban-app-suministros.onrender.com/** (rama `main`, plan gratuito).
Los otros equipos y la app en el celular lo usan sin necesidad de correr nada en el PC.

- Salud: <https://uniban-app-suministros.onrender.com/health> · Documentación: <https://uniban-app-suministros.onrender.com/docs>
- En el plan gratuito el servicio se **duerme tras 15 minutos sin uso**; la primera petición tarda cerca de un minuto. Abrir `/health` antes de una prueba o demo.
- Usa la misma base de datos de Neon que el equipo. Las migraciones se aplican a mano (`alembic upgrade head`) desde un equipo con acceso a esa base.

Pasos y configuración completos en [`docs/despliegue-render.md`](docs/despliegue-render.md).

## Variables de entorno y configuración local

| Dónde | Archivo (no versionado) | Plantilla | Claves |
|-------|-------------------------|-----------|--------|
| Backend | `backend/.env` | `backend/.env.example` | `DATABASE_URL`, `FIREBASE_PROJECT_ID`, `CORS_ORIGINS`, `ENVIRONMENT`, `API_V1_PREFIX`, `APP_NAME` |
| Android | `android-app/local.properties` | `android-app/local.properties.example` | `sdk.dir`, `api.baseUrl` |
| Firebase | `android-app/app/google-services.json` | — (se descarga de la consola de Firebase) | — |

Ningún secreto (contraseñas, cadenas de conexión, claves) debe subirse al repositorio (RT_14).

## Documentación disponible

- **Anteproyecto:** [`AnteproyectoInicial-PI1-Equipo3-Uniban.pdf`](docs/avances/AnteproyectoInicial-PI1-Equipo3-Uniban.pdf) – propuesta inicial y planteamiento general.
- **Requisitos e historias de usuario:** [`HU_y_Requisitos_Tecnicos_Equipo3.pdf`](docs/requisitos/HU_y_Requisitos_Tecnicos_Equipo3.pdf) – 14 HU y 18 requisitos técnicos (incluye HU_13, registro, y HU_14, registro de parcelas).
- **Modelo de datos:** [`MERv2.drawio`](docs/modelo-datos/MERv2.drawio).
- **Mockups:** [`MockupsFigma.zip`](docs/mockups/MockupsFigma.zip).
- **Arquitectura:** [`docs/arquitectura/README.md`](docs/arquitectura/README.md).

## Estado actual

- [x] Documentación inicial (anteproyecto, HU y RT, MER, mockups).
- [x] HU_04 local: inventario demo, detalle, entrada y confirmación; cantidades `Float` con dos decimales.
- [x] Lectura local de inventario y movimientos como soporte de HU_04.
- [x] Esqueleto backend FastAPI con `/health`, `/api/v1` y configuración por entorno.
- [x] HU_01: inicio y cierre de sesión con Firebase Authentication (ver [`docs/hu01.md`](docs/hu01.md)).
- [x] Registro de usuarios: cuenta en Firebase y perfil de productor en PostgreSQL (`/api/v1/users/register`).
- [x] HU_03: consulta de inventario y movimientos recientes sin conexión, con saldo consistente con su historial (RT_10). Ver [`docs/hu03.md`](docs/hu03.md). Falta separar los datos locales por sesión y usar el catálogo real.
- [x] HU_05: consumos (con parcela y ciclo activo) y pérdidas (con motivo) que restan existencia sin dejarla negativa, sin conexión y sincronizados. Ver [`docs/hu05.md`](docs/hu05.md).
- [x] HU_06: sincronización de movimientos con PostgreSQL (Neon) vía FastAPI, WorkManager y `SYNCED/ERROR`. Ver [`docs/hu06.md`](docs/hu06.md).
- [x] HU_02: lista de parcelas y detalle con su ciclo activo (fechas y etapa) o aviso sin ciclo; funciona sin conexión. Ver [`docs/hu02.md`](docs/hu02.md).
- [x] HU_07: historial de movimientos por insumo (con resumen inicial + entradas − consumos − pérdidas = existencia) y general, con filtros por insumo, tipo, parcela y fechas; sin conexión. Ver [`docs/hu07.md`](docs/hu07.md).
- [x] HU_14: registro de parcelas con ciclo productivo opcional (`/api/v1/parcels`), copia local en Room (versión 2) y barra inferior Parcelas / Inventario. Ver [`docs/hu14.md`](docs/hu14.md).

La guía de implementación y demostración está en [`docs/hu04.md`](docs/hu04.md).

## Próximos pasos

- HU de ajustes de inventario (AJUSTE) y definición del tipo SALIDA (ver [`docs/hu05.md`](docs/hu05.md#pendiente)).
- Pendientes de HU_14 (ver [`docs/hu14.md`](docs/hu14.md#pendientes-del-equipo)): actualizar el MER y acordar `id_parcela` con el Equipo 1.
- Cada cambio del esquema local exige subir la versión de Room y versionar el nuevo `app/schemas/.../N.json` (HU_14 usa `AutoMigration` 1 → 2 y HU_05, 2 → 3).

## Equipo

**Equipo 3 – Proyecto Integrador 1, UdeA**
