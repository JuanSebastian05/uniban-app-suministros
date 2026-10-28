"""Historial de cambios de etapa y cierre de ciclos.

Revision ID: 0012
Revises: 0011
Create Date: 2026-10-28
"""

import sqlalchemy as sa
from alembic import op


revision = "0012"
down_revision = "0011"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "production_cycle_status_changes",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "cycle_id", sa.Uuid(), sa.ForeignKey("production_cycles.id", ondelete="CASCADE"), nullable=False,
        ),
        sa.Column("from_status", sa.String(length=20), nullable=True),
        sa.Column("to_status", sa.String(length=20), nullable=False),
        sa.Column("effective_date", sa.Date(), nullable=False),
        sa.Column("changed_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint(
            "to_status IN ('EN_PREPARACION', 'PRODUCCION', 'DESCANSO')",
            name="ck_cycle_status_changes_to_status",
        ),
        sa.CheckConstraint(
            "from_status IS NULL OR from_status IN ('EN_PREPARACION', 'PRODUCCION', 'DESCANSO')",
            name="ck_cycle_status_changes_from_status",
        ),
    )
    op.create_index(
        "ix_production_cycle_status_changes_cycle_id", "production_cycle_status_changes", ["cycle_id"],
    )


def downgrade() -> None:
    op.drop_index("ix_production_cycle_status_changes_cycle_id", table_name="production_cycle_status_changes")
    op.drop_table("production_cycle_status_changes")
