"""Router raíz de la API versionada ``/api/v1``.

Cada HU agrega su router con ``api_router.include_router(...)``.
"""

from fastapi import APIRouter

from app.api.v1.endpoints import activities, aforamientos, inventory, movements, parcels, service, users

api_router = APIRouter()
api_router.include_router(activities.router, prefix="/activities", tags=["actividades"])
api_router.include_router(aforamientos.router, prefix="/aforamientos", tags=["servicio (otros equipos)"])
api_router.include_router(inventory.router, prefix="/inventory", tags=["inventario"])
api_router.include_router(movements.router, prefix="/movements", tags=["movimientos"])
api_router.include_router(parcels.router, prefix="/parcels", tags=["parcelas"])
api_router.include_router(service.router, prefix="/service", tags=["servicio (otros equipos)"])
api_router.include_router(users.router, prefix="/users", tags=["usuarios"])
