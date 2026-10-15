"""Endpoint de sincronización de movimientos (HU_06).

Exige el token de Firebase (RT_13): el movimiento se guarda en el inventario del productor
del usuario autenticado y nunca toca datos de otro usuario.
"""

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import User
from app.schemas.movements import MovementIn, MovementSyncOut
from app.services.movement_sync import MovementConflict, sync_movement

router = APIRouter()


@router.post(
    "",
    response_model=MovementSyncOut,
    status_code=status.HTTP_201_CREATED,
    summary="Recibir un movimiento pendiente (idempotente por UUID)",
    responses={
        200: {"description": "El movimiento ya estaba registrado con el mismo contenido."},
        401: {"description": "Falta el token de Firebase o no es válido."},
        403: {"description": "La cuenta no está registrada."},
        409: {"description": "Conflicto: mismo UUID con contenido distinto, datos de otro usuario, "
                             "tipo no soportado o stock fuera de límite."},
    },
)
def receive_movement(
    payload: MovementIn,
    response: Response,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MovementSyncOut:
    try:
        result = sync_movement(db, payload, user.producer_id)
    except MovementConflict as error:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(error)) from error
    if result == "DUPLICATE":
        response.status_code = status.HTTP_200_OK
    return MovementSyncOut(id=payload.id, status=result)
