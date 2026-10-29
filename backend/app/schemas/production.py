"""Contrato propio del Equipo 3. No modifica mensajes de los otros equipos."""
import uuid
from datetime import date
from typing import Literal
from pydantic import AwareDatetime, Field
from app.schemas.movements import CamelModel, MovementIn
from app.schemas.recommendations import RecommendationOut


class HarvestIn(CamelModel):
    id: uuid.UUID
    parcel_id: uuid.UUID
    cycle_id: uuid.UUID
    recommendation_id: uuid.UUID
    aforamiento_id: uuid.UUID
    date: date
    quantity: int = Field(strict=True, gt=0, le=1_000_000_000)
    observation: str = Field(default="", max_length=500)
    exception_confirmed: bool = False
    created_at: AwareDatetime


class DispatchIn(CamelModel):
    id: uuid.UUID
    parcel_id: uuid.UUID
    cycle_id: uuid.UUID
    recommendation_id: uuid.UUID
    dispatched_at: AwareDatetime
    quantity: int = Field(strict=True, gt=0, le=1_000_000_000)
    vehicle_id: str = Field(default="", max_length=120)
    order: int = Field(strict=True, ge=1)
    observation: str = Field(default="", max_length=500)
    created_at: AwareDatetime
    consumptions: list[MovementIn] = Field(default_factory=list, max_length=100)
    # Ausente en peticiones anteriores: se preservan sus reintentos y consumos originales.
    packing_policy: Literal["legacy_decimal", "whole_units_v1"] = "legacy_decimal"


class ProductionHistoryOut(CamelModel):
    harvests: list[HarvestIn]
    dispatches: list[DispatchIn]
    recommendations: list[RecommendationOut]
