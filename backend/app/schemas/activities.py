"""Contrato HTTP de las actividades productivas (HU_08). JSON en camelCase, como el resto de la API."""

import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import Field

from app.schemas.movements import CamelModel

ActivityType = Literal[
    "FERTILIZACION", "SIEMBRA", "COSECHA", "APLICACION_INSUMO", "CONTROL_FITOSANITARIO", "OTRA",
]


class ActivityIn(CamelModel):
    id: uuid.UUID
    parcel_id: uuid.UUID
    # La actividad exige un ciclo: puede ser el activo o, durante el descanso, el último finalizado.
    cycle_id: uuid.UUID
    type: ActivityType
    date: date
    observation: str = Field(default="", max_length=500)
    created_at: datetime
    updated_at: datetime


class ActivitySyncOut(CamelModel):
    id: uuid.UUID
    # CREATED: se guardó ahora. DUPLICATE: ya existía idéntica.
    status: Literal["CREATED", "DUPLICATE"]
