"""Modelos ORM (SQLAlchemy) de PostgreSQL.

Importar aquí cada modelo para que ``Base.metadata`` (y Alembic) los conozca.
"""

from app.models.activity import Activity
from app.models.aforamiento import Aforamiento
from app.models.inventory import Inventory, Location, Movement, Producer, Supply
from app.models.parcel import Parcel, ProductionCycle, ProductionCycleStatusChange
from app.models.recommendation import Recommendation
from app.models.user import User
from app.models.production import Harvest, Dispatch

__all__ = [
    "Activity", "Aforamiento", "Inventory", "Location", "Movement", "Parcel", "Producer", "ProductionCycle",
    "ProductionCycleStatusChange", "Recommendation", "Supply", "User", "Harvest", "Dispatch",
]
