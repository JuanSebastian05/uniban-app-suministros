"""Punto de entrada del backend FastAPI.

Ejecutar (desde la carpeta backend/):
    uvicorn app.main:app --reload

Documentación OpenAPI automática: /docs (Swagger UI) y /redoc.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.router import api_router
from app.core.config import get_settings

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Backend del Equipo 3 – gestión y trazabilidad de suministros (Plátano / Unibán).",
)

if settings.cors_origins_list:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )


@app.get("/health", tags=["técnico"])
def health() -> dict[str, str]:
    """Endpoint técnico para comprobar que el backend está levantado (no es una HU)."""
    return {"status": "ok"}


app.include_router(api_router, prefix=settings.api_v1_prefix)
