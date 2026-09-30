"""Contrato HTTP de sincronización de movimientos (HU_06).

El JSON usa camelCase para coincidir con los nombres de la app Android.
Cada movimiento viaja con el contexto de su inventario (productor, ubicación, insumo):
el backend lo inserta solo si todavía no existe.
"""

import uuid
from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ProducerIn(CamelModel):
    id: uuid.UUID
    name: str = Field(min_length=1, max_length=120)


class LocationIn(CamelModel):
    id: uuid.UUID
    name: str = Field(min_length=1, max_length=120)
    producer: ProducerIn


class SupplyIn(CamelModel):
    id: uuid.UUID
    name: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=60)
    unit: str = Field(min_length=1, max_length=20)


class InventoryIn(CamelModel):
    id: uuid.UUID
    initial_quantity: Decimal = Field(ge=0, le=Decimal("99999.99"), max_digits=9, decimal_places=2)
    location: LocationIn
    supply: SupplyIn


class MovementIn(CamelModel):
    id: uuid.UUID
    type: str = Field(min_length=1, max_length=20)
    quantity: Decimal = Field(gt=0, le=Decimal("99999.99"), max_digits=9, decimal_places=2)
    date: date
    observation: str = Field(default="", max_length=500)
    created_at: datetime
    updated_at: datetime
    inventory: InventoryIn


class MovementSyncOut(CamelModel):
    id: uuid.UUID
    # CREATED: se guardó ahora. DUPLICATE: ya existía idéntico; no se volvió a sumar.
    status: Literal["CREATED", "DUPLICATE"]
