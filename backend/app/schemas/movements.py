"""Contrato HTTP de sincronización de movimientos (HU_06).

El JSON usa camelCase para coincidir con los nombres de la app Android.
Cada movimiento viaja con el contexto de su inventario (ubicación, insumo): el backend lo
inserta solo si todavía no existe, siempre a nombre del productor del usuario autenticado.
"""

import uuid
from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ProducerIn(CamelModel):
    id: uuid.UUID
    name: str = Field(min_length=1, max_length=120)


class LocationIn(CamelModel):
    id: uuid.UUID
    name: str = Field(min_length=1, max_length=120)
    # Se ignora: el dueño es siempre el productor del token. Se acepta por compatibilidad.
    producer: ProducerIn | None = None


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


LossReason = Literal["DESPERDICIO", "MAL_MANEJO", "CLIMA", "OTRO"]
AdjustDirection = Literal["AUMENTO", "DISMINUCION"]


class MovementIn(CamelModel):
    id: uuid.UUID
    type: str = Field(min_length=1, max_length=20)
    quantity: Decimal = Field(gt=0, le=Decimal("99999.99"), max_digits=9, decimal_places=2)
    date: date
    observation: str = Field(default="", max_length=500)
    created_at: datetime
    updated_at: datetime
    inventory: InventoryIn
    # HU_05: un CONSUMO exige parcela (el ciclo es opcional); una PERDIDA exige motivo.
    parcel_id: uuid.UUID | None = None
    cycle_id: uuid.UUID | None = None
    loss_reason: LossReason | None = None
    # Un AJUSTE corrige la existencia: exige el sentido (suma o resta) y el motivo en la observación.
    adjust_direction: AdjustDirection | None = None

    @model_validator(mode="after")
    def _check_type_fields(self) -> "MovementIn":
        if self.type == "AJUSTE":
            if self.adjust_direction is None:
                raise ValueError("Un ajuste debe indicar si aumenta o disminuye la existencia.")
            if not self.observation.strip():
                raise ValueError("Un ajuste exige el motivo en la observación.")
        elif self.adjust_direction is not None:
            raise ValueError("Solo los ajustes llevan sentido.")
        if self.type == "CONSUMO" and self.parcel_id is None:
            raise ValueError("Un consumo debe indicar la parcela.")
        if self.type == "PERDIDA":
            if self.loss_reason is None:
                raise ValueError("Una pérdida debe indicar el motivo.")
            if self.loss_reason == "OTRO" and not self.observation.strip():
                raise ValueError("El motivo «Otro» exige una observación.")
        if self.type != "CONSUMO" and (self.parcel_id is not None or self.cycle_id is not None):
            raise ValueError("Solo los consumos llevan parcela y ciclo.")
        if self.type != "PERDIDA" and self.loss_reason is not None:
            raise ValueError("Solo las pérdidas llevan motivo.")
        return self


class MovementSyncOut(CamelModel):
    id: uuid.UUID
    # CREATED: se guardó ahora. DUPLICATE: ya existía idéntico; no se volvió a sumar.
    status: Literal["CREATED", "DUPLICATE"]


class InventoryItemOut(CamelModel):
    id: uuid.UUID
    supply_id: uuid.UUID
    name: str
    category: str
    unit: str
    location: str
    quantity: Decimal
