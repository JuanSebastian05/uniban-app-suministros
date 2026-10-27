"""Aforamientos del Equipo 1 (HU_09): tabla aforamientos

Revision ID: 0010
Revises: 0009
Create Date: 2026-10-26
"""

import sqlalchemy as sa
from alembic import op

revision = "0010"
down_revision = "0009"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "aforamientos",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("parcel_id", sa.Uuid(), sa.ForeignKey("parcels.id"), nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("area_ha", sa.Numeric(9, 2), nullable=False),
        sa.Column("estimated_plants", sa.Integer(), nullable=False),
        sa.Column("confidence", sa.Numeric(4, 3), nullable=True),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.CheckConstraint("area_ha > 0", name="ck_aforamientos_area_positive"),
        sa.CheckConstraint("estimated_plants >= 0", name="ck_aforamientos_plants_non_negative"),
        sa.CheckConstraint(
            "confidence IS NULL OR (confidence >= 0 AND confidence <= 1)", name="ck_aforamientos_confidence"
        ),
    )
    op.create_index("ix_aforamientos_parcel_id_date", "aforamientos", ["parcel_id", "date"])


def downgrade() -> None:
    op.drop_index("ix_aforamientos_parcel_id_date", table_name="aforamientos")
    op.drop_table("aforamientos")
