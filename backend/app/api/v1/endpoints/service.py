"""Rutas para otros equipos (Equipo 1), autenticadas con la clave de servicio ``X-API-Key``.

No usan el token de Firebase ni ``get_current_user``: quien llama es un servicio, no un productor.
"""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import require_service_key
from app.db.session import get_db
from app.schemas.parcels import ServiceParcelOut
from app.services.parcels import list_all_parcels

router = APIRouter(dependencies=[Depends(require_service_key)])

_KEY_RESPONSES = {
    401: {"description": "Falta la cabecera `X-API-Key` o la clave no coincide."},
    503: {"description": "El servidor no tiene configurada la clave de servicio."},
}


@router.get(
    "/parcels",
    response_model=list[ServiceParcelOut],
    summary="Todas las parcelas (id, nombre y área) para el aforamiento del Equipo 1",
    responses=_KEY_RESPONSES,
)
def all_parcels(db: Session = Depends(get_db)) -> list[ServiceParcelOut]:
    return list_all_parcels(db)
