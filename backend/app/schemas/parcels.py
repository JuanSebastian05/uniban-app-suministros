"""Contrato HTTP de parcelas y ciclos productivos (HU_14). JSON en camelCase, como la app."""

import uuid
from datetime import date
from decimal import Decimal
from typing import Literal

from pydantic import Field, field_validator

from app.schemas.aforamientos import AforamientoOut
from app.schemas.movements import CamelModel

CycleStatus = Literal["EN_PREPARACION", "PRODUCCION", "DESCANSO"]


class CycleIn(CamelModel):
    id: uuid.UUID
    start_date: date
    status: Literal["EN_PREPARACION", "PRODUCCION"]


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


class CycleStageUpdateIn(CamelModel):
    """Etapa activa del ciclo. Descanso se registra cerrando el ciclo, no como su etapa."""
    status: Literal["EN_PREPARACION", "PRODUCCION"]
    effective_date: date


class CycleStartIn(CamelModel):
    id: uuid.UUID
    start_date: date


class CycleFinishIn(CamelModel):
    end_date: date


class CycleOut(CamelModel):
    id: uuid.UUID
    start_date: date
    end_date: date | None
    status: CycleStatus


class ServiceParcelOut(CamelModel):
    """Lo único que se entrega al Equipo 1: sin productor, documento, ciclo ni otros datos (privacidad)."""

    id: uuid.UUID
    name: str
    area_ha: Decimal


class ParcelOut(CamelModel):
    id: uuid.UUID
    name: str
    area_ha: Decimal
    active_cycle: CycleOut | None
    # Estado operativo de la parcela: etapa del ciclo, DESCANSO tras cerrar uno, o nulo si nunca inició.
    current_status: CycleStatus | None = None
    # Ciclo al que se pueden asociar actividades: el activo o, durante el descanso, el último finalizado.
    activity_cycle_id: uuid.UUID | None = None
    # Último ciclo cerrado, conservado localmente para asociar actividades durante el descanso.
    last_cycle: CycleOut | None = None
    # Aforamiento más reciente recibido del Equipo 1 (HU_09); nulo si la parcela no tiene ninguno.
    aforamiento: AforamientoOut | None = None
