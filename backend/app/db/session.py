"""Sesión de base de datos (PostgreSQL vía SQLAlchemy 2.x).

El engine se crea de forma perezosa: el backend puede iniciar (y /health responder)
aunque aún no haya una base PostgreSQL configurada. La conexión real solo se abre
cuando un endpoint use la dependencia ``get_db``.
"""

from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import get_settings


@lru_cache
def get_engine() -> Engine:
    settings = get_settings()
    if not settings.database_url:
        raise RuntimeError("DATABASE_URL no está configurada. Ver backend/.env.example.")
    return create_engine(settings.database_url, pool_pre_ping=True)


@lru_cache
def get_session_factory() -> sessionmaker[Session]:
    return sessionmaker(bind=get_engine(), autoflush=False, expire_on_commit=False)


def get_db() -> Generator[Session, None, None]:
    """Dependencia de FastAPI: ``db: Session = Depends(get_db)``."""
    db = get_session_factory()()
    try:
        yield db
    finally:
        db.close()
