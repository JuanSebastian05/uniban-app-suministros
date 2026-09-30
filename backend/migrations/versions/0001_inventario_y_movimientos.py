"""Tablas de inventario y movimientos (HU_06)

Revision ID: 0001
Revises:
Create Date: 2026-09-29
"""

import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "producers",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(120), nullable=False),
    )
    op.create_table(
        "locations",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("producer_id", sa.Uuid(), sa.ForeignKey("producers.id"), nullable=False),
        sa.Column("name", sa.String(120), nullable=False),
    )
    op.create_index("ix_locations_producer_id", "locations", ["producer_id"])
    op.create_table(
        "supplies",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(120), nullable=False),
        sa.Column("category", sa.String(60), nullable=False),
        sa.Column("unit", sa.String(20), nullable=False),
    )
    op.create_table(
        "inventory",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("location_id", sa.Uuid(), sa.ForeignKey("locations.id"), nullable=False),
        sa.Column("supply_id", sa.Uuid(), sa.ForeignKey("supplies.id"), nullable=False),
        sa.Column("initial_quantity", sa.Numeric(9, 2), nullable=False),
        sa.Column("quantity", sa.Numeric(9, 2), nullable=False),
        sa.UniqueConstraint("location_id", "supply_id", name="uq_inventory_location_supply"),
    )
    op.create_index("ix_inventory_supply_id", "inventory", ["supply_id"])
    op.create_table(
        "movements",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("inventory_id", sa.Uuid(), sa.ForeignKey("inventory.id"), nullable=False),
        sa.Column("type", sa.String(20), nullable=False),
        sa.Column("quantity", sa.Numeric(9, 2), nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("observation", sa.String(500), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint("quantity > 0", name="ck_movements_quantity_positive"),
    )
    op.create_index("ix_movements_inventory_id", "movements", ["inventory_id"])


def downgrade() -> None:
    op.drop_table("movements")
    op.drop_table("inventory")
    op.drop_table("supplies")
    op.drop_table("locations")
    op.drop_table("producers")
