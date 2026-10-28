"""Registro y consulta de parcelas (HU_14).

Reglas:
- La parcela queda siempre a nombre del productor del usuario autenticado.
- El nombre es único por productor sin distinguir mayúsculas ni espacios sobrantes.
- Idempotente por UUID: reenviar la misma parcela no la duplica (``created`` = False);
  el mismo UUID con otro contenido, o de otro productor, es un conflicto.
- Si viene un ciclo, se crea en la misma transacción como ciclo activo de la parcela.
"""

import uuid
from datetime import date, datetime, timezone
from decimal import Decimal

from sqlalchemy import and_, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, aliased

from app.models import Aforamiento, Parcel, ProductionCycle, ProductionCycleStatusChange
from app.models.parcel import name_key
from app.schemas.aforamientos import AforamientoOut
from app.services.aforamientos import aforamiento_out
from app.schemas.parcels import CycleOut, ParcelIn, ParcelOut, ServiceParcelOut

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


def _to_out(
    parcel: Parcel, cycle: ProductionCycle | None, aforamiento: AforamientoOut | None = None,
    current_status: str | None = None, activity_cycle_id: uuid.UUID | None = None,
    last_cycle: ProductionCycle | None = None,
) -> ParcelOut:
    return ParcelOut(
        aforamiento=aforamiento,
        id=parcel.id,
        name=parcel.name,
        # Siempre con dos decimales: igual al crear que al leer de la base.
        area_ha=Decimal(parcel.area_ha).quantize(Decimal("0.01")),
        active_cycle=None if cycle is None else CycleOut(
            id=cycle.id, start_date=cycle.start_date, end_date=cycle.end_date, status=cycle.status,
        ),
        current_status=current_status if current_status is not None else (cycle.status if cycle else None),
        activity_cycle_id=activity_cycle_id if activity_cycle_id is not None else (cycle.id if cycle else None),
        last_cycle=None if last_cycle is None else CycleOut(
            id=last_cycle.id, start_date=last_cycle.start_date, end_date=last_cycle.end_date, status=last_cycle.status,
        ),
    )


def _current_status(db: Session, parcel_id: uuid.UUID, active_cycle: ProductionCycle | None) -> str | None:
    if active_cycle is not None:
        return active_cycle.status
    has_completed_cycle = db.scalar(
        select(ProductionCycle.id).where(
            ProductionCycle.parcel_id == parcel_id, ProductionCycle.end_date.is_not(None),
        ).limit(1)
    )
    return "DESCANSO" if has_completed_cycle is not None else None


def _last_completed_cycle_id(db: Session, parcel_id: uuid.UUID) -> uuid.UUID | None:
    cycle = _last_completed_cycle(db, parcel_id)
    return cycle.id if cycle else None


def _last_completed_cycle(db: Session, parcel_id: uuid.UUID) -> ProductionCycle | None:
    return db.scalar(
        select(ProductionCycle)
        .where(ProductionCycle.parcel_id == parcel_id, ProductionCycle.end_date.is_not(None))
        .order_by(ProductionCycle.end_date.desc(), ProductionCycle.start_date.desc(), ProductionCycle.id)
        .limit(1)
    )


def _same_content(db: Session, parcel: Parcel, data: ParcelIn) -> bool:
    if parcel.name != data.name or parcel.area_ha != data.area_ha:
        return False
    if data.cycle is None:
        # A parcel may have gained or completed cycles after its original idempotent registration.
        return True
    original_cycle = db.get(ProductionCycle, data.cycle.id)
    return original_cycle is not None and original_cycle.parcel_id == parcel.id and original_cycle.start_date == data.cycle.start_date


