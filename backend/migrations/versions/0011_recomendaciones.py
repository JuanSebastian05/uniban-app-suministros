"""Recomendaciones del Equipo 2 (HU_12): tabla recommendations

Revision ID: 0011
Revises: 0010
Create Date: 2026-10-26
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "0011"
down_revision = "0010"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "recommendations",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("parcel_id", sa.Uuid(), sa.ForeignKey("parcels.id"), nullable=False),
        sa.Column("period", sa.String(20), nullable=False),
        sa.Column("source_version", sa.String(60), nullable=False),
        sa.Column("generated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        sa.Column("payload", sa.JSON().with_variant(postgresql.JSONB(), "postgresql"), nullable=False),
    )
    op.create_index(
        "ix_recommendations_parcel_id_generated_at", "recommendations", ["parcel_id", "generated_at"],
    )


def downgrade() -> None:
    op.drop_index("ix_recommendations_parcel_id_generated_at", table_name="recommendations")
    op.drop_table("recommendations")
