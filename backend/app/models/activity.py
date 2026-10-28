"""Actividades productivas (HU_08): siembra, fertilización, cosecha, etc.

Una actividad se registra sobre una parcela y su ciclo (el ciclo es obligatorio: puede ser el activo o,
durante el descanso, el último ciclo finalizado). Los consumos de insumos que la acompañan son movimientos CONSUMO que
apuntan a ella con ``movements.activity_id``. Los identificadores son UUID generados en el dispositivo (RT_06).
"""

import uuid
from datetime import date, datetime

from sqlalchemy import CheckConstraint, Date, DateTime, ForeignKey, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

# Tipos de los mockups: Fertilización, Siembra, Cosecha, Aplicación de insumo, Control fitosanitario, Otra.
ACTIVITY_TYPES = (
    "FERTILIZACION", "SIEMBRA", "COSECHA", "APLICACION_INSUMO", "CONTROL_FITOSANITARIO", "OTRA",
)


class Activity(Base):
    __tablename__ = "activities"
    __table_args__ = (
        CheckConstraint(
            "type IN ('FERTILIZACION', 'SIEMBRA', 'COSECHA', 'APLICACION_INSUMO', "
            "'CONTROL_FITOSANITARIO', 'OTRA')",
            name="ck_activities_type",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    parcel_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("parcels.id"), index=True)
    cycle_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("production_cycles.id"))
    type: Mapped[str] = mapped_column(String(30))
    date: Mapped[date] = mapped_column(Date)
    observation: Mapped[str] = mapped_column(String(500), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
