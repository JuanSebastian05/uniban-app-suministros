"""Cupo semanal y saldo real: validación y escritura atómicas bajo bloqueo de parcela."""
import uuid
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
from sqlalchemy import func, select, or_
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from app.models import Activity, Aforamiento, Dispatch, Harvest, Inventory, Movement, Parcel, ProductionCycle, ProductionCycleStatusChange, Recommendation, Supply
from app.schemas.production import DispatchIn, HarvestIn
from app.services.movement_sync import _ensure_inventory, MovementConflict
from app.services.recommendations import _packaging_name

BOGOTA = timezone(timedelta(hours=-5))


class ProductionConflict(Exception):
    pass


def stage_at(db: Session, parcel_id: uuid.UUID, day: date, recorded_at: datetime | None = None) -> tuple[ProductionCycle | None, str | None]:
    cutoff = None
    if recorded_at is not None:
        recorded_at = recorded_at.replace(tzinfo=timezone.utc) if recorded_at.tzinfo is None else recorded_at
        if recorded_at.astimezone(BOGOTA).date() == day:
            cutoff = recorded_at.astimezone(timezone.utc)
    query = select(ProductionCycle).where(
        ProductionCycle.parcel_id == parcel_id, ProductionCycle.start_date <= day,
    )
    if cutoff is not None:
        query = query.where(or_(ProductionCycle.start_date < day, ProductionCycle.created_at <= cutoff))
    cycle = db.scalar(query.order_by(ProductionCycle.start_date.desc(), ProductionCycle.created_at.desc()).limit(1))
    if cycle is None:
        return None, None
    closed_later_that_day = cutoff is not None and cycle.end_date == day and db.scalar(select(ProductionCycleStatusChange.id).where(
        ProductionCycleStatusChange.cycle_id == cycle.id, ProductionCycleStatusChange.to_status == "DESCANSO",
        ProductionCycleStatusChange.effective_date == day, ProductionCycleStatusChange.changed_at > cutoff,
    ).limit(1)) is not None
    if cycle.end_date is not None and cycle.end_date <= day and not closed_later_that_day:
        return cycle, "DESCANSO"
    changes = select(ProductionCycleStatusChange).where(
        ProductionCycleStatusChange.cycle_id == cycle.id, ProductionCycleStatusChange.effective_date <= day,
    )
    if cutoff is not None:
        changes = changes.where(or_(ProductionCycleStatusChange.effective_date < day, ProductionCycleStatusChange.changed_at <= cutoff))
    change = db.scalar(changes.order_by(ProductionCycleStatusChange.effective_date.desc(), ProductionCycleStatusChange.changed_at.desc()).limit(1))
    return cycle, change.to_status if change else cycle.status


def _payload(data):
    payload = data.model_dump(mode="json", by_alias=True)
    if "consumptions" in payload:
        payload["consumptions"].sort(key=lambda item: item["id"])
    return payload


def _lock(db, parcel_id, producer_id):
    parcel = db.scalar(select(Parcel).where(Parcel.id == parcel_id).with_for_update())
    if parcel is None or parcel.producer_id != producer_id:
        raise ProductionConflict("La parcela no existe o pertenece a otra cuenta.")
    return parcel


def _context(db, data, producer_id):
    _lock(db, data.parcel_id, producer_id)
    rec = db.get(Recommendation, data.recommendation_id)
    cycle = db.get(ProductionCycle, data.cycle_id)
    if rec is None or rec.parcel_id != data.parcel_id or cycle is None or cycle.parcel_id != data.parcel_id:
        raise ProductionConflict("La recomendación o el ciclo no pertenecen a esta parcela.")
    if rec.cycle_id != cycle.id or rec.aforamiento_id is None:
        raise ProductionConflict("Solicita una recomendación con trazabilidad de ciclo y aforamiento.")
    first = db.scalar(select(Harvest).where(Harvest.parcel_id == data.parcel_id, Harvest.period == rec.period)
                      .order_by(Harvest.received_at, Harvest.id).limit(1))
    if first and first.recommendation_id != rec.id:
        raise ProductionConflict("Este periodo ya tiene una recomendación de origen. Actualiza y usa la referencia original.")
    return rec, cycle


def _total(db, model, parcel_id, period):
    return db.scalar(select(func.coalesce(func.sum(model.quantity), 0)).where(
        model.parcel_id == parcel_id, model.period == period,
    ))


