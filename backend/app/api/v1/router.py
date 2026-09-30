"""Router raíz de la API versionada ``/api/v1``.

Cada HU agrega su router con ``api_router.include_router(...)``.
"""

from fastapi import APIRouter

from app.api.v1.endpoints import movements

api_router = APIRouter()
api_router.include_router(movements.router, prefix="/movements", tags=["movimientos"])
