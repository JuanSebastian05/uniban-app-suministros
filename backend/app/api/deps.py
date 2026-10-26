"""Dependencias compartidas por los endpoints."""

import secrets

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
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


def require_service_key(
    x_api_key: str | None = Header(default=None, description="Clave de servicio entregada al Equipo 1."),
    settings: Settings = Depends(get_settings),
) -> None:
    """Autentica a otro equipo por clave de servicio (cabecera ``X-API-Key``), no por Firebase.

    503 si ``SERVICE_API_KEY`` no está configurada (nunca se acepta una clave vacía); 401 si falta o no coincide.
    """
    expected = settings.service_api_key
    if not expected or not expected.strip():
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "La clave de servicio no está configurada.")
    # Se comparan bytes: compare_digest falla con texto no ASCII y el tiempo no debe revelar la coincidencia.
    if x_api_key is None or not secrets.compare_digest(x_api_key.encode(), expected.encode()):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Clave de servicio inválida o ausente.")
