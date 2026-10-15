"""Registro y consulta de parcelas (HU_14).

Reglas:
- La parcela queda siempre a nombre del productor del usuario autenticado.
- El nombre es único por productor sin distinguir mayúsculas ni espacios sobrantes.
- Idempotente por UUID: reenviar la misma parcela no la duplica (``created`` = False);
  el mismo UUID con otro contenido, o de otro productor, es un conflicto.
- Si viene un ciclo, se crea en la misma transacción como ciclo activo de la parcela.
"""

import uuid
from decimal import Decimal

from sqlalchemy import and_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Parcel, ProductionCycle
from app.models.parcel import name_key
from app.schemas.parcels import CycleOut, ParcelIn, ParcelOut

DUPLICATE_NAME = "DUPLICATE_NAME"
ID_CONFLICT = "ID_CONFLICT"


class ParcelConflict(Exception):
    """La parcela no puede aceptarse (HTTP 409). ``code`` le permite a la app mostrar el error en el campo correcto."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def _active_cycle(db: Session, parcel_id: uuid.UUID) -> ProductionCycle | None:
    return db.scalar(
        select(ProductionCycle).where(ProductionCycle.parcel_id == parcel_id, ProductionCycle.end_date.is_(None))
    )


def _to_out(parcel: Parcel, cycle: ProductionCycle | None) -> ParcelOut:
    return ParcelOut(
        id=parcel.id,
        name=parcel.name,
        # Siempre con dos decimales: igual al crear que al leer de la base.
        area_ha=Decimal(parcel.area_ha).quantize(Decimal("0.01")),
        active_cycle=None if cycle is None else CycleOut(
            id=cycle.id, start_date=cycle.start_date, end_date=cycle.end_date, status=cycle.status,
        ),
    )


def _same_content(parcel: Parcel, cycle: ProductionCycle | None, data: ParcelIn) -> bool:
    if parcel.name != data.name or parcel.area_ha != data.area_ha:
        return False
    if data.cycle is None:
        return cycle is None
    return (
        cycle is not None
        and cycle.id == data.cycle.id
        and cycle.start_date == data.cycle.start_date
        and cycle.status == data.cycle.status
    )


def _resolve_existing(db: Session, data: ParcelIn, producer_id: uuid.UUID) -> ParcelOut | None:
    """Si el UUID ya existe: devuelve la parcela si es un reenvío idéntico; si no, lanza conflicto."""
    existing = db.get(Parcel, data.id)
    if existing is None:
        return None
    cycle = _active_cycle(db, existing.id)
    if existing.producer_id != producer_id or not _same_content(existing, cycle, data):
        raise ParcelConflict(ID_CONFLICT, "Ya existe una parcela con este identificador y datos distintos.")
    return _to_out(existing, cycle)


def _name_taken(db: Session, producer_id: uuid.UUID, name: str) -> bool:
    return db.scalar(
        select(Parcel.id).where(Parcel.producer_id == producer_id, Parcel.name_key == name_key(name))
    ) is not None


def create_parcel(db: Session, data: ParcelIn, producer_id: uuid.UUID) -> tuple[ParcelOut, bool]:
    """Devuelve ``(parcela, creada)``; lanza :class:`ParcelConflict`."""
    resent = _resolve_existing(db, data, producer_id)
    if resent is not None:
        return resent, False
    if _name_taken(db, producer_id, data.name):
        raise ParcelConflict(DUPLICATE_NAME, "Ya tienes una parcela con ese nombre.")
    if data.cycle is not None and db.get(ProductionCycle, data.cycle.id) is not None:
        raise ParcelConflict(ID_CONFLICT, "Ya existe un ciclo con este identificador.")

    parcel = Parcel(
        id=data.id, producer_id=producer_id, name=data.name, name_key=name_key(data.name), area_ha=data.area_ha,
    )
    cycle = None
    if data.cycle is not None:
        cycle = ProductionCycle(
            id=data.cycle.id, parcel_id=parcel.id, start_date=data.cycle.start_date, status=data.cycle.status,
        )
    db.add(parcel)
    db.flush()  # la parcela debe existir antes que la llave foránea del ciclo
    if cycle is not None:
        db.add(cycle)
    try:
        db.commit()
    except IntegrityError:
        # Carrera: otra petición guardó el mismo UUID o el mismo nombre entre la lectura y la escritura.
        db.rollback()
        resent = _resolve_existing(db, data, producer_id)
        if resent is not None:
            return resent, False
        if _name_taken(db, producer_id, data.name):
            raise ParcelConflict(DUPLICATE_NAME, "Ya tienes una parcela con ese nombre.")
        raise ParcelConflict(ID_CONFLICT, "No se pudo guardar la parcela por una restricción de integridad.")
    return _to_out(parcel, cycle), True


def list_parcels(db: Session, producer_id: uuid.UUID) -> list[ParcelOut]:
    """Parcelas de ``producer_id`` con su ciclo activo (si tienen), ordenadas por nombre."""
    rows = db.execute(
        select(Parcel, ProductionCycle)
        .outerjoin(
            ProductionCycle,
            and_(ProductionCycle.parcel_id == Parcel.id, ProductionCycle.end_date.is_(None)),
        )
        .where(Parcel.producer_id == producer_id)
        .order_by(Parcel.name_key)
    ).all()
    return [_to_out(parcel, cycle) for parcel, cycle in rows]
