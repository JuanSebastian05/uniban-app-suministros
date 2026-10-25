"""Sincronización de actividades productivas (HU_08).

Exige el token de Firebase (RT_13): la actividad solo puede registrarse sobre una parcela del productor del
usuario autenticado.
"""

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import User
from app.schemas.activities import ActivityIn, ActivitySyncOut
from app.services.activities import ActivityConflict, sync_activity

router = APIRouter()


@router.post(
    "",
    response_model=ActivitySyncOut,
    status_code=status.HTTP_201_CREATED,
    summary="Recibir una actividad productiva pendiente (idempotente por UUID)",
    responses={
        200: {"description": "La actividad ya estaba registrada con el mismo contenido."},
        401: {"description": "Falta el token de Firebase o no es válido."},
        403: {"description": "La cuenta no está registrada."},
        409: {"description": "Conflicto: mismo UUID con contenido distinto, parcela de otro usuario o ciclo que "
                             "no es de la parcela."},
        422: {"description": "Datos inválidos (tipo desconocido, observación de más de 500 caracteres, ciclo ausente)."},
    },
)
def receive_activity(
    payload: ActivityIn,
    response: Response,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ActivitySyncOut:
    try:
        result = sync_activity(db, payload, user.producer_id)
    except ActivityConflict as error:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(error)) from error
    if result == "DUPLICATE":
        response.status_code = status.HTTP_200_OK
    return ActivitySyncOut(id=payload.id, status=result)
