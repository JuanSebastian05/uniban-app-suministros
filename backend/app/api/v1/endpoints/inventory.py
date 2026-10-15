"""Inventario del usuario autenticado. Cada usuario ve solo el de su productor."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import User
from app.schemas.movements import InventoryItemOut
from app.services.inventory_query import list_inventory

router = APIRouter()


@router.get(
    "",
    response_model=list[InventoryItemOut],
    summary="Existencias del inventario del usuario autenticado",
    responses={
        401: {"description": "Falta el token de Firebase o no es válido."},
        403: {"description": "La cuenta no está registrada."},
    },
)
def my_inventory(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> list[InventoryItemOut]:
    return list_inventory(db, user.producer_id)
