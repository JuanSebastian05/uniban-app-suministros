"""Recepción idempotente de movimientos enviados por la app (HU_06, RT_07).

Reglas:
- El UUID del movimiento identifica el registro. Reenviar el mismo contenido no duplica
  ni vuelve a sumar (DUPLICATE); reenviar el mismo UUID con otro contenido es un conflicto.
- Los movimientos son solo de inserción. Se aceptan ENTRADA (suma) y, desde HU_05, CONSUMO y
  PERDIDA (restan). La existencia nunca queda negativa: si no alcanza, es un conflicto.
- Un consumo se asocia a una parcela del mismo productor y, si viene, a un ciclo de esa parcela.
- Ubicación, insumo e inventario se insertan solo si no existen; nunca se sobrescriben.
  La existencia = inicial + movimientos recibidos, en la misma transacción.
- Todo pertenece al productor del usuario autenticado: lo que es de otro productor se rechaza
  como conflicto, sin revelar su contenido.
"""

import uuid

import logging
from decimal import Decimal

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Inventory, Location, Movement, Parcel, ProductionCycle, Supply
from app.schemas.movements import InventoryIn, MovementIn

logger = logging.getLogger(__name__)

MAX_QUANTITY = Decimal("99999.99")
SUPPORTED_TYPES = {"ENTRADA", "CONSUMO", "PERDIDA"}
SUBTRACTING_TYPES = {"CONSUMO", "PERDIDA"}


class MovementConflict(Exception):
    """El movimiento no puede aceptarse; el detalle se devuelve al cliente (HTTP 409)."""


def _same_content(existing: Movement, data: MovementIn) -> bool:
    return (
        existing.inventory_id == data.inventory.id
        and existing.type == data.type
        and existing.quantity == data.quantity
        and existing.date == data.date
        and existing.observation == data.observation
        and existing.parcel_id == data.parcel_id
        and existing.cycle_id == data.cycle_id
        and existing.loss_reason == data.loss_reason
    )


def _check_parcel(db: Session, data: MovementIn, producer_id: uuid.UUID) -> None:
    """La parcela (y el ciclo, si viene) del consumo deben ser del productor."""
    if data.parcel_id is None:
        return
    parcel = db.get(Parcel, data.parcel_id)
    if parcel is None or parcel.producer_id != producer_id:
        raise MovementConflict("La parcela no existe o pertenece a otro usuario.")
    if data.cycle_id is not None:
        cycle = db.get(ProductionCycle, data.cycle_id)
        if cycle is None or cycle.parcel_id != parcel.id:
            raise MovementConflict("El ciclo no pertenece a la parcela indicada.")


def _ensure_inventory(db: Session, data: InventoryIn, producer_id: uuid.UUID) -> Inventory:
    # Sin relationship() SQLAlchemy no conoce el orden entre tablas: se hace flush tras cada
    # padre para que PostgreSQL vea ubicación → insumo → inventario en ese orden.
    location = db.get(Location, data.location.id)
    if location is None:
        db.add(Location(id=data.location.id, producer_id=producer_id, name=data.location.name))
        db.flush()
    elif location.producer_id != producer_id:
        raise MovementConflict("La ubicación pertenece a otro usuario.")
    supply = db.get(Supply, data.supply.id)
    if supply is None:
        db.add(Supply(
            id=data.supply.id, producer_id=producer_id,
            name=data.supply.name, category=data.supply.category, unit=data.supply.unit,
        ))
        db.flush()
    elif supply.producer_id != producer_id:
        raise MovementConflict("El insumo pertenece a otro usuario.")

    # La ubicación ya es del productor, así que un inventario con la misma ubicación también.
    inventory = db.get(Inventory, data.id)
    if inventory is None:
        inventory = Inventory(
            id=data.id,
            location_id=data.location.id,
            supply_id=data.supply.id,
            initial_quantity=data.initial_quantity,
            quantity=data.initial_quantity,
        )
        db.add(inventory)
        db.flush()
    elif inventory.location_id != data.location.id or inventory.supply_id != data.supply.id:
        raise MovementConflict("El inventario ya existe con otra ubicación o insumo.")
    return inventory


def _owner_of(db: Session, movement: Movement) -> uuid.UUID:
    inventory = db.get(Inventory, movement.inventory_id)
    return db.get(Location, inventory.location_id).producer_id


def _existing_result(db: Session, existing: Movement, data: MovementIn, producer_id: uuid.UUID) -> str:
    if _owner_of(db, existing) != producer_id:
        # Mismo mensaje que un contenido distinto: no se revela que el UUID es de otro usuario.
        raise MovementConflict("Ya existe un movimiento con este id y contenido distinto.")
    if _same_content(existing, data):
        return "DUPLICATE"
    raise MovementConflict("Ya existe un movimiento con este id y contenido distinto.")


def sync_movement(db: Session, data: MovementIn, producer_id: uuid.UUID) -> str:
    """Guarda el movimiento en el inventario de ``producer_id``.

    Devuelve ``CREATED`` o ``DUPLICATE``; lanza MovementConflict.
    """
    if data.type not in SUPPORTED_TYPES:
        raise MovementConflict(f"Tipo de movimiento no soportado todavía: {data.type}.")

    existing = db.get(Movement, data.id)
    if existing is not None:
        return _existing_result(db, existing, data, producer_id)

    try:
        with db.begin_nested():
            inventory = _ensure_inventory(db, data.inventory, producer_id)
            _check_parcel(db, data, producer_id)
            if data.type in SUBTRACTING_TYPES:
                new_quantity = inventory.quantity - data.quantity
                if new_quantity < 0:
                    raise MovementConflict("La cantidad supera la existencia disponible.")
            else:
                new_quantity = inventory.quantity + data.quantity
            if new_quantity > MAX_QUANTITY:
                raise MovementConflict("La existencia resultante supera 99.999,99.")
            db.add(
                Movement(
                    id=data.id,
                    inventory_id=inventory.id,
                    type=data.type,
                    quantity=data.quantity,
                    date=data.date,
                    observation=data.observation,
                    created_at=data.created_at,
                    updated_at=data.updated_at,
                    parcel_id=data.parcel_id,
                    cycle_id=data.cycle_id,
                    loss_reason=data.loss_reason,
                )
            )
            inventory.quantity = new_quantity
            db.flush()
    except IntegrityError:
        # Carrera: otra petición insertó el mismo UUID entre la lectura y la escritura.
        logger.warning("IntegrityError al guardar el movimiento %s", data.id, exc_info=True)
        db.rollback()
        existing = db.get(Movement, data.id)
        if existing is None:
            raise MovementConflict("No se pudo guardar el movimiento por una restricción de integridad.")
        return _existing_result(db, existing, data, producer_id)
    except MovementConflict:
        db.rollback()
        raise

    db.commit()
    return "CREATED"
