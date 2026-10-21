"""Consulta del inventario de un productor (HU_03 en el backend)."""

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Inventory, Location, Supply
from app.schemas.movements import InventoryItemOut


def list_inventory(db: Session, producer_id: uuid.UUID) -> list[InventoryItemOut]:
    """Solo los insumos de las ubicaciones de ``producer_id``, ordenados por nombre."""
    rows = db.execute(
        select(Inventory, Supply, Location)
        .join(Location, Inventory.location_id == Location.id)
        .join(Supply, Inventory.supply_id == Supply.id)
        .where(Location.producer_id == producer_id)
        .order_by(Supply.name, Location.name)
    ).all()
    return [
        InventoryItemOut(
            id=inventory.id, supply_id=supply.id, name=supply.name, category=supply.category,
            unit=supply.unit, location=location.name, quantity=inventory.quantity,
        )
        for inventory, supply, location in rows
    ]
