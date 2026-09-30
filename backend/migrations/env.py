"""Entorno de Alembic: usa el mismo engine y los mismos modelos que la aplicación."""

from alembic import context

import app.models  # noqa: F401  (registra los modelos en Base.metadata)
from app.db.base import Base
from app.db.session import get_engine

target_metadata = Base.metadata


def run_migrations_online() -> None:
    with get_engine().connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


run_migrations_online()
