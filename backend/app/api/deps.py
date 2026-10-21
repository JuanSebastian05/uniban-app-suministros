"""Dependencias compartidas por los endpoints."""

from fastapi import Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import FirebaseIdentity, get_current_identity
from app.db.session import get_db
from app.models import User
from app.services.user_registration import find_user


def get_current_user(
    identity: FirebaseIdentity = Depends(get_current_identity),
    db: Session = Depends(get_db),
) -> User:
    """Usuario registrado dueño del token: 401 sin token válido, 403 si la cuenta no tiene perfil."""
    user = find_user(db, identity.uid)
    if user is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "La cuenta no está registrada.")
    return user
