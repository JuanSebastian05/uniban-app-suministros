"""Documento de identidad único por usuario (HU_13, criterio 3)

Agrega ``users.document_key`` (documento sin mayúsculas, puntos, espacios ni guiones) con una
restricción única. Las cuentas existentes se rellenan por orden de creación: si dos comparten
documento, solo la más antigua conserva la clave y la otra queda en NULL (PostgreSQL admite varios
NULL en una restricción única), así la migración nunca falla ni borra datos.

Revision ID: 0007
Revises: 0005
Create Date: 2026-10-21

NOTA PARA EL EQUIPO: la migración 0006 está reservada para aforamientos (HU_09). Quien haga merge
en segundo lugar debe cambiar su ``down_revision`` al id de la anterior para mantener una sola
cabeza (``alembic heads`` debe mostrar un único resultado). Ver docs/avisos-equipo.md.
"""

import sqlalchemy as sa
from alembic import op

revision = "0007"
down_revision = "0005"
branch_labels = None
depends_on = None


def _key(document: str) -> str:
    # Misma regla que ``app.models.user.document_key`` (copiada: una migración no debe cambiar
    # si la aplicación cambia después).
    return "".join(char for char in document if char.isalnum()).upper()


def upgrade() -> None:
    op.add_column("users", sa.Column("document_key", sa.String(30), nullable=True))
    users = sa.table(
        "users",
        sa.column("id", sa.Uuid()),
        sa.column("document", sa.String()),
        sa.column("document_key", sa.String()),
        sa.column("created_at", sa.DateTime(timezone=True)),
    )
    connection = op.get_bind()
    rows = connection.execute(
        sa.select(users.c.id, users.c.document).order_by(users.c.created_at, users.c.id)
    ).all()
    taken: set[str] = set()
    for user_id, document in rows:
        key = _key(document or "")
        if key and key not in taken:
            taken.add(key)
            connection.execute(users.update().where(users.c.id == user_id).values(document_key=key))
    op.create_unique_constraint("uq_users_document_key", "users", ["document_key"])


def downgrade() -> None:
    op.drop_constraint("uq_users_document_key", "users", type_="unique")
    op.drop_column("users", "document_key")
