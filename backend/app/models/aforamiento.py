"""Aforamientos recibidos del Equipo 1 (estimación de plantas y área de una parcela en una fecha).

El Equipo 1 los envía por ``POST /api/v1/aforamientos`` con la clave de servicio; la app solo los consulta
(HU_09). El ``id`` lo genera el Equipo 1 y hace idempotente el reenvío. El área es informativa: no se contrasta
con la de la parcela.
"""

import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import CheckConstraint, Date, DateTime, ForeignKey, Index, Integer, Numeric, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base
from app.models.parcel import AreaHa


class Aforamiento(Base):
    __tablename__ = "aforamientos"
    __table_args__ = (
        CheckConstraint("area_ha > 0", name="ck_aforamientos_area_positive"),
        CheckConstraint("estimated_plants >= 0", name="ck_aforamientos_plants_non_negative"),
        CheckConstraint("confidence IS NULL OR (confidence >= 0 AND confidence <= 1)", name="ck_aforamientos_confidence"),
        # Cubre «el aforamiento más reciente de una parcela» y la búsqueda por parcela.
        Index("ix_aforamientos_parcel_id_date", "parcel_id", "date"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    parcel_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("parcels.id"))
    date: Mapped[date] = mapped_column(Date)
    area_ha: Mapped[Decimal] = mapped_column(AreaHa)
    estimated_plants: Mapped[int] = mapped_column(Integer)
    # Opcional («si existe, confianza», HU_09): fracción entre 0 y 1.
    confidence: Mapped[Decimal | None] = mapped_column(Numeric(4, 3))
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
