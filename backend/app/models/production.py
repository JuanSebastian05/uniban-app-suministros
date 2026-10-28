"""Operaciones reales, separadas de las estimaciones y del inventario de insumos."""
import uuid
from datetime import date, datetime
from sqlalchemy import Boolean, CheckConstraint, Date, DateTime, ForeignKey, Index, Integer, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base
from app.models.recommendation import JsonDocument


class Harvest(Base):
    __tablename__ = "harvests"
    __table_args__ = (
        CheckConstraint("quantity > 0", name="ck_harvests_quantity"),
        Index("ix_harvests_parcel_period", "parcel_id", "period"),
    )
    # Una actividad COSECHA y su detalle comparten la identidad, no son dos operaciones.
    id: Mapped[uuid.UUID] = mapped_column(ForeignKey("activities.id"), primary_key=True)
    parcel_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("parcels.id"))
    cycle_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("production_cycles.id"))
    recommendation_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("recommendations.id"))
    aforamiento_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("aforamientos.id"))
    period: Mapped[str] = mapped_column(String(20))
    quantity: Mapped[int] = mapped_column(Integer)
    date: Mapped[date] = mapped_column(Date)
    exception_confirmed: Mapped[bool] = mapped_column(Boolean)
    request_payload: Mapped[dict] = mapped_column(JsonDocument)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Dispatch(Base):
    __tablename__ = "dispatches"
    __table_args__ = (
        CheckConstraint("quantity > 0", name="ck_dispatches_quantity"),
        Index("ix_dispatches_parcel_period", "parcel_id", "period"),
    )
    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True)
    parcel_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("parcels.id"))
    cycle_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("production_cycles.id"))
    recommendation_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("recommendations.id"))
    period: Mapped[str] = mapped_column(String(20))
    quantity: Mapped[int] = mapped_column(Integer)
    dispatched_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    request_payload: Mapped[dict] = mapped_column(JsonDocument)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
