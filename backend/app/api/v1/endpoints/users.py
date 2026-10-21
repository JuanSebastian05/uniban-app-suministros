"""Registro y perfil del usuario autenticado (HU_01).

La cuenta se crea primero en Firebase desde la app; luego la app llama a ``/users/register``
con su token para crear el perfil y su productor en PostgreSQL.
"""

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.core.security import FirebaseIdentity, get_current_identity
from app.db.session import get_db
from app.models import User
from app.schemas.users import RegisterIn, UserOut
from app.services.user_registration import find_user, register_user

router = APIRouter()


def _out(user: User) -> UserOut:
    return UserOut(
        id=user.id, email=user.email, name=user.name, document=user.document,
        phone=user.phone, role=user.role, producer_id=user.producer_id,
    )


@router.post(
    "/register",
    response_model=UserOut,
    status_code=status.HTTP_201_CREATED,
    summary="Registrar el perfil del usuario autenticado (idempotente por cuenta de Firebase)",
    responses={
        200: {"description": "La cuenta ya estaba registrada; se devuelve su perfil."},
        401: {"description": "Falta el token de Firebase o no es válido."},
        422: {"description": "Datos inválidos o token sin correo."},
    },
)
def register(
    payload: RegisterIn,
    response: Response,
    identity: FirebaseIdentity = Depends(get_current_identity),
    db: Session = Depends(get_db),
) -> UserOut:
    if not identity.email:
        raise HTTPException(422, "La cuenta de Firebase no tiene correo.")
    user, created = register_user(db, identity, payload)
    if not created:
        response.status_code = status.HTTP_200_OK
    return _out(user)


@router.get(
    "/me",
    response_model=UserOut,
    summary="Perfil del usuario autenticado",
    responses={404: {"description": "La cuenta de Firebase todavía no está registrada."}},
)
def me(identity: FirebaseIdentity = Depends(get_current_identity), db: Session = Depends(get_db)) -> UserOut:
    user = find_user(db, identity.uid)
    if user is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Usuario no registrado.")
    return _out(user)
