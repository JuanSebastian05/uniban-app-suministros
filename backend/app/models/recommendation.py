"""Recomendaciones generadas por el servicio de logística del Equipo 2 (HU_12).

Cada solicitud exitosa agrega una fila (solo inserción, como movimientos y actividades): la app siempre muestra la más
reciente de la parcela. ``payload`` guarda la respuesta ya validada y normalizada (el mismo JSON que ve la app), de modo
que se puede servir sin volver a llamar al Equipo 2. El ``id`` lo genera el backend.
"""

import uuid
from datetime import datetime

from sqlalchemy import JSON, DateTime, ForeignKey, Index, String, Uuid, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

# JSONB en PostgreSQL; JSON genérico en SQLite (pruebas).
JsonDocument = JSON().with_variant(JSONB(), "postgresql")


class Recommendation(Base):
    __tablename__ = "recommendations"
    __table_args__ = (
        # Cubre «la recomendación más reciente de una parcela».
        Index("ix_recommendations_parcel_id_generated_at", "parcel_id", "generated_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    parcel_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("parcels.id"))
    # Semana ISO que planificó el Equipo 2 (por ejemplo «2026-W44»).
    period: Mapped[str] = mapped_column(String(20))
    # ``version_metodo`` del Equipo 2: trazabilidad del método que generó la recomendación.
    source_version: Mapped[str] = mapped_column(String(60))
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    payload: Mapped[dict] = mapped_column(JsonDocument)
    aforamiento_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("aforamientos.id"))
    cycle_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("production_cycles.id"))
