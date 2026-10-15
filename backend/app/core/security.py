"""Verificación del token de Firebase (RT_05, RT_13).

La app envía ``Authorization: Bearer <ID token>``. Aquí solo se comprueba la identidad;
los permisos se deciden con el rol guardado en PostgreSQL.

El token se valida con las llaves públicas de Google (``google-auth``): basta con el ID del
proyecto, sin cuenta de servicio ni credenciales de Google Cloud.
"""

import logging
from dataclasses import dataclass
from functools import lru_cache

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import get_settings

logger = logging.getLogger(__name__)

bearer = HTTPBearer(auto_error=False)

# Tolerancia para relojes desfasados (p. ej. un emulador recién despertado).
CLOCK_SKEW_SECONDS = 30


@dataclass(frozen=True)
class FirebaseIdentity:
    uid: str
    email: str | None


@lru_cache
def _google_request():
    from google.auth.transport import requests as google_requests

    # Reutiliza la sesión HTTP; google-auth guarda en caché las llaves públicas.
    return google_requests.Request()


def verify_firebase_token(token: str, project_id: str) -> dict:
    """Valida firma, vencimiento, audiencia y emisor; lanza ``ValueError`` si algo no cuadra."""
    from google.oauth2 import id_token

    claims = id_token.verify_firebase_token(
        token, _google_request(), audience=project_id, clock_skew_in_seconds=CLOCK_SKEW_SECONDS
    )
    if claims.get("iss") != f"https://securetoken.google.com/{project_id}":
        raise ValueError("Emisor del token inesperado.")
    if not claims.get("sub"):
        raise ValueError("El token no tiene uid.")
    return claims


def get_current_identity(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> FirebaseIdentity:
    """Dependencia de FastAPI: 401 si falta el token o no es válido; 503 si Firebase no está configurado."""
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Falta el token de autenticación.")
    project_id = get_settings().firebase_project_id
    if not project_id:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "FIREBASE_PROJECT_ID no está configurado.")
    try:
        claims = verify_firebase_token(credentials.credentials, project_id)
    except Exception as error:
        # El motivo queda en el log del servidor (p. ej. audiencia distinta = FIREBASE_PROJECT_ID equivocado).
        logger.warning("Token de Firebase rechazado: %s", error)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token de autenticación inválido.") from error
    return FirebaseIdentity(uid=claims["sub"], email=claims.get("email"))
