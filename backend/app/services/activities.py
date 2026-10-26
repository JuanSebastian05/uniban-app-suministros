"""Recepción idempotente de actividades productivas enviadas por la app (HU_08).

Reglas:
- El UUID identifica la actividad. Reenviar el mismo contenido no duplica (DUPLICATE); el mismo UUID con otro
  contenido, o de otro productor, es un conflicto.
- La parcela debe ser del productor del usuario autenticado y el ciclo debe pertenecer a esa parcela.
  No se exige que el ciclo siga activo: la actividad pudo registrarse sin conexión antes de que se cerrara.
- Las actividades son solo de inserción, igual que los movimientos (RT_07).
"""

import logging
import uuid

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Activity, Parcel, ProductionCycle
from app.schemas.activities import ActivityIn

logger = logging.getLogger(__name__)

_CONTENT_CONFLICT = "Ya existe una actividad con este id y contenido distinto."


class ActivityConflict(Exception):
    """La actividad no puede aceptarse; el detalle se devuelve al cliente (HTTP 409)."""


def _same_content(existing: Activity, data: ActivityIn) -> bool:
    return (
        existing.parcel_id == data.parcel_id
        and existing.cycle_id == data.cycle_id
        and existing.type == data.type
        and existing.date == data.date
        and existing.observation == data.observation
    )


def _existing_result(db: Session, existing: Activity, data: ActivityIn, producer_id: uuid.UUID) -> str:
    parcel = db.get(Parcel, existing.parcel_id)
    # Mismo mensaje para «de otro usuario» y «contenido distinto»: no se revela el dueño.
    if parcel is None or parcel.producer_id != producer_id or not _same_content(existing, data):
        raise ActivityConflict(_CONTENT_CONFLICT)
    return "DUPLICATE"


def _check_context(db: Session, data: ActivityIn, producer_id: uuid.UUID) -> None:
    parcel = db.get(Parcel, data.parcel_id)
    if parcel is None or parcel.producer_id != producer_id:
        raise ActivityConflict("La parcela no existe o pertenece a otro usuario.")
    cycle = db.get(ProductionCycle, data.cycle_id)
    if cycle is None or cycle.parcel_id != parcel.id:
        raise ActivityConflict("El ciclo no pertenece a la parcela indicada.")


def sync_activity(db: Session, data: ActivityIn, producer_id: uuid.UUID) -> str:
    """Guarda la actividad. Devuelve ``CREATED`` o ``DUPLICATE``; lanza ActivityConflict."""
    existing = db.get(Activity, data.id)
    if existing is not None:
        return _existing_result(db, existing, data, producer_id)
    _check_context(db, data, producer_id)
    try:
        db.add(Activity(
            id=data.id, parcel_id=data.parcel_id, cycle_id=data.cycle_id, type=data.type, date=data.date,
            observation=data.observation, created_at=data.created_at, updated_at=data.updated_at,
        ))
        db.commit()
    except IntegrityError:
        # Carrera: otra petición insertó el mismo UUID entre la lectura y la escritura.
        logger.warning("IntegrityError al guardar la actividad %s", data.id, exc_info=True)
        db.rollback()
        existing = db.get(Activity, data.id)
        if existing is None:
            raise ActivityConflict("No se pudo guardar la actividad por una restricción de integridad.")
        return _existing_result(db, existing, data, producer_id)
    return "CREATED"