def _resolve_existing(db: Session, data: ParcelIn, producer_id: uuid.UUID) -> ParcelOut | None:
    """Si el UUID ya existe: devuelve la parcela si es un reenvío idéntico; si no, lanza conflicto."""
    existing = db.get(Parcel, data.id)
    if existing is None:
        return None
    cycle = _active_cycle(db, existing.id)
    if existing.producer_id != producer_id or not _same_content(db, existing, data):
        raise ParcelConflict(ID_CONFLICT, "Ya existe una parcela con este identificador y datos distintos.")
    return _to_out(
        existing, cycle, current_status=_current_status(db, existing.id, cycle),
        activity_cycle_id=cycle.id if cycle else _last_completed_cycle_id(db, existing.id),
        last_cycle=_last_completed_cycle(db, existing.id),
    )


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
        _record_cycle_change(db, cycle, None, cycle.status, cycle.start_date)
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
    return _to_out(
        parcel, cycle, current_status=_current_status(db, parcel.id, cycle),
        activity_cycle_id=cycle.id if cycle else _last_completed_cycle_id(db, parcel.id),
        last_cycle=_last_completed_cycle(db, parcel.id),
    ), True


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
    latest = _latest_by_parcel(db, [parcel.id for parcel, _ in rows])
    parcel_ids = [parcel.id for parcel, _ in rows]
    completed_cycles = db.scalars(
        select(ProductionCycle)
        .where(ProductionCycle.parcel_id.in_(parcel_ids), ProductionCycle.end_date.is_not(None))
        .order_by(
            ProductionCycle.parcel_id, ProductionCycle.end_date.desc(),
            ProductionCycle.start_date.desc(), ProductionCycle.id,
        )
    ).all() if parcel_ids else []
    last_completed: dict[uuid.UUID, ProductionCycle] = {}
    for completed_cycle in completed_cycles:
        last_completed.setdefault(completed_cycle.parcel_id, completed_cycle)
    return [
        _to_out(
            parcel, cycle, latest.get(parcel.id),
            current_status=cycle.status if cycle is not None else ("DESCANSO" if parcel.id in last_completed else None),
            activity_cycle_id=cycle.id if cycle is not None else (
                last_completed[parcel.id].id if parcel.id in last_completed else None
            ),
            last_cycle=last_completed.get(parcel.id),
        )
        for parcel, cycle in rows
    ]


def _owned_parcel(db: Session, parcel_id: uuid.UUID, producer_id: uuid.UUID) -> Parcel:
    parcel = db.scalar(select(Parcel).where(Parcel.id == parcel_id).with_for_update())
    if parcel is None or parcel.producer_id != producer_id:
        raise ParcelConflict("PARCEL_NOT_FOUND", "La parcela no existe o pertenece a otro usuario.")
    return parcel


def _record_cycle_change(
    db: Session, cycle: ProductionCycle, from_status: str | None, to_status: str, effective_date: date,
) -> None:
    db.add(ProductionCycleStatusChange(
        id=uuid.uuid4(), cycle_id=cycle.id, from_status=from_status, to_status=to_status,
        effective_date=effective_date, changed_at=datetime.now(timezone.utc),
    ))


def change_cycle_stage(
    db: Session, parcel_id: uuid.UUID, producer_id: uuid.UUID, status: str, effective_date: date,
) -> ParcelOut:
    """Cambia la etapa del ciclo activo y conserva el evento para trazabilidad."""
    parcel = _owned_parcel(db, parcel_id, producer_id)
    cycle = _active_cycle(db, parcel.id)
    if cycle is None:
        raise ParcelConflict("NO_ACTIVE_CYCLE", "La parcela no tiene un ciclo activo para cambiar de etapa.")
    if effective_date < cycle.start_date:
        raise ParcelConflict("INVALID_CYCLE_DATE", "La fecha del cambio no puede ser anterior al inicio del ciclo.")
    if cycle.status != status:
        _record_cycle_change(db, cycle, cycle.status, status, effective_date)
        cycle.status = status
        cycle.updated_at = datetime.now(timezone.utc)
        db.commit()
    return _to_out(parcel, cycle, current_status=cycle.status)


def finish_cycle(
    db: Session, parcel_id: uuid.UUID, producer_id: uuid.UUID, end_date: date,
) -> ParcelOut:
    """Cierra el ciclo activo. La parcela queda en descanso hasta iniciar el siguiente ciclo."""
    parcel = _owned_parcel(db, parcel_id, producer_id)
    cycle = _active_cycle(db, parcel.id)
    if cycle is None:
        current_status = _current_status(db, parcel.id, None)
        if current_status == "DESCANSO":
            return _to_out(
                parcel, None, current_status=current_status,
                activity_cycle_id=_last_completed_cycle_id(db, parcel.id),
                last_cycle=_last_completed_cycle(db, parcel.id),
            )
        raise ParcelConflict("NO_ACTIVE_CYCLE", "La parcela no tiene un ciclo activo para finalizar.")
    if end_date < cycle.start_date:
        raise ParcelConflict("INVALID_CYCLE_DATE", "La fecha de fin no puede ser anterior al inicio del ciclo.")
    _record_cycle_change(db, cycle, cycle.status, "DESCANSO", end_date)
    cycle.end_date = end_date
    cycle.updated_at = datetime.now(timezone.utc)
    db.commit()
    return _to_out(
        parcel, None, current_status="DESCANSO", activity_cycle_id=cycle.id, last_cycle=cycle,
    )


