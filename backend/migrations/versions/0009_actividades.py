"""Actividades productivas (HU_08): tabla activities y movements.activity_id

Revision ID: 0009
Revises: 0008
Create Date: 2026-10-24
"""

import sqlalchemy as sa
from alembic import op

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "activities",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("parcel_id", sa.Uuid(), sa.ForeignKey("parcels.id"), nullable=False),
        sa.Column("cycle_id", sa.Uuid(), sa.ForeignKey("production_cycles.id"), nullable=False),
        sa.Column("type", sa.String(30), nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("observation", sa.String(500), nullable=False, server_default=""),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.CheckConstraint(
            "type IN ('FERTILIZACION', 'SIEMBRA', 'COSECHA', 'APLICACION_INSUMO', "
            "'CONTROL_FITOSANITARIO', 'OTRA')",
            name="ck_activities_type",
        ),
    )
    op.create_index("ix_activities_parcel_id", "activities", ["parcel_id"])
    # Columna nula: los movimientos existentes no pertenecen a ninguna actividad.
    op.add_column("movements", sa.Column("activity_id", sa.Uuid(), nullable=True))
    op.create_foreign_key("fk_movements_activity_id", "movements", "activities", ["activity_id"], ["id"])
    op.create_index("ix_movements_activity_id", "movements", ["activity_id"])


def downgrade() -> None:
    op.drop_index("ix_movements_activity_id", table_name="movements")
    op.drop_constraint("fk_movements_activity_id", "movements", type_="foreignkey")
    op.drop_column("movements", "activity_id")
    op.drop_index("ix_activities_parcel_id", table_name="activities")
    op.drop_table("activities")
