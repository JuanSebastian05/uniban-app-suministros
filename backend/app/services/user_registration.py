"""Alta de usuarios productores (HU_01, registro; HU_13).

Idempotente por ``firebase_uid``: si la app reintenta el registro, se devuelve el usuario
existente sin crear otro productor. El documento de identidad es único (HU_13, criterio 3):
otra cuenta que intente usarlo recibe :class:`DuplicateDocument`.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.security import FirebaseIdentity
from app.models import Producer, User
from app.models.user import ROLE_PRODUCER, document_key
from app.schemas.users import RegisterIn

DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT"


class DuplicateDocument(Exception):
    """El documento ya pertenece a otra cuenta (HTTP 409, ``detail.code`` = DUPLICATE_DOCUMENT)."""

    code = DUPLICATE_DOCUMENT
    message = "Ya existe una cuenta con este documento."


def find_user(db: Session, firebase_uid: str) -> User | None:
    return db.scalar(select(User).where(User.firebase_uid == firebase_uid))


def _document_taken(db: Session, key: str) -> bool:
    return db.scalar(select(User.id).where(User.document_key == key)) is not None


def register_user(db: Session, identity: FirebaseIdentity, data: RegisterIn) -> tuple[User, bool]:
    """Devuelve ``(usuario, creado)``. El productor del usuario se crea en la misma transacción.

    Lanza :class:`DuplicateDocument` si otra cuenta ya usa el documento.
    """
    existing = find_user(db, identity.uid)
    if existing is not None:
        return existing, False
    name = data.name.strip()
    document = data.document.strip()
    key = document_key(document)
    if _document_taken(db, key):
        raise DuplicateDocument()
    producer = Producer(id=uuid.uuid4(), name=name)
    user = User(
        firebase_uid=identity.uid,
        email=identity.email or "",
        name=name,
        document=document,
        document_key=key,
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
        # Carrera: dos solicitudes simultáneas, de la misma cuenta (se conserva la primera) o de dos
        # cuentas con el mismo documento (gana la primera).
        db.rollback()
        winner = find_user(db, identity.uid)
        if winner is not None:
            return winner, False
        if _document_taken(db, key):
            raise DuplicateDocument() from None
        raise
    return user, True
