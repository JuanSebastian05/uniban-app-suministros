"""Parcelas del usuario autenticado (HU_14). Cada usuario ve y registra solo las de su productor."""

import uuid

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import User
from app.schemas.aforamientos import AforamientoOut
from app.schemas.parcels import ParcelIn, ParcelOut
from app.services.aforamientos import ParcelNotFound, latest_for_producer
from app.services.parcels import ParcelConflict, create_parcel, list_parcels

router = APIRouter()

_AUTH_RESPONSES = {
    401: {"description": "Falta el token de Firebase o no es válido."},
    403: {"description": "La cuenta no está registrada."},
}


@router.post(
    "",
    response_model=ParcelOut,
    status_code=status.HTTP_201_CREATED,
    summary="Registrar una parcela (y opcionalmente su ciclo inicial); idempotente por UUID",
    responses={
        200: {"description": "La parcela ya estaba registrada con el mismo contenido."},
        **_AUTH_RESPONSES,
        409: {"description": "`detail.code` = DUPLICATE_NAME (nombre repetido) o ID_CONFLICT (UUID en uso con otros datos)."},
        422: {"description": "Datos inválidos (nombre vacío, área no positiva o con más de dos decimales, estado desconocido)."},
    },
)
def register_parcel(
    payload: ParcelIn,
    response: Response,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ParcelOut:
    try:
        parcel, created = create_parcel(db, payload, user.producer_id)
    except ParcelConflict as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT, detail={"code": error.code, "message": error.message},
        ) from error
    if not created:
        response.status_code = status.HTTP_200_OK
    return parcel


@router.get(
    "",
    response_model=list[ParcelOut],
    summary="Parcelas del usuario autenticado con su ciclo activo",
    responses=_AUTH_RESPONSES,
)
def my_parcels(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> list[ParcelOut]:
    return list_parcels(db, user.producer_id)


@router.get(
    "/{parcel_id}/aforamiento",
    response_model=AforamientoOut,
    summary="Aforamiento más reciente de una parcela del usuario (HU_09)",
    responses={
        **_AUTH_RESPONSES,
        404: {"description": "La parcela no existe (o es de otro productor) o todavía no tiene aforamientos."},
    },
)
def latest_parcel_aforamiento(
    parcel_id: uuid.UUID, user: User = Depends(get_current_user), db: Session = Depends(get_db),
) -> AforamientoOut:
    try:
        aforamiento = latest_for_producer(db, parcel_id, user.producer_id)
    except ParcelNotFound as error:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(error)) from error
    if aforamiento is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="La parcela no tiene aforamientos.")
    return aforamiento
