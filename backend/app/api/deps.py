"""Dependencias compartidas por los endpoints."""

from fastapi import Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import FirebaseIdentity, get_current_identity
from app.db.session import get_db
from app.models import User
from app.models.user import ROLE_PRODUCER
from app.services.user_registration import find_user


def get_current_user(
    identity: FirebaseIdentity = Depends(get_current_identity),
    db: Session = Depends(get_db),
) -> User:
    """Usuario registrado dueño del token: 401 sin token válido, 403 si no tiene perfil o su rol no puede usar la API.

    Hoy solo existe el rol PRODUCTOR (RT_13): cualquier otro rol recibe 403 en las rutas de datos.
    """
    user = find_user(db, identity.uid)
    if user is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "La cuenta no está registrada.")
    if user.role != ROLE_PRODUCER:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "El rol de la cuenta no tiene acceso a esta función.")
    return user
