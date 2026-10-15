"""Parcelas y ciclos productivos (HU_14)

Revision ID: 0004
Revises: 0003
Create Date: 2026-10-15
"""

import sqlalchemy as sa
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "parcels",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("producer_id", sa.Uuid(), sa.ForeignKey("producers.id"), nullable=False),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("name_key", sa.String(120), nullable=False),
        sa.Column("area_ha", sa.Numeric(9, 2), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("producer_id", "name_key", name="uq_parcels_producer_name"),
        sa.CheckConstraint("area_ha > 0", name="ck_parcels_area_positive"),
    )
    op.create_index("ix_parcels_producer_id", "parcels", ["producer_id"])

    op.create_table(
        "production_cycles",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("parcel_id", sa.Uuid(), sa.ForeignKey("parcels.id"), nullable=False),
        sa.Column("start_date", sa.Date(), nullable=False),
        sa.Column("end_date", sa.Date(), nullable=True),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint(
            "status IN ('EN_PREPARACION', 'PRODUCCION', 'DESCANSO')", name="ck_production_cycles_status"
        ),
        sa.CheckConstraint("end_date IS NULL OR end_date >= start_date", name="ck_production_cycles_dates"),
    )
    op.create_index("ix_production_cycles_parcel_id", "production_cycles", ["parcel_id"])
    # Un solo ciclo activo (sin fecha de fin) por parcela.
    op.create_index(
        "uq_production_cycles_active",
        "production_cycles",
        ["parcel_id"],
        unique=True,
        postgresql_where=sa.text("end_date IS NULL"),
    )


def downgrade() -> None:
    op.drop_index("uq_production_cycles_active", table_name="production_cycles")
    op.drop_index("ix_production_cycles_parcel_id", table_name="production_cycles")
    op.drop_table("production_cycles")
    op.drop_index("ix_parcels_producer_id", table_name="parcels")
    op.drop_table("parcels")
