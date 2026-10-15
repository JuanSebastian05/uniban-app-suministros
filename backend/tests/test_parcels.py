"""Pruebas del registro de parcelas (HU_14) y del aislamiento por usuario.

SQLite en memoria (sin PostgreSQL ni Neon); la verificación del token de Firebase se reemplaza
por una identidad que cada prueba puede cambiar.
"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, func, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import Parcel, ProductionCycle

URL = "/api/v1/parcels"
REGISTER = "/api/v1/users/register"
ANA = FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")
LUIS = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")


def new_uuid() -> str:
    return str(uuid.uuid4())


def parcel(name: str = "La Esperanza", area=2.4, cycle: dict | None = None, parcel_id: str | None = None) -> dict:
    body = {"id": parcel_id or new_uuid(), "name": name, "areaHa": area}
    if cycle is not None:
        body["cycle"] = cycle
    return body


def cycle(status: str = "PRODUCCION", start: str = "2024-01-15", cycle_id: str | None = None) -> dict:
    return {"id": cycle_id or new_uuid(), "startDate": start, "status": status}


@pytest.fixture()
def session_factory():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)

    @event.listens_for(engine, "connect")
    def _enable_foreign_keys(dbapi_connection, _record):
        dbapi_connection.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


@pytest.fixture()
def identity():
    return {"value": ANA}


@pytest.fixture()
def client(session_factory, identity):
    def override_get_db():
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_current_identity] = lambda: identity["value"]
    test_client = TestClient(app)
    assert test_client.post(REGISTER, json={"name": "Ana", "document": "100"}).status_code == 201
    yield test_client
    app.dependency_overrides.clear()


def as_user(client, identity, user: FirebaseIdentity, document: str):
    identity["value"] = user
    assert client.post(REGISTER, json={"name": user.uid, "document": document}).status_code == 201


def count(session_factory, model) -> int:
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(model))


def test_registra_parcela_sin_ciclo(client, session_factory):
    body = parcel()
    response = client.post(URL, json=body)
    assert response.status_code == 201
    data = response.json()
    assert data["id"] == body["id"]
    assert data["name"] == "La Esperanza"
    assert float(data["areaHa"]) == 2.4
    assert data["activeCycle"] is None
    assert count(session_factory, Parcel) == 1
    assert count(session_factory, ProductionCycle) == 0


def test_registra_parcela_con_ciclo_activo(client):
    body = parcel(cycle=cycle(status="EN_PREPARACION", start="2024-03-01"))
    response = client.post(URL, json=body)
    assert response.status_code == 201
    active = response.json()["activeCycle"]
    assert active["id"] == body["cycle"]["id"]
    assert active["startDate"] == "2024-03-01"
    assert active["endDate"] is None
    assert active["status"] == "EN_PREPARACION"

    listed = client.get(URL).json()
    assert [p["name"] for p in listed] == ["La Esperanza"]
    assert listed[0]["activeCycle"]["status"] == "EN_PREPARACION"


def test_nombre_se_limpia_de_espacios(client):
    response = client.post(URL, json=parcel(name="  La   Palma  "))
    assert response.status_code == 201
    assert response.json()["name"] == "La Palma"


@pytest.mark.parametrize("other_name", ["La Esperanza", "la esperanza", "  LA  ESPERANZA "])
def test_nombre_repetido_del_mismo_productor_responde_409(client, session_factory, other_name):
    client.post(URL, json=parcel(name="La Esperanza"))
    response = client.post(URL, json=parcel(name=other_name))
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "DUPLICATE_NAME"
    assert count(session_factory, Parcel) == 1


def test_reenviar_la_misma_parcela_no_duplica(client, session_factory):
    body = parcel(cycle=cycle())
    first = client.post(URL, json=body)
    second = client.post(URL, json=body)
    assert second.status_code == 200
    assert second.json() == first.json()
    assert count(session_factory, Parcel) == 1
    assert count(session_factory, ProductionCycle) == 1


def test_mismo_uuid_con_otros_datos_responde_409(client):
    body = parcel()
    client.post(URL, json=body)
    response = client.post(URL, json={**body, "areaHa": 3})
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "ID_CONFLICT"


@pytest.mark.parametrize(
    "overrides",
    [
        {"name": ""},
        {"name": "   "},
        {"name": "x" * 121},
        {"areaHa": 0},
        {"areaHa": -1},
        {"areaHa": 2.345},
        {"areaHa": 100000},
        {"cycle": {"id": "c3b6a1a2-0000-4000-8000-000000000000", "startDate": "2024-01-15", "status": "COSECHA"}},
        {"cycle": {"id": "c3b6a1a2-0000-4000-8000-000000000000", "status": "PRODUCCION"}},
    ],
)
def test_datos_invalidos_responden_422(client, session_factory, overrides):
    assert client.post(URL, json={**parcel(), **overrides}).status_code == 422
    assert count(session_factory, Parcel) == 0


def test_otro_usuario_no_ve_mis_parcelas(client, identity):
    client.post(URL, json=parcel(name="La Esperanza"))
    as_user(client, identity, LUIS, "200")
    assert client.get(URL).json() == []


def test_otro_usuario_puede_usar_el_mismo_nombre(client, identity, session_factory):
    client.post(URL, json=parcel(name="La Esperanza"))
    as_user(client, identity, LUIS, "200")
    assert client.post(URL, json=parcel(name="La Esperanza")).status_code == 201
    assert count(session_factory, Parcel) == 2


def test_otro_usuario_no_puede_reusar_mi_uuid(client, identity):
    body = parcel()
    client.post(URL, json=body)
    as_user(client, identity, LUIS, "200")
    response = client.post(URL, json=body)
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "ID_CONFLICT"


def test_lista_ordenada_por_nombre(client):
    for name in ["El Progreso", "la Palma", "El Mirador"]:
        client.post(URL, json=parcel(name=name))
    assert [p["name"] for p in client.get(URL).json()] == ["El Mirador", "El Progreso", "la Palma"]


def test_cuenta_sin_perfil_responde_403(client, identity):
    identity["value"] = FirebaseIdentity(uid="uid-sin-perfil", email="x@unibanagro.com")
    assert client.post(URL, json=parcel()).status_code == 403
    assert client.get(URL).status_code == 403


def test_sin_token_responde_401(session_factory):
    app.dependency_overrides[get_db] = lambda: session_factory()
    try:
        response = TestClient(app).post(URL, json=parcel())
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 401


def test_un_solo_ciclo_activo_por_parcela(session_factory, client):
    """La base de datos rechaza un segundo ciclo activo aunque se inserte por fuera de la API."""
    body = parcel(cycle=cycle())
    client.post(URL, json=body)
    from datetime import date

    from sqlalchemy.exc import IntegrityError

    with session_factory() as db:
        db.add(ProductionCycle(id=uuid.uuid4(), parcel_id=uuid.UUID(body["id"]),
                               start_date=date(2024, 6, 1), status="DESCANSO"))
        with pytest.raises(IntegrityError):
            db.commit()
