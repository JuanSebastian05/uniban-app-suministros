"""Recepción idempotente de aforamientos enviados por el Equipo 1 y consulta del más reciente.

Reglas:
- El UUID (lo genera el Equipo 1) identifica el aforamiento. Reenviar el mismo contenido no duplica
  (DUPLICATE); el mismo UUID con otro contenido es un conflicto.
- La parcela debe existir. No se contrasta el área del aforamiento con la de la parcela.
- Son de solo inserción, igual que movimientos y actividades.
"""

import logging
import uuid
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Aforamiento, Parcel
from app.schemas.aforamientos import AforamientoIn, AforamientoOut

logger = logging.getLogger(__name__)

_CONTENT_CONFLICT = "Ya existe un aforamiento con este id y contenido distinto."


class AforamientoConflict(Exception):
    """El aforamiento no puede aceptarse (HTTP 409)."""


class ParcelNotFound(Exception):
    """La parcela del aforamiento no existe (HTTP 404)."""


def _area(value: Decimal) -> Decimal:
    return Decimal(value).quantize(Decimal("0.01"))


def _fraction(value: Decimal | None) -> Decimal | None:
    return None if value is None else Decimal(value).quantize(Decimal("0.001"))


def _same_content(existing: Aforamiento, data: AforamientoIn) -> bool:
    return (
        existing.parcel_id == data.parcel_id
        and existing.date == data.date
        and _area(existing.area_ha) == data.area_ha
        and existing.estimated_plants == data.estimated_plants
        and _fraction(existing.confidence) == data.confidence
    )


def _existing_result(existing: Aforamiento, data: AforamientoIn) -> str:
    if not _same_content(existing, data):
        raise AforamientoConflict(_CONTENT_CONFLICT)
    return "DUPLICATE"


def receive_aforamiento(db: Session, data: AforamientoIn) -> str:
    """Guarda el aforamiento. Devuelve ``CREATED`` o ``DUPLICATE``; lanza AforamientoConflict o ParcelNotFound."""
    existing = db.get(Aforamiento, data.id)
    if existing is not None:
        return _existing_result(existing, data)
    if db.get(Parcel, data.parcel_id) is None:
        raise ParcelNotFound("La parcela indicada no existe.")
    try:
        db.add(Aforamiento(
            id=data.id, parcel_id=data.parcel_id, date=data.date, area_ha=data.area_ha,
            estimated_plants=data.estimated_plants, confidence=data.confidence,
        ))
        db.commit()
    except IntegrityError:
        # Carrera: otra petición insertó el mismo UUID entre la lectura y la escritura.
        logger.warning("IntegrityError al guardar el aforamiento %s", data.id, exc_info=True)
        db.rollback()
        existing = db.get(Aforamiento, data.id)
        if existing is None:
            raise AforamientoConflict("No se pudo guardar el aforamiento por una restricción de integridad.")
        return _existing_result(existing, data)
    return "CREATED"


def latest_aforamiento(db: Session, parcel_id: uuid.UUID) -> AforamientoOut | None:
    """El aforamiento más reciente de la parcela: mayor ``date``; empata por ``received_at`` y luego por id."""
    row = db.scalar(
        select(Aforamiento)
        .where(Aforamiento.parcel_id == parcel_id)
        .order_by(Aforamiento.date.desc(), Aforamiento.received_at.desc(), Aforamiento.id)
        .limit(1)
    )
    if row is None:
        return None
    return AforamientoOut(
        id=row.id, parcel_id=row.parcel_id, date=row.date, area_ha=_area(row.area_ha),
        estimated_plants=row.estimated_plants, confidence=_fraction(row.confidence),
    )
