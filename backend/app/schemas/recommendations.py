"""Contratos de las recomendaciones (HU_12).

- ``Equipo2*``: lo que **recibimos** del servicio del Equipo 2 (``RespuestaRecomendacion`` de su OpenAPI), en snake_case.
  Se valida la forma completa; lo que no la cumpla es un error controlado (502), nunca una excepción sin manejar.
- ``RecommendationOut``: lo que **servimos** a la app, en camelCase y en inglés como el resto de la API.
"""

import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.movements import CamelModel


class _Equipo2Model(BaseModel):
    # Los campos nuevos que agregue el Equipo 2 se ignoran; los que faltan o tienen otro tipo, no.
    model_config = ConfigDict(extra="ignore")


class Equipo2ItemPlan(_Equipo2Model):
    parcela_id: str
    fecha_corte: datetime
    vehiculo_id: str | None = None
    orden: int = Field(ge=1)
    carga_estimada_racimos: int = Field(ge=0)
    cumple_ventana: bool
    motivo: str | None = None


class Equipo2Insumo(_Equipo2Model):
    insumo: Literal["cajas", "carton", "bolsas"]
    fecha: date
    cantidad_requerida: float = Field(ge=0)
    inventario_disponible: float = Field(ge=0)
    deficit: float = Field(ge=0)


class Equipo2Metricas(_Equipo2Model):
    cumplimiento_ventana: float = Field(ge=0, le=1)
    uso_capacidad: float = Field(ge=0)


class Equipo2Respuesta(_Equipo2Model):
    parcela_id: str
    periodo: str = Field(pattern=r"^\d{4}-W(?:0[1-9]|[1-4]\d|5[0-3])$")
    volumen_racimos: float = Field(ge=0)
    fecha_limite_corte: datetime
    cumple_ventana: bool
    version_metodo: str
    item_plan: Equipo2ItemPlan
    insumos: list[Equipo2Insumo]
    metricas: Equipo2Metricas


class RecommendationPlanOut(CamelModel):
    """Plan de corte completo que entrega el Equipo 2."""

    parcel_id: str
    cut_date: datetime
    order: int
    estimated_load_bunches: int
    meets_window: bool
    vehicle_id: str | None
    reason: str | None


class RecommendationSupplyOut(CamelModel):
    """Un insumo de empaque con su necesidad. El Equipo 2 no entrega «exceso»: solo requerido, disponible y déficit."""

    name: str
    date: date
    required_quantity: float
    available_quantity: float
    deficit: float


class RecommendationMetricsOut(CamelModel):
    window_compliance: float
    capacity_use: float


class RecommendationOut(CamelModel):
    id: uuid.UUID
    parcel_id: uuid.UUID
    period: str
    generated_at: datetime
    source_version: str
    volume_bunches: float
    cut_deadline: datetime
    meets_window: bool
    plan: RecommendationPlanOut
    supplies: list[RecommendationSupplyOut]
    metrics: RecommendationMetricsOut
