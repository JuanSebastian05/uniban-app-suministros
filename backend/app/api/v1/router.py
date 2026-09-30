"""Router raíz de la API versionada ``/api/v1``.

Vacío a propósito: no hay endpoints de dominio todavía.
Cada HU agregará su router, por ejemplo::

    from app.api.v1.endpoints import inventario
    api_router.include_router(inventario.router, prefix="/inventario", tags=["inventario"])
"""

from fastapi import APIRouter

api_router = APIRouter()
