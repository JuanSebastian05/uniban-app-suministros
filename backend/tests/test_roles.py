"""Autorización por rol (RT_13): solo el rol PRODUCTOR usa las rutas de datos; cualquier otro recibe 403.
SQLite en memoria; el token de Firebase se reemplaza por una identidad fija.
"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import User


def new_uuid() -> str:
    return str(uuid.uuid4())


@pytest.fixture()
def session_factory():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)

    @event.listens_for(engine, "connect")
    def _enable_foreign_keys(dbapi_connection, _record):
        dbapi_connection.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


@pytest.fixture()
def client(session_factory):
    def override_get_db():
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_current_identity] = lambda: FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")
    test_client = TestClient(app)
    assert test_client.post("/api/v1/users/register", json={"name": "Ana", "document": "1"}).status_code == 201
    yield test_client
    app.dependency_overrides.clear()


def set_role(session_factory, role: str) -> None:
    with session_factory() as db:
        user = db.scalar(select(User).where(User.firebase_uid == "uid-ana"))
        user.role = role
        db.commit()


DATA_ROUTES = [
    ("get", "/api/v1/parcels", None),
    ("post", "/api/v1/parcels", {"id": new_uuid(), "name": "La Esperanza", "areaHa": 2.4}),
    ("get", "/api/v1/inventory", None),
    ("post", "/api/v1/movements", {}),
    ("post", "/api/v1/activities", {}),
]


def call(client, method: str, path: str, body):
    return getattr(client, method)(path, json=body) if body is not None else getattr(client, method)(path)


@pytest.mark.parametrize("method,path,body", DATA_ROUTES)
def test_un_rol_distinto_de_productor_recibe_403(client, session_factory, method, path, body):
    set_role(session_factory, "ASESOR")
    response = call(client, method, path, body)
    assert response.status_code == 403
    assert "rol" in response.json()["detail"].lower()


@pytest.mark.parametrize("method,path,body", DATA_ROUTES[:3])
def test_el_productor_si_accede(client, method, path, body):
    assert call(client, method, path, body).status_code in (200, 201)


def test_el_perfil_sigue_disponible_con_otro_rol(client, session_factory):
    # /users/me y /users/register solo exigen identidad: así la app puede mostrar el error en vez de fallar.
    set_role(session_factory, "ASESOR")
    response = client.get("/api/v1/users/me")
    assert response.status_code == 200
    assert response.json()["role"] == "ASESOR"


def test_cuenta_sin_perfil_sigue_siendo_403_distinto_del_rol(client, session_factory):
    app.dependency_overrides[get_current_identity] = lambda: FirebaseIdentity(uid="uid-nueva", email="n@x.co")
    response = client.get("/api/v1/parcels")
    assert response.status_code == 403
    assert "registrada" in response.json()["detail"]
