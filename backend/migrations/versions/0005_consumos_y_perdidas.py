"""Consumos y pérdidas (HU_05): parcela, ciclo y motivo en los movimientos

Revision ID: 0005
Revises: 0004
Create Date: 2026-10-15
"""

import sqlalchemy as sa
from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Columnas nulas: los movimientos existentes (entradas) no se modifican.
    op.add_column("movements", sa.Column("parcel_id", sa.Uuid(), nullable=True))
    op.add_column("movements", sa.Column("cycle_id", sa.Uuid(), nullable=True))
    op.add_column("movements", sa.Column("loss_reason", sa.String(20), nullable=True))
    op.create_foreign_key("fk_movements_parcel_id", "movements", "parcels", ["parcel_id"], ["id"])
    op.create_foreign_key("fk_movements_cycle_id", "movements", "production_cycles", ["cycle_id"], ["id"])
    op.create_index("ix_movements_parcel_id", "movements", ["parcel_id"])
    op.create_check_constraint(
        "ck_movements_loss_reason",
        "movements",
        "loss_reason IS NULL OR loss_reason IN ('DESPERDICIO', 'MAL_MANEJO', 'CLIMA', 'OTRO')",
    )


def downgrade() -> None:
    op.drop_constraint("ck_movements_loss_reason", "movements", type_="check")
    op.drop_index("ix_movements_parcel_id", table_name="movements")
    op.drop_constraint("fk_movements_cycle_id", "movements", type_="foreignkey")
    op.drop_constraint("fk_movements_parcel_id", "movements", type_="foreignkey")
    op.drop_column("movements", "loss_reason")
    op.drop_column("movements", "cycle_id")
    op.drop_column("movements", "parcel_id")
