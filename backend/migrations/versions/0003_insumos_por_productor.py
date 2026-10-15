"""Insumos con dueño: cada productor tiene su propio catálogo (inventario por usuario)

Revision ID: 0003
Revises: 0002
Create Date: 2026-10-14
"""

import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("supplies", sa.Column("producer_id", sa.Uuid(), nullable=True))
    op.create_foreign_key("fk_supplies_producer_id", "supplies", "producers", ["producer_id"], ["id"])
    op.create_index("ix_supplies_producer_id", "supplies", ["producer_id"])
    # Los insumos existentes quedan a nombre del productor de su inventario (los datos de demostración).
    op.execute(
        """
        UPDATE supplies SET producer_id = (
            SELECT l.producer_id FROM inventory i JOIN locations l ON l.id = i.location_id
            WHERE i.supply_id = supplies.id LIMIT 1
        )
        """
    )


def downgrade() -> None:
    op.drop_index("ix_supplies_producer_id", table_name="supplies")
    op.drop_constraint("fk_supplies_producer_id", "supplies", type_="foreignkey")
    op.drop_column("supplies", "producer_id")
