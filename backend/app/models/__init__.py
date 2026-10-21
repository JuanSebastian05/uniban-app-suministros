"""Modelos ORM (SQLAlchemy) de PostgreSQL.

Importar aquí cada modelo para que ``Base.metadata`` (y Alembic) los conozca.
"""

from app.models.inventory import Inventory, Location, Movement, Producer, Supply
from app.models.parcel import Parcel, ProductionCycle
from app.models.user import User

__all__ = ["Inventory", "Location", "Movement", "Parcel", "Producer", "ProductionCycle", "Supply", "User"]
