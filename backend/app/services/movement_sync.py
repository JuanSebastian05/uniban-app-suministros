"""Recepción idempotente de movimientos enviados por la app (HU_06, RT_07).

Reglas:
- El UUID del movimiento identifica el registro. Reenviar el mismo contenido no duplica
  ni vuelve a sumar (DUPLICATE); reenviar el mismo UUID con otro contenido es un conflicto.
- Los movimientos son solo de inserción. Por ahora solo se aceptan entradas.
- Productor, ubicación, insumo e inventario se insertan solo si no existen; nunca se
  sobrescriben. La existencia = inicial + movimientos recibidos, en la misma transacción.
"""

from decimal import Decimal

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models import Inventory, Location, Movement, Producer, Supply
from app.schemas.movements import InventoryIn, MovementIn

MAX_QUANTITY = Decimal("99999.99")
SUPPORTED_TYPES = {"ENTRADA"}


class MovementConflict(Exception):
    """El movimiento no puede aceptarse; el detalle se devuelve al cliente (HTTP 409)."""


def _same_content(existing: Movement, data: MovementIn) -> bool:
    return (
        existing.inventory_id == data.inventory.id
        and existing.type == data.type
        and existing.quantity == data.quantity
        and existing.date == data.date
        and existing.observation == data.observation
    )


def _ensure_inventory(db: Session, data: InventoryIn) -> Inventory:
    if db.get(Producer, data.location.producer.id) is None:
        db.add(Producer(id=data.location.producer.id, name=data.location.producer.name))
    if db.get(Location, data.location.id) is None:
        db.add(Location(id=data.location.id, producer_id=data.location.producer.id, name=data.location.name))
    if db.get(Supply, data.supply.id) is None:
        db.add(Supply(id=data.supply.id, name=data.supply.name, category=data.supply.category, unit=data.supply.unit))
    db.flush()

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


def _existing_result(existing: Movement, data: MovementIn) -> str:
    if _same_content(existing, data):
        return "DUPLICATE"
    raise MovementConflict("Ya existe un movimiento con este id y contenido distinto.")


def sync_movement(db: Session, data: MovementIn) -> str:
    """Guarda el movimiento. Devuelve ``CREATED`` o ``DUPLICATE``; lanza MovementConflict."""
    if data.type not in SUPPORTED_TYPES:
        raise MovementConflict(f"Tipo de movimiento no soportado todavía: {data.type}.")

    existing = db.get(Movement, data.id)
    if existing is not None:
        return _existing_result(existing, data)

    try:
        with db.begin_nested():
            inventory = _ensure_inventory(db, data.inventory)
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
                )
            )
            inventory.quantity = new_quantity
            db.flush()
    except IntegrityError:
        # Carrera: otra petición insertó el mismo UUID entre la lectura y la escritura.
        db.rollback()
        existing = db.get(Movement, data.id)
        if existing is None:
            raise MovementConflict("No se pudo guardar el movimiento por una restricción de integridad.")
        return _existing_result(existing, data)
    except MovementConflict:
        db.rollback()
        raise

    db.commit()
    return "CREATED"
