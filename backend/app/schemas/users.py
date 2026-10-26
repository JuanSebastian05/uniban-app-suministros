"""Contrato HTTP del registro de usuarios (HU_01, HU_13). JSON en camelCase, como la app."""

import uuid

from pydantic import Field, field_validator

from app.models.user import document_key
from app.schemas.movements import CamelModel


class RegisterIn(CamelModel):
    # El correo no viaja en el cuerpo: se toma del token de Firebase ya verificado.
    name: str = Field(min_length=1, max_length=120)
    document: str = Field(min_length=1, max_length=30)
    phone: str | None = Field(default=None, max_length=30)

    @field_validator("document")
    @classmethod
    def _document_has_content(cls, value: str) -> str:
        # Sin letras ni números («---», «...») no hay nada con qué comparar documentos repetidos.
        if not document_key(value):
            raise ValueError("El documento debe tener letras o números.")
        return value


class UserOut(CamelModel):
    id: uuid.UUID
    email: str
    name: str
    document: str
    phone: str | None
    role: str
    producer_id: uuid.UUID
