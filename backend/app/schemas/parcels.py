"""Contrato HTTP de parcelas y ciclos productivos (HU_14). JSON en camelCase, como la app."""

import uuid
from datetime import date
from decimal import Decimal
from typing import Literal

from pydantic import Field, field_validator

from app.schemas.movements import CamelModel

CycleStatus = Literal["EN_PREPARACION", "PRODUCCION", "DESCANSO"]


class CycleIn(CamelModel):
    id: uuid.UUID
    start_date: date
    status: CycleStatus


class ParcelIn(CamelModel):
    id: uuid.UUID
    name: str = Field(max_length=120)
    area_ha: Decimal = Field(gt=0, le=Decimal("99999.99"), max_digits=9, decimal_places=2)
    # Opcional: ciclo inicial de la parcela («Iniciar ciclo» en la app).
    cycle: CycleIn | None = None

    @field_validator("name")
    @classmethod
    def _clean_name(cls, value: str) -> str:
        cleaned = " ".join(value.split())
        if not cleaned:
            raise ValueError("El nombre es obligatorio.")
        return cleaned


class CycleOut(CamelModel):
    id: uuid.UUID
    start_date: date
    end_date: date | None
    status: CycleStatus


class ParcelOut(CamelModel):
    id: uuid.UUID
    name: str
    area_ha: Decimal
    active_cycle: CycleOut | None
