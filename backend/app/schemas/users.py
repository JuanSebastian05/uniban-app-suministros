"""Contrato HTTP del registro de usuarios (HU_01). JSON en camelCase, como la app."""

import uuid

from pydantic import Field

from app.schemas.movements import CamelModel


class RegisterIn(CamelModel):
    # El correo no viaja en el cuerpo: se toma del token de Firebase ya verificado.
    name: str = Field(min_length=1, max_length=120)
    document: str = Field(min_length=1, max_length=30)
    phone: str | None = Field(default=None, max_length=30)


class UserOut(CamelModel):
    id: uuid.UUID
    email: str
    name: str
    document: str
    phone: str | None
    role: str
    producer_id: uuid.UUID
