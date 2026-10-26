"""Contrato HTTP de los aforamientos del Equipo 1. JSON en camelCase, como el resto de la API."""

import uuid
from datetime import date
from decimal import ROUND_HALF_UP, Decimal
from typing import Literal

from pydantic import Field, field_validator

from app.schemas.movements import CamelModel

MAX_PLANTS = 2_147_483_647  # INTEGER de PostgreSQL


def _round(value: Decimal, places: str) -> Decimal:
    return value.quantize(Decimal(places), rounding=ROUND_HALF_UP)


class AforamientoIn(CamelModel):
    id: uuid.UUID
    parcel_id: uuid.UUID
    date: date
    # Informativa (no se contrasta con la parcela). Se redondea a dos decimales, como las áreas de la app.
    area_ha: Decimal = Field(gt=0, le=Decimal("99999.99"))
    estimated_plants: int = Field(ge=0, le=MAX_PLANTS)
    # Fracción entre 0 y 1; opcional. Se redondea a tres decimales.
    confidence: Decimal | None = Field(default=None, ge=0, le=1)

    @field_validator("area_ha")
    @classmethod
    def _round_area(cls, value: Decimal) -> Decimal:
        rounded = _round(value, "0.01")
        if rounded <= 0:
            raise ValueError("El área debe ser al menos 0,01 ha.")
        return rounded

    @field_validator("confidence")
    @classmethod
    def _round_confidence(cls, value: Decimal | None) -> Decimal | None:
        return None if value is None else _round(value, "0.001")


class AforamientoSyncOut(CamelModel):
    id: uuid.UUID
    # CREATED: se guardó ahora. DUPLICATE: ya existía idéntico.
    status: Literal["CREATED", "DUPLICATE"]


class AforamientoOut(CamelModel):
    """Aforamiento tal como lo ve la app (HU_09)."""

    id: uuid.UUID
    parcel_id: uuid.UUID
    date: date
    area_ha: Decimal
    estimated_plants: int
    confidence: Decimal | None
