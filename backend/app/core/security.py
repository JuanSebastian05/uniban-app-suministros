"""Verificación del token de Firebase (RT_05, RT_13).

La app envía ``Authorization: Bearer <ID token>``. Aquí solo se comprueba la identidad;
los permisos se deciden con el rol guardado en PostgreSQL.
"""

from dataclasses import dataclass
from functools import lru_cache

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import get_settings

bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class FirebaseIdentity:
    uid: str
    email: str | None


@lru_cache
def _firebase_app():
    # Import perezoso: el backend y sus pruebas funcionan sin firebase-admin configurado.
    import firebase_admin

    # Verificar tokens solo requiere el ID del proyecto (llaves públicas de Google), sin cuenta de servicio.
    return firebase_admin.initialize_app(options={"projectId": get_settings().firebase_project_id}, name="uniban-auth")


def get_current_identity(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> FirebaseIdentity:
    """Dependencia de FastAPI: 401 si falta el token o no es válido; 503 si Firebase no está configurado."""
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Falta el token de autenticación.")
    if not get_settings().firebase_project_id:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "FIREBASE_PROJECT_ID no está configurado.")
    from firebase_admin import auth

    try:
        claims = auth.verify_id_token(credentials.credentials, app=_firebase_app())
    except Exception as error:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token de autenticación inválido.") from error
    return FirebaseIdentity(uid=claims["uid"], email=claims.get("email"))
