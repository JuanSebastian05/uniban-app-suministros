"""HU_09: la app consulta el aforamiento más reciente de sus parcelas (`GET /parcels` y `GET /parcels/{id}/aforamiento`).

SQLite en memoria; el token de Firebase es una identidad que cada prueba puede cambiar.
"""

import uuid
from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import Aforamiento

PARCELS = "/api/v1/parcels"
REGISTER = "/api/v1/users/register"
ANA = FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")
LUIS = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")


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


def create_parcel(client, name="La Esperanza") -> str:
    body = {"id": new_uuid(), "name": name, "areaHa": 2.4}
    assert client.post(PARCELS, json=body).status_code == 201
    return body["id"]


def add(session_factory, parcel_id, date, plants=1000, confidence=0.8, area=2.4, received=None, row_id=None):
    with session_factory() as db:
        db.add(Aforamiento(
            id=uuid.UUID(row_id) if row_id else uuid.uuid4(), parcel_id=uuid.UUID(parcel_id),
            date=datetime.strptime(date, "%Y-%m-%d").date(), area_ha=area, estimated_plants=plants,
            confidence=confidence,
            **({"received_at": received} if received else {}),
        ))
        db.commit()


# --- GET /parcels/{id}/aforamiento ---

def test_devuelve_el_mas_reciente_por_fecha(client, session_factory):
    parcel_id = create_parcel(client)
    add(session_factory, parcel_id, "2026-09-01", plants=900)
    add(session_factory, parcel_id, "2026-10-29", plants=1850, confidence=0.87, area=2.41)
    add(session_factory, parcel_id, "2026-10-01", plants=1200)

    response = client.get(f"{PARCELS}/{parcel_id}/aforamiento")

    assert response.status_code == 200
    data = response.json()
    assert data["date"] == "2026-10-29"
    assert data["estimatedPlants"] == 1850
    assert data["areaHa"] == "2.41"
    assert data["confidence"] == "0.870"
    assert data["parcelId"] == parcel_id
    assert set(data) == {"id", "parcelId", "date", "areaHa", "estimatedPlants", "confidence"}


def test_si_empatan_en_fecha_gana_el_recibido_despues(client, session_factory):
    parcel_id = create_parcel(client)
    early = datetime(2026, 10, 29, 8, 0, tzinfo=timezone.utc)
    late = datetime(2026, 10, 29, 9, 0, tzinfo=timezone.utc)
    add(session_factory, parcel_id, "2026-10-29", plants=100, received=late)
    add(session_factory, parcel_id, "2026-10-29", plants=200, received=early)
    assert client.get(f"{PARCELS}/{parcel_id}/aforamiento").json()["estimatedPlants"] == 100


def test_la_confianza_ausente_sale_nula(client, session_factory):
    parcel_id = create_parcel(client)
    add(session_factory, parcel_id, "2026-10-29", confidence=None)
    data = client.get(f"{PARCELS}/{parcel_id}/aforamiento").json()
    assert data["confidence"] is None


def test_sin_aforamientos_responde_404(client):
    parcel_id = create_parcel(client)
    response = client.get(f"{PARCELS}/{parcel_id}/aforamiento")
    assert response.status_code == 404
    assert "aforamientos" in response.json()["detail"]


def test_parcela_de_otro_productor_responde_404_sin_filtrar_datos(client, session_factory, identity):
    parcel_id = create_parcel(client)
    add(session_factory, parcel_id, "2026-10-29", plants=1850)
    identity["value"] = LUIS
    assert client.post(REGISTER, json={"name": "Luis", "document": "200"}).status_code == 201

    response = client.get(f"{PARCELS}/{parcel_id}/aforamiento")

    assert response.status_code == 404
    assert "1850" not in response.text
    # Mismo mensaje que una parcela inexistente: no se revela que existe.
    assert response.json() == client.get(f"{PARCELS}/{new_uuid()}/aforamiento").json()


def test_parcela_inexistente_responde_404(client):
    assert client.get(f"{PARCELS}/{new_uuid()}/aforamiento").status_code == 404


def test_id_que_no_es_uuid_responde_422(client):
    assert client.get(f"{PARCELS}/no-es-uuid/aforamiento").status_code == 422


def test_sin_token_responde_401():
    app.dependency_overrides.clear()
    assert TestClient(app).get(f"{PARCELS}/{new_uuid()}/aforamiento").status_code == 401


def test_cuenta_sin_perfil_responde_403(client, identity):
    identity["value"] = FirebaseIdentity(uid="uid-sin-perfil", email="x@unibanagro.com")
    assert client.get(f"{PARCELS}/{new_uuid()}/aforamiento").status_code == 403


# --- GET /parcels: aforamiento embebido ---

def test_la_lista_incluye_el_aforamiento_mas_reciente_o_nulo(client, session_factory):
    with_data = create_parcel(client, "Con aforamiento")
    without = create_parcel(client, "Sin aforamiento")
    add(session_factory, with_data, "2026-09-01", plants=900)
    add(session_factory, with_data, "2026-10-29", plants=1850)

    rows = {p["name"]: p for p in client.get(PARCELS).json()}

    assert rows["Con aforamiento"]["aforamiento"]["estimatedPlants"] == 1850
    assert rows["Con aforamiento"]["aforamiento"]["date"] == "2026-10-29"
    assert rows["Sin aforamiento"]["aforamiento"] is None
    assert rows["Sin aforamiento"]["id"] == without


def test_la_lista_no_mezcla_aforamientos_entre_parcelas_ni_productores(client, session_factory, identity):
    mine = create_parcel(client, "Mía")
    add(session_factory, mine, "2026-10-29", plants=111)
    identity["value"] = LUIS
    assert client.post(REGISTER, json={"name": "Luis", "document": "200"}).status_code == 201
    theirs = create_parcel(client, "Suya")
    add(session_factory, theirs, "2026-10-01", plants=222)

    luis_rows = client.get(PARCELS).json()
    assert [(p["name"], p["aforamiento"]["estimatedPlants"]) for p in luis_rows] == [("Suya", 222)]
    identity["value"] = ANA
    ana_rows = client.get(PARCELS).json()
    assert [(p["name"], p["aforamiento"]["estimatedPlants"]) for p in ana_rows] == [("Mía", 111)]


def test_la_lista_sin_parcelas_sigue_vacia(client):
    assert client.get(PARCELS).json() == []


def test_el_campo_es_aditivo_y_la_respuesta_de_crear_lo_trae_nulo(client):
    body = {"id": new_uuid(), "name": "Nueva", "areaHa": 1}
    created = client.post(PARCELS, json=body).json()
    assert created["aforamiento"] is None
    assert {"id", "name", "areaHa", "activeCycle"} <= set(created)