def start_cycle(
    db: Session, parcel_id: uuid.UUID, producer_id: uuid.UUID, cycle_id: uuid.UUID, start_date: date,
) -> tuple[ParcelOut, bool]:
    """Inicia un nuevo ciclo en una parcela sin ciclo activo; devuelve si se creó."""
    parcel = _owned_parcel(db, parcel_id, producer_id)
    existing = db.get(ProductionCycle, cycle_id)
    if existing is not None:
        if existing.parcel_id == parcel.id and existing.start_date == start_date:
            active = _active_cycle(db, parcel.id)
            return _to_out(
                parcel, active, current_status=_current_status(db, parcel.id, active),
                activity_cycle_id=active.id if active else _last_completed_cycle_id(db, parcel.id),
                last_cycle=_last_completed_cycle(db, parcel.id),
            ), False
        raise ParcelConflict(ID_CONFLICT, "Ya existe un ciclo con este identificador y datos distintos.")
    active = _active_cycle(db, parcel.id)
    if active is not None:
        raise ParcelConflict("CYCLE_ALREADY_ACTIVE", "La parcela ya tiene un ciclo activo.")
    last_end = db.scalar(
        select(func.max(ProductionCycle.end_date)).where(
            ProductionCycle.parcel_id == parcel.id, ProductionCycle.end_date.is_not(None),
        )
    )
    if last_end is not None and start_date < last_end:
        raise ParcelConflict("INVALID_CYCLE_DATE", "El nuevo ciclo no puede iniciar antes de finalizar el anterior.")
    cycle = ProductionCycle(
        id=cycle_id, parcel_id=parcel.id, start_date=start_date, status="EN_PREPARACION",
    )
    try:
        db.add(cycle)
        db.flush()
        _record_cycle_change(db, cycle, None, "EN_PREPARACION", start_date)
        db.commit()
    except IntegrityError:
        db.rollback()
        existing = db.get(ProductionCycle, cycle_id)
        if existing is not None and existing.parcel_id == parcel.id and existing.start_date == start_date:
            active = _active_cycle(db, parcel.id)
            return _to_out(
                parcel, active, current_status=_current_status(db, parcel.id, active),
                activity_cycle_id=active.id if active else _last_completed_cycle_id(db, parcel.id),
                last_cycle=_last_completed_cycle(db, parcel.id),
            ), False
        if _active_cycle(db, parcel.id) is not None:
            raise ParcelConflict("CYCLE_ALREADY_ACTIVE", "La parcela ya tiene un ciclo activo.")
        raise ParcelConflict(ID_CONFLICT, "No se pudo iniciar el ciclo por una restricción de integridad.")
    return _to_out(
        parcel, cycle, current_status=cycle.status, last_cycle=_last_completed_cycle(db, parcel.id),
    ), True


def _latest_by_parcel(db: Session, parcel_ids: list[uuid.UUID]) -> dict[uuid.UUID, AforamientoOut]:
    """El aforamiento más reciente de cada parcela en una sola consulta (mismo orden que ``latest_aforamiento``)."""
    if not parcel_ids:
        return {}
    ranked = (
        select(
            Aforamiento,
            func.row_number().over(
                partition_by=Aforamiento.parcel_id,
                order_by=(Aforamiento.date.desc(), Aforamiento.received_at.desc(), Aforamiento.id),
            ).label("rank"),
        )
        .where(Aforamiento.parcel_id.in_(parcel_ids))
        .subquery()
    )
    latest = aliased(Aforamiento, ranked)
    rows = db.scalars(select(latest).where(ranked.c.rank == 1)).all()
    return {row.parcel_id: aforamiento_out(row) for row in rows}


def list_all_parcels(db: Session) -> list[ServiceParcelOut]:
    """Todas las parcelas de todos los productores, solo con id, nombre y área (para el Equipo 1)."""
    rows = db.execute(select(Parcel).order_by(Parcel.name_key, Parcel.id)).scalars().all()
    return [
        ServiceParcelOut(id=p.id, name=p.name, area_ha=Decimal(p.area_ha).quantize(Decimal("0.01"))) for p in rows
    ]
