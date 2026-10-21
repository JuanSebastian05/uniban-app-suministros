"""Router raíz de la API versionada ``/api/v1``.

Cada HU agrega su router con ``api_router.include_router(...)``.
"""

from fastapi import APIRouter

from app.api.v1.endpoints import inventory, movements, parcels, users

api_router = APIRouter()
api_router.include_router(inventory.router, prefix="/inventory", tags=["inventario"])
api_router.include_router(movements.router, prefix="/movements", tags=["movimientos"])
api_router.include_router(parcels.router, prefix="/parcels", tags=["parcelas"])
api_router.include_router(users.router, prefix="/users", tags=["usuarios"])
