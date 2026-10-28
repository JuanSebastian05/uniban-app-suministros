"""Parcelas y ciclos productivos (HU_14).

Productor → Parcela → Ciclo productivo. Cada parcela pertenece a un solo productor (el del
usuario que la registró) y tiene como máximo un ciclo activo (``end_date`` nula).
Los identificadores son UUID generados en el dispositivo (RT_06).
"""

import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import CheckConstraint, Date, DateTime, ForeignKey, Index, Numeric, String, UniqueConstraint, Uuid, func, text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

# En preparación y Producción son etapas de un ciclo; Descanso se conserva para compatibilidad histórica.
CYCLE_STATUSES = ("EN_PREPARACION", "PRODUCCION", "DESCANSO")

# Hectáreas con dos decimales, hasta 99.999,99 (mismo límite que las cantidades).
AreaHa = Numeric(9, 2)


def name_key(name: str) -> str:
    """Clave para detectar nombres repetidos: sin mayúsculas ni espacios sobrantes."""
    return " ".join(name.split()).casefold()


class Parcel(Base):
    __tablename__ = "parcels"
    __table_args__ = (
        UniqueConstraint("producer_id", "name_key", name="uq_parcels_producer_name"),
        CheckConstraint("area_ha > 0", name="ck_parcels_area_positive"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    producer_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("producers.id"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    # Nombre normalizado (ver ``name_key``): el nombre único por productor no distingue mayúsculas.
    name_key: Mapped[str] = mapped_column(String(120))
    area_ha: Mapped[Decimal] = mapped_column(AreaHa)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class ProductionCycle(Base):
    __tablename__ = "production_cycles"
    __table_args__ = (
        CheckConstraint(
            "status IN ('EN_PREPARACION', 'PRODUCCION', 'DESCANSO')", name="ck_production_cycles_status"
        ),
        CheckConstraint("end_date IS NULL OR end_date >= start_date", name="ck_production_cycles_dates"),
        # Un solo ciclo activo por parcela: índice único parcial (PostgreSQL y SQLite lo soportan).
        Index(
            "uq_production_cycles_active",
            "parcel_id",
            unique=True,
            postgresql_where=text("end_date IS NULL"),
            sqlite_where=text("end_date IS NULL"),
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    parcel_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("parcels.id"), index=True)
    start_date: Mapped[date] = mapped_column(Date)
    end_date: Mapped[date | None] = mapped_column(Date)
    status: Mapped[str] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class ProductionCycleStatusChange(Base):
    """Historial de etapas y cierres del ciclo, para conservar la evolución de la parcela."""
    __tablename__ = "production_cycle_status_changes"
    __table_args__ = (
        CheckConstraint(
            "to_status IN ('EN_PREPARACION', 'PRODUCCION', 'DESCANSO')",
            name="ck_cycle_status_changes_to_status",
        ),
        CheckConstraint(
            "from_status IS NULL OR from_status IN ('EN_PREPARACION', 'PRODUCCION', 'DESCANSO')",
            name="ck_cycle_status_changes_from_status",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    cycle_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("production_cycles.id", ondelete="CASCADE"), index=True)
    from_status: Mapped[str | None] = mapped_column(String(20))
    to_status: Mapped[str] = mapped_column(String(20))
    effective_date: Mapped[date] = mapped_column(Date)
    changed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
