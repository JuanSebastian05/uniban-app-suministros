"""Endpoint de sincronización de movimientos (HU_06).

Sin verificación de identidad por ahora: HU_01 (Firebase) y RT_13 están pendientes.
"""

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.db.session import get_db
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
        409: {"description": "Conflicto: mismo UUID con contenido distinto, tipo no soportado o stock fuera de límite."},
    },
)
def receive_movement(payload: MovementIn, response: Response, db: Session = Depends(get_db)) -> MovementSyncOut:
    try:
        result = sync_movement(db, payload)
    except MovementConflict as error:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(error)) from error
    if result == "DUPLICATE":
        response.status_code = status.HTTP_200_OK
    return MovementSyncOut(id=payload.id, status=result)
