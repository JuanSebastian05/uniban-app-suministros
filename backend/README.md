# Backend FastAPI

API central de la solución. Atiende a la app Android y sirve como puente con los Equipos 1 y 2. El backend está organizado en FastAPI, SQLAlchemy 2, Alembic y PostgreSQL.

> El código de esta copia incluye migraciones hasta 0011_recomendaciones. La revisión efectiva de Neon y la versión que Render está ejecutando deben verificarse por separado; consultar [estado del proyecto](../docs/estado-proyecto.md).

## Funciones disponibles

| Grupo | Rutas principales | Acceso |
| --- | --- | --- |
| Salud | GET /health | Público. |
| Usuarios | POST /api/v1/users/register, GET /api/v1/users/me | Token Firebase, con reglas de registro/perfil. |
| Inventario y movimientos | GET /api/v1/inventory, POST /api/v1/movements | Token Firebase; datos del productor autenticado. |
| Parcelas | POST/GET /api/v1/parcels | Token Firebase; solo parcelas del productor. |
| Actividades | POST /api/v1/activities | Token Firebase; envío idempotente. |
| Aforamiento | GET /api/v1/service/parcels, POST /api/v1/aforamientos | Clave de servicio X-API-Key, usada por Equipo 1. |
| Recomendaciones | POST/GET /api/v1/parcels/{id}/recommendation | Token Firebase; el backend consulta al Equipo 2. |
| Consulta de aforamiento | GET /api/v1/parcels/{id}/aforamiento y aforamiento incluido en la lista | Token Firebase; datos del productor. |

La clave del Equipo 1 se configura mediante SERVICE_API_KEY. El servicio del Equipo 2 se configura con EQUIPO2_BASE_URL y, si ese equipo la exige, EQUIPO2_API_KEY.

## Estructura

~~~text
backend/
├── app/
│   ├── api/                 # dependencias de autenticación y rutas
│   ├── core/                # configuración
│   ├── db/                  # base declarativa y sesiones
│   ├── models/              # productor, inventario, movimientos, parcelas, ciclos, actividad, aforo, recomendación
│   ├── schemas/             # validación y contratos HTTP
│   └── services/            # reglas de negocio y clientes externos
├── migrations/versions/     # Alembic: 0001–0005 y 0007–0011
├── tests/
├── requirements.txt
└── .env.example
~~~

## Requisitos y ejecución local

Requisitos: Python 3.11 o superior y PostgreSQL 15 o superior.

~~~powershell
cd backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
# Completar DATABASE_URL y FIREBASE_PROJECT_ID en .env
.venv\Scripts\python.exe -m alembic upgrade head
.venv\Scripts\python.exe -m uvicorn app.main:app --reload
~~~

- Salud: http://localhost:8000/health
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
- Emulador Android: configura api.baseUrl=http://10.0.2.2:8000/.

Las rutas autenticadas requieren un proyecto Firebase válido. Para probar rutas del Equipo 1 localmente, configura un valor ficticio y privado en SERVICE_API_KEY; no reutilices una clave de producción.

## Base de datos

Aplica migraciones con Alembic, no crees tablas manualmente:

~~~powershell
.venv\Scripts\python.exe -m alembic heads
.venv\Scripts\python.exe -m alembic upgrade head
~~~

La revisión de cabeza de este repositorio es 0011_recomendaciones. En Neon, revisa primero la revisión aplicada y sigue el procedimiento de [migraciones de despliegue](../docs/lista-verificacion-despliegue.md). Render no ejecuta migraciones por sí mismo.

## Configuración

Las variables están en .env.example. Las claves sensibles deben permanecer en un .env no versionado o en el panel de secretos de Render.

| Variable | Uso |
| --- | --- |
| DATABASE_URL | Conexión PostgreSQL con SQLAlchemy/psycopg. |
| FIREBASE_PROJECT_ID | Validación de tokens de Firebase. |
| SERVICE_API_KEY | Clave compartida para rutas del Equipo 1. |
| EQUIPO2_BASE_URL | URL del servicio de recomendaciones. |
| EQUIPO2_API_KEY | Opcional según autenticación del Equipo 2. |
| APP_NAME, ENVIRONMENT, API_V1_PREFIX | Configuración general de FastAPI. |
| CORS_ORIGINS | Solo necesario para clientes web que requieran CORS. |

## Despliegue

La URL configurada para el servicio es https://uniban-app-suministros.onrender.com/. Los pasos, variables y comprobaciones están en [despliegue Render](../docs/despliegue-render.md).

## Límite del contrato del Equipo 2

El backend envía solo cajas, carton y bolsas, incluso con saldo cero; los consumos y pérdidas se filtran a esos mismos productos. Usa `POST https://platano-logistica-api.onrender.com/api/v1/recomendar` con `Content-Type: application/json`, y valida/guarda la respuesta. Swagger no declara consumos ni pérdidas, así que su uso debe confirmarse con el Equipo 2. Ver [contrato del Equipo 2](../docs/contrato-equipo2.md).