def _duplicate(db, model, data, producer_id):
    existing = db.get(model, data.id)
    if existing is None:
        return False
    parcel = db.get(Parcel, existing.parcel_id)
    if parcel is None or parcel.producer_id != producer_id or existing.request_payload != _payload(data):
        raise ProductionConflict("El UUID ya identifica una operación con otro contenido o propietario.")
    return True


def sync_harvest(db: Session, data: HarvestIn, producer_id: uuid.UUID):
    try:
        _lock(db, data.parcel_id, producer_id)
        if _duplicate(db, Harvest, data, producer_id):
            return "DUPLICATE"
        rec, cycle = _context(db, data, producer_id)
        active, stage = stage_at(db, data.parcel_id, data.date, data.created_at)
        if active is None or active.id != cycle.id or stage != "PRODUCCION":
            raise ProductionConflict("Solo se puede cosechar durante Producción y en el ciclo de origen.")
        aforamiento = db.get(Aforamiento, data.aforamiento_id)
        if rec.aforamiento_id != data.aforamiento_id or aforamiento is None or aforamiento.parcel_id != data.parcel_id:
            raise ProductionConflict("El aforamiento no corresponde a la recomendación.")
        if data.date < aforamiento.date or data.date > datetime.now(BOGOTA).date():
            raise ProductionConflict("La fecha real debe ser posterior al aforamiento y no puede ser futura.")
        total = _total(db, Harvest, data.parcel_id, rec.period) + data.quantity
        if total > 1_000_000_000:
            raise ProductionConflict("La cantidad acumulada supera el límite permitido.")
        if Decimal(total) > Decimal(str(rec.payload["volumeBunches"])) and not data.exception_confirmed:
            raise ProductionConflict("La cosecha acumulada supera la estimación semanal. Confirma explícitamente la excepción.")
        if db.get(Activity, data.id) is not None:
            raise ProductionConflict("Este UUID ya identifica una actividad sin cosecha estructurada.")
        db.add(Activity(id=data.id, parcel_id=data.parcel_id, cycle_id=data.cycle_id, type="COSECHA", date=data.date,
                        observation=data.observation, created_at=data.created_at, updated_at=data.created_at))
        db.flush()
        db.add(Harvest(id=data.id, parcel_id=data.parcel_id, cycle_id=data.cycle_id, recommendation_id=rec.id,
                       aforamiento_id=data.aforamiento_id, period=rec.period, quantity=data.quantity, date=data.date,
                       exception_confirmed=data.exception_confirmed, request_payload=_payload(data)))
        db.commit()
        return "CREATED"
    except (ProductionConflict, IntegrityError):
        db.rollback()
        raise


def packaging_targets(payload, shipped):
    load = payload.get("plan", {}).get("estimatedLoadBunches", 0)
    if load <= 0:
        raise ProductionConflict("La carga del plan debe ser positiva para distribuir empaques.")
    fraction = min(Decimal(shipped) / Decimal(load), Decimal(1))
    targets = {}
    for supply in payload["supplies"]:
        name = _packaging_name(supply["name"])
        if name is None or name in targets:
            raise ProductionConflict("El plan contiene empaques desconocidos o repetidos.")
        targets[name] = (Decimal(str(supply["requiredQuantity"])) * fraction).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return targets


