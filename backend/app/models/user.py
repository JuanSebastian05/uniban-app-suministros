"""Usuario registrado en la app (HU_01, registro).

Firebase guarda solo la identidad (RT_05); el perfil y el rol viven aquí. Cada usuario
productor queda ligado a su propio registro de ``producers``: es el dueño previsto de sus
ubicaciones e inventario cuando se separen los datos por cuenta.
"""

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base

ROLE_PRODUCER = "PRODUCTOR"


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    firebase_uid: Mapped[str] = mapped_column(String(128), unique=True)
    email: Mapped[str] = mapped_column(String(254))
    name: Mapped[str] = mapped_column(String(120))
    document: Mapped[str] = mapped_column(String(30))
    phone: Mapped[str | None] = mapped_column(String(30))
    role: Mapped[str] = mapped_column(String(20), default=ROLE_PRODUCER)
    producer_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("producers.id"), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
