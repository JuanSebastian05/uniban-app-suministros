"""Modelos ORM del inventario y sus movimientos (espejo del modelo local de la app).

Productor → Ubicación → Inventario → Movimiento, con Insumo como catálogo.
Los identificadores son UUID generados en el dispositivo (RT_06). Los movimientos
son solo de inserción (RT_07): las correcciones futuras serán movimientos AJUSTE.
"""

import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import CheckConstraint, Date, DateTime, ForeignKey, Numeric, String, UniqueConstraint, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

# Hasta 99.999,99: mismo límite que valida la app.
Quantity = Numeric(9, 2)


class Producer(Base):
    __tablename__ = "producers"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    name: Mapped[str] = mapped_column(String(120))


class Location(Base):
    __tablename__ = "locations"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    producer_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("producers.id"), index=True)
    name: Mapped[str] = mapped_column(String(120))


class Supply(Base):
    __tablename__ = "supplies"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    category: Mapped[str] = mapped_column(String(60))
    unit: Mapped[str] = mapped_column(String(20))


class Inventory(Base):
    __tablename__ = "inventory"
    __table_args__ = (UniqueConstraint("location_id", "supply_id", name="uq_inventory_location_supply"),)

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    location_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("locations.id"))
    supply_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("supplies.id"), index=True)
    initial_quantity: Mapped[Decimal] = mapped_column(Quantity)
    # Existencia = inicial + movimientos recibidos; se actualiza en la misma transacción (RT_10).
    quantity: Mapped[Decimal] = mapped_column(Quantity)


class Movement(Base):
    __tablename__ = "movements"
    __table_args__ = (CheckConstraint("quantity > 0", name="ck_movements_quantity_positive"),)

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    inventory_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("inventory.id"), index=True)
    type: Mapped[str] = mapped_column(String(20))
    quantity: Mapped[Decimal] = mapped_column(Quantity)
    date: Mapped[date] = mapped_column(Date)
    observation: Mapped[str] = mapped_column(String(500), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
