"""Alta de usuarios productores (HU_01, registro).

Idempotente por ``firebase_uid``: si la app reintenta el registro, se devuelve el usuario
existente sin crear otro productor.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.security import FirebaseIdentity
from app.models import Producer, User
from app.models.user import ROLE_PRODUCER
from app.schemas.users import RegisterIn


def find_user(db: Session, firebase_uid: str) -> User | None:
    return db.scalar(select(User).where(User.firebase_uid == firebase_uid))


def register_user(db: Session, identity: FirebaseIdentity, data: RegisterIn) -> tuple[User, bool]:
    """Devuelve ``(usuario, creado)``. El productor del usuario se crea en la misma transacción."""
    existing = find_user(db, identity.uid)
    if existing is not None:
        return existing, False
    name = data.name.strip()
    producer = Producer(id=uuid.uuid4(), name=name)
    user = User(
        firebase_uid=identity.uid,
        email=identity.email or "",
        name=name,
        document=data.document.strip(),
        phone=(data.phone or "").strip() or None,
        role=ROLE_PRODUCER,
        producer_id=producer.id,
    )
    db.add(producer)
    db.flush()  # el productor debe existir antes que la llave foránea del usuario
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        # Dos solicitudes simultáneas de la misma cuenta: se conserva la primera.
        db.rollback()
        winner = find_user(db, identity.uid)
        if winner is None:
            raise
        return winner, False
    return user, True
