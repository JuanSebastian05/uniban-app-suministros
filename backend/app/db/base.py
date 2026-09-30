"""Clase base declarativa de SQLAlchemy.

Todos los modelos ORM (en ``app/models``) deben heredar de ``Base``.
"""

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass
