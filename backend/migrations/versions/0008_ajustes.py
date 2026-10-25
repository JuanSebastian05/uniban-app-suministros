"""Ajustes de inventario: sentido (aumento o disminución) en los movimientos

Revision ID: 0008
Revises: 0007
Create Date: 2026-10-24
"""

import sqlalchemy as sa
from alembic import op

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Columna nula: los movimientos existentes no son ajustes y no se modifican.
    op.add_column("movements", sa.Column("adjust_direction", sa.String(12), nullable=True))
    op.create_check_constraint(
        "ck_movements_adjust_direction",
        "movements",
        "adjust_direction IS NULL OR adjust_direction IN ('AUMENTO', 'DISMINUCION')",
    )


def downgrade() -> None:
    op.drop_constraint("ck_movements_adjust_direction", "movements", type_="check")
    op.drop_column("movements", "adjust_direction")
