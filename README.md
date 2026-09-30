# Uniban App Suministros

Repositorio para la realización del proyecto **Uniban – Proyecto Integrador 1 de la Universidad de Antioquia (UdeA)**.

> **Estado actual: HU_04 implementada localmente con datos demo en Room.**
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
│   │   ├── data/                # InventoryRepository, Quantity y local (Room)
│   │   ├── ui/                  # navigation | theme | inventory
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
3. Para desarrollar HU_01, colocar `google-services.json` en `android-app/app/`. El esqueleto
   compila sin él, pero el inicio de sesión requiere la configuración de Firebase.
   Este archivo **no se sube** al repositorio.
4. Sincronizar Gradle y ejecutar la configuración `app`. Se abre **Inventario** con seis insumos demo.
   HU_04 funciona sin Firebase, backend ni conexión a internet.

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

Más detalles en [`backend/README.md`](backend/README.md).

## Variables de entorno y configuración local

| Dónde | Archivo (no versionado) | Plantilla | Claves |
|-------|-------------------------|-----------|--------|
| Backend | `backend/.env` | `backend/.env.example` | `DATABASE_URL`, `CORS_ORIGINS`, `ENVIRONMENT`, `API_V1_PREFIX`, `APP_NAME` |
| Android | `android-app/local.properties` | `android-app/local.properties.example` | `sdk.dir`, `api.baseUrl` |
| Firebase | `android-app/app/google-services.json` | — (se descarga de la consola de Firebase) | — |

Ningún secreto (contraseñas, cadenas de conexión, claves) debe subirse al repositorio (RT_14).

## Documentación disponible

- **Anteproyecto:** [`AnteproyectoInicial-PI1-Equipo3-Uniban.pdf`](docs/avances/AnteproyectoInicial-PI1-Equipo3-Uniban.pdf) – propuesta inicial y planteamiento general.
- **Requisitos e historias de usuario:** [`HU_y_Requisitos_Tecnicos_Equipo3.pdf`](docs/requisitos/HU_y_Requisitos_Tecnicos_Equipo3.pdf) – 12 HU y 18 requisitos técnicos.
- **Modelo de datos:** [`MERv2.drawio`](docs/modelo-datos/MERv2.drawio).
- **Mockups:** [`MockupsFigma.zip`](docs/mockups/MockupsFigma.zip).
- **Arquitectura:** [`docs/arquitectura/README.md`](docs/arquitectura/README.md).

## Estado actual

- [x] Documentación inicial (anteproyecto, HU y RT, MER, mockups).
- [x] HU_04 local: inventario demo, detalle, entrada y confirmación; cantidades `Float` con dos decimales.
- [x] Lectura local de inventario y movimientos como soporte de HU_04.
- [x] Esqueleto backend FastAPI con `/health`, `/api/v1` y configuración por entorno.
- [ ] HU_01: autenticación y separación de datos por sesión.
- [ ] HU_03: integración del inventario con la sesión y el catálogo real.
- [ ] HU_06: envío de movimientos al servidor. Por ahora permanecen `PENDING`.

La guía de implementación y demostración está en [`docs/hu04.md`](docs/hu04.md).

## Próximos pasos

- Conectar HU_01 y reemplazar el productor demo por la sesión autorizada.
- Implementar HU_06 con envío idempotente y reintentos de los movimientos locales.
- Incorporar migraciones al cambiar el esquema Room y crear las primeras tablas PostgreSQL.

## Equipo

**Equipo 3 – Proyecto Integrador 1, UdeA**