def sync_dispatch(db: Session, data: DispatchIn, producer_id: uuid.UUID):
    try:
        _lock(db, data.parcel_id, producer_id)
        if _duplicate(db, Dispatch, data, producer_id):
            return "DUPLICATE"
        rec, cycle = _context(db, data, producer_id)
        day = data.dispatched_at.astimezone(BOGOTA).date()
        if data.dispatched_at > datetime.now(timezone.utc):
            raise ProductionConflict("La fecha y hora reales del despacho no pueden ser futuras.")
        _, stage = stage_at(db, data.parcel_id, day, data.dispatched_at)
        if stage not in ("PRODUCCION", "DESCANSO") or day < cycle.start_date:
            raise ProductionConflict("Solo se puede despachar durante Producción o Descanso.")
        harvested = _total(db, Harvest, data.parcel_id, rec.period)
        # Un envío no puede anteceder a las cosechas que lo respaldan.
        harvested_by_date = db.scalar(select(func.coalesce(func.sum(Harvest.quantity), 0)).where(
            Harvest.parcel_id == data.parcel_id, Harvest.period == rec.period, Harvest.date <= day))
        shipped = _total(db, Dispatch, data.parcel_id, rec.period)
        if shipped + data.quantity > min(harvested, harvested_by_date):
            raise ProductionConflict(f"Solo hay {max(0, min(harvested, harvested_by_date) - shipped)} racimos disponibles para esa fecha.")
        before, after = packaging_targets(rec.payload, shipped), packaging_targets(rec.payload, shipped + data.quantity)
        expected = {name: after[name] - before[name] for name in after if after[name] > before[name]}
        provided = {}
        inventory_ids = set()
        # Bloqueos siempre en orden de UUID para evitar interbloqueos entre parcelas con empaques compartidos.
        for movement in sorted(data.consumptions, key=lambda item: str(item.inventory.id)):
            name = _packaging_name(movement.inventory.supply.name)
            if name is None or movement.inventory.id in inventory_ids or movement.type != "CONSUMO" or movement.activity_id is not None or \
                    movement.parcel_id != data.parcel_id or movement.cycle_id != cycle.id or movement.date != day:
                raise ProductionConflict("El consumo de empaque no corresponde al despacho.")
            if db.get(Movement, movement.id) is not None:
                raise ProductionConflict("El UUID de un consumo ya fue utilizado.")
            inventory = _ensure_inventory(db, movement.inventory, producer_id)
            actual_supply = db.get(Supply, inventory.supply_id)
            if _packaging_name(actual_supply.name) != name or actual_supply.unit != "unidad" or actual_supply.category.casefold() != "empaque":
                raise ProductionConflict("El inventario indicado no corresponde al empaque del plan.")
            # Refrescar tras obtener el bloqueo: el inventario puede ser compartido por otras parcelas.
            inventory = db.scalar(select(Inventory).where(Inventory.id == inventory.id).with_for_update().execution_options(populate_existing=True))
            if inventory.quantity < movement.quantity:
                raise ProductionConflict(f"Faltan {movement.quantity - inventory.quantity} unidades de {name}. Registra una entrada de inventario.")
            provided[name] = provided.get(name, Decimal(0)) + movement.quantity
            inventory_ids.add(movement.inventory.id)
        if provided != expected:
            raise ProductionConflict("Los empaques no coinciden con la distribución acumulada del plan. Actualiza el avance.")
        db.add(Dispatch(id=data.id, parcel_id=data.parcel_id, cycle_id=cycle.id, recommendation_id=rec.id,
                        period=rec.period, quantity=data.quantity, dispatched_at=data.dispatched_at, request_payload=_payload(data)))
        db.flush()
        for movement in data.consumptions:
            inventory = db.get(Inventory, movement.inventory.id)
            inventory.quantity -= movement.quantity
            db.add(Movement(id=movement.id, inventory_id=inventory.id, type="CONSUMO", quantity=movement.quantity,
                            date=day, observation=movement.observation, created_at=movement.created_at, updated_at=movement.updated_at,
                            parcel_id=data.parcel_id, cycle_id=cycle.id, dispatch_id=data.id))
        db.commit()
        return "CREATED"
    except (ProductionConflict, MovementConflict, IntegrityError):
        db.rollback()
        raise


def production_history(db: Session, parcel_id, producer_id):
    # El historial debe representar un único corte coherente de cosechas y despachos concurrentes.
    _lock(db, parcel_id, producer_id)
    harvests = db.scalars(select(Harvest).where(Harvest.parcel_id == parcel_id).order_by(Harvest.received_at, Harvest.id)).all()
    dispatches = db.scalars(select(Dispatch).where(Dispatch.parcel_id == parcel_id).order_by(Dispatch.received_at, Dispatch.id)).all()
    ids = {item.recommendation_id for item in harvests}
    latest = db.scalar(select(Recommendation).where(Recommendation.parcel_id == parcel_id).order_by(Recommendation.generated_at.desc(), Recommendation.id).limit(1))
    if latest and latest.aforamiento_id and latest.cycle_id:
        ids.add(latest.id)
    recommendations = db.scalars(select(Recommendation).where(Recommendation.id.in_(ids))).all() if ids else []
    return {"harvests": [item.request_payload for item in harvests], "dispatches": [item.request_payload for item in dispatches],
            "recommendations": [dict(item.payload, aforamientoId=str(item.aforamiento_id), cycleId=str(item.cycle_id)) for item in recommendations]}
