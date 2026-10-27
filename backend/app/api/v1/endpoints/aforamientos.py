"""Recepción de aforamientos del Equipo 1 (autenticada con la clave de servicio ``X-API-Key``)."""

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.api.deps import require_service_key
from app.db.session import get_db
from app.schemas.aforamientos import AforamientoIn, AforamientoSyncOut
from app.services.aforamientos import AforamientoConflict, ParcelNotFound, receive_aforamiento

router = APIRouter(dependencies=[Depends(require_service_key)])


@router.post(
    "",
    response_model=AforamientoSyncOut,
    status_code=status.HTTP_201_CREATED,
    summary="Recibir un aforamiento del Equipo 1 (idempotente por id)",
    responses={
        200: {"description": "El aforamiento ya estaba registrado con el mismo contenido."},
        401: {"description": "Falta la cabecera `X-API-Key` o la clave no coincide."},
        404: {"description": "La parcela indicada no existe."},
        409: {"description": "Mismo `id` con contenido distinto."},
        422: {"description": "Datos inválidos (fecha, área <= 0, plantas negativas o confianza fuera de 0 a 1)."},
        503: {"description": "El servidor no tiene configurada la clave de servicio."},
    },
)
def receive(payload: AforamientoIn, response: Response, db: Session = Depends(get_db)) -> AforamientoSyncOut:
    try:
        result = receive_aforamiento(db, payload)
    except ParcelNotFound as error:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(error)) from error
    except AforamientoConflict as error:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(error)) from error
    if result == "DUPLICATE":
        response.status_code = status.HTTP_200_OK
    return AforamientoSyncOut(id=payload.id, status=result)
