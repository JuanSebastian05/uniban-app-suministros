"""Cosechas estructuradas, despachos y trazabilidad del plan semanal.

Revision ID: 0013
Revises: 0012
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0013"
down_revision = "0012"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("recommendations", sa.Column("aforamiento_id", sa.Uuid(), sa.ForeignKey("aforamientos.id"), nullable=True))
    op.add_column("recommendations", sa.Column("cycle_id", sa.Uuid(), sa.ForeignKey("production_cycles.id"), nullable=True))
    op.create_table("harvests",
        sa.Column("id", sa.Uuid(), sa.ForeignKey("activities.id"), primary_key=True),
        sa.Column("parcel_id", sa.Uuid(), sa.ForeignKey("parcels.id"), nullable=False),
        sa.Column("cycle_id", sa.Uuid(), sa.ForeignKey("production_cycles.id"), nullable=False),
        sa.Column("recommendation_id", sa.Uuid(), sa.ForeignKey("recommendations.id"), nullable=False),
        sa.Column("aforamiento_id", sa.Uuid(), sa.ForeignKey("aforamientos.id"), nullable=False),
        sa.Column("period", sa.String(20), nullable=False), sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("date", sa.Date(), nullable=False), sa.Column("exception_confirmed", sa.Boolean(), nullable=False),
        sa.Column("request_payload", postgresql.JSONB(), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint("quantity > 0", name="ck_harvests_quantity"))
    op.create_index("ix_harvests_parcel_period", "harvests", ["parcel_id", "period"])
    op.create_table("dispatches",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("parcel_id", sa.Uuid(), sa.ForeignKey("parcels.id"), nullable=False),
        sa.Column("cycle_id", sa.Uuid(), sa.ForeignKey("production_cycles.id"), nullable=False),
        sa.Column("recommendation_id", sa.Uuid(), sa.ForeignKey("recommendations.id"), nullable=False),
        sa.Column("period", sa.String(20), nullable=False), sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("dispatched_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("request_payload", postgresql.JSONB(), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint("quantity > 0", name="ck_dispatches_quantity"))
    op.create_index("ix_dispatches_parcel_period", "dispatches", ["parcel_id", "period"])
    op.add_column("movements", sa.Column("dispatch_id", sa.Uuid(), sa.ForeignKey("dispatches.id"), nullable=True))
    op.create_index("ix_movements_dispatch_id", "movements", ["dispatch_id"])


def downgrade():
    op.drop_index("ix_movements_dispatch_id", table_name="movements")
    op.drop_constraint("movements_dispatch_id_fkey", "movements", type_="foreignkey")
    op.drop_column("movements", "dispatch_id")
    op.drop_table("dispatches")
    op.drop_table("harvests")
    op.drop_constraint("recommendations_cycle_id_fkey", "recommendations", type_="foreignkey")
    op.drop_constraint("recommendations_aforamiento_id_fkey", "recommendations", type_="foreignkey")
    op.drop_column("recommendations", "cycle_id")
    op.drop_column("recommendations", "aforamiento_id")
