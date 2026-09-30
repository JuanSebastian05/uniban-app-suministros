"""Modelos ORM (SQLAlchemy) de PostgreSQL.

Importar aquí cada modelo para que ``Base.metadata`` (y Alembic) los conozca.
"""

from app.models.inventory import Inventory, Location, Movement, Producer, Supply

__all__ = ["Inventory", "Location", "Movement", "Producer", "Supply"]
