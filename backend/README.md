# Backend – FastAPI (Equipo 3)

API central del sistema. La app Android solo se comunica con este backend (RT_11);
cualquier integración con los Equipos 1 y 2 pasará por aquí en el futuro.

**Estado actual:** arquitectura base/configuración inicial. Solo existe el endpoint técnico
`GET /health`. No hay endpoints de dominio ni tablas creadas.

## Estructura

```text
backend/
├── app/
│   ├── main.py            # crea la app FastAPI, CORS, /health e incluye /api/v1
│   ├── api/v1/router.py   # router raíz de /api/v1 (vacío)
│   ├── core/config.py     # configuración por variables de entorno (pydantic-settings)
│   ├── db/
│   │   ├── base.py        # Base declarativa de SQLAlchemy
│   │   └── session.py     # engine perezoso + dependencia get_db()
│   ├── models/            # modelos ORM (vacío)
│   ├── schemas/           # esquemas Pydantic / contratos OpenAPI (vacío)
│   └── (HU_03/04/06: endpoints y lógica mínima de inventario)
├── tests/test_health.py   # PyTest: /health y /openapi.json
├── requirements.txt
├── pytest.ini
└── .env.example
```

Para esta entrega, el flujo previsto es `api (router) → lógica de inventario → db`.
`schemas` define los contratos HTTP y `models` las tablas. Se extraerán capas
adicionales solo si la lógica real las justifica.

## Requisitos

- Python 3.11 o superior
- PostgreSQL 15+ (solo necesario cuando existan endpoints que usen la base de datos)

## Cómo ejecutar

```bash
cd backend
python -m venv .venv
# Windows:  .venv\Scripts\activate
# Linux/Mac: source .venv/bin/activate
pip install -r requirements.txt

cp .env.example .env        # Windows: copy .env.example .env  → luego editar valores
uvicorn app.main:app --reload
```

- Salud: <http://localhost:8000/health> → `{"status": "ok"}`
- Swagger UI: <http://localhost:8000/docs>
- ReDoc: <http://localhost:8000/redoc>

Para que el emulador Android lo alcance se usa `http://10.0.2.2:8000/`. Desde un dispositivo
físico, ejecutar `uvicorn app.main:app --reload --host 0.0.0.0` y usar la IP local del PC.

## Pruebas

```bash
pytest
```

## Variables de entorno

| Variable        | Descripción                                               | Ejemplo |
|-----------------|-----------------------------------------------------------|---------|
| `APP_NAME`      | Nombre mostrado en OpenAPI                                | `Uniban Suministros API` |
| `ENVIRONMENT`   | `development` / `production`                              | `development` |
| `API_V1_PREFIX` | Prefijo de la API versionada                              | `/api/v1` |
| `DATABASE_URL`  | Conexión SQLAlchemy a PostgreSQL (**secreto**, solo en `.env`) | `postgresql+psycopg://USUARIO:CLAVE@localhost:5432/uniban_suministros` |
| `CORS_ORIGINS`  | Orígenes permitidos separados por coma (vacío = sin CORS) | `http://localhost:3000` |

## Decisiones pendientes

- Migraciones (Alembic) al crear la primera tabla de inventario.
- Verificación de tokens de Firebase en el backend (HU_01 / RT_13).
