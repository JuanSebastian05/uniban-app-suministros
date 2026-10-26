"""Aforamientos del Equipo 1: recepción idempotente con clave de servicio y validaciones.

SQLite en memoria. La clave es un valor falso de prueba.
"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, func, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.config import Settings, get_settings
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import Aforamiento

URL = "/api/v1/aforamientos"
PARCELS = "/api/v1/parcels"
REGISTER = "/api/v1/users/register"
KEY = "clave-falsa-de-prueba"
HEADERS = {"X-API-Key": KEY}


def new_uuid() -> str:
    return str(uuid.uuid4())


def settings_with(key: str | None):
    return lambda: Settings(_env_file=None, service_api_key=key)


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
    app.dependency_overrides[get_settings] = settings_with(KEY)
    test_client = TestClient(app)
    assert test_client.post(REGISTER, json={"name": "Ana", "document": "100"}).status_code == 201
    yield test_client
    app.dependency_overrides.clear()


@pytest.fixture()
def parcel_id(client):
    body = {"id": new_uuid(), "name": "La Esperanza", "areaHa": 2.4}
    assert client.post(PARCELS, json=body).status_code == 201
    return body["id"]


def aforamiento(parcel_id: str, **extra) -> dict:
    body = {
        "id": new_uuid(), "parcelId": parcel_id, "date": "2026-10-29",
        "areaHa": 2.4, "estimatedPlants": 1850, "confidence": 0.87,
    }
    body.update(extra)
    return body


def count(session_factory) -> int:
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(Aforamiento))


# --- Clave de servicio ---

def test_sin_clave_responde_401_y_no_guarda(client, session_factory, parcel_id):
    assert client.post(URL, json=aforamiento(parcel_id)).status_code == 401
    assert client.post(URL, json=aforamiento(parcel_id), headers={"X-API-Key": "otra"}).status_code == 401
    assert count(session_factory) == 0


def test_sin_clave_configurada_responde_503(client, session_factory, parcel_id):
    app.dependency_overrides[get_settings] = settings_with(None)
    assert client.post(URL, json=aforamiento(parcel_id), headers=HEADERS).status_code == 503
    assert client.post(URL, json=aforamiento(parcel_id), headers={"X-API-Key": ""}).status_code == 503
    assert count(session_factory) == 0


def test_el_token_de_firebase_no_sustituye_la_clave(client, parcel_id):
    # La identidad de Firebase está activa en este cliente y aun así la ruta exige la clave.
    assert client.post(URL, json=aforamiento(parcel_id)).status_code == 401


# --- Creación e idempotencia ---

def test_crea_el_aforamiento(client, session_factory, parcel_id):
    body = aforamiento(parcel_id)
    response = client.post(URL, json=body, headers=HEADERS)
    assert response.status_code == 201
    assert response.json() == {"id": body["id"], "status": "CREATED"}
    with session_factory() as db:
        saved = db.get(Aforamiento, uuid.UUID(body["id"]))
        assert str(saved.parcel_id) == parcel_id
        assert saved.estimated_plants == 1850
        assert float(saved.area_ha) == 2.4
        assert float(saved.confidence) == 0.87
        assert saved.received_at is not None


def test_la_confianza_es_opcional(client, session_factory, parcel_id):
    body = aforamiento(parcel_id)
    del body["confidence"]
    assert client.post(URL, json=body, headers=HEADERS).status_code == 201
    body2 = aforamiento(parcel_id, confidence=None)
    assert client.post(URL, json=body2, headers=HEADERS).status_code == 201
    with session_factory() as db:
        assert db.get(Aforamiento, uuid.UUID(body["id"])).confidence is None


def test_reenviar_lo_mismo_responde_200_sin_duplicar(client, session_factory, parcel_id):
    body = aforamiento(parcel_id)
    assert client.post(URL, json=body, headers=HEADERS).status_code == 201
    again = client.post(URL, json=body, headers=HEADERS)
    assert again.status_code == 200
    assert again.json() == {"id": body["id"], "status": "DUPLICATE"}
    assert count(session_factory) == 1


def test_reenviar_con_otro_contenido_responde_409(client, session_factory, parcel_id):
    body = aforamiento(parcel_id)
    assert client.post(URL, json=body, headers=HEADERS).status_code == 201
    for change in ({"estimatedPlants": 1900}, {"areaHa": 3.1}, {"date": "2026-10-30"}, {"confidence": 0.5},
                   {"confidence": None}):
        response = client.post(URL, json={**body, **change}, headers=HEADERS)
        assert response.status_code == 409, change
    other = client.post(PARCELS, json={"id": new_uuid(), "name": "El Mirador", "areaHa": 1}).json()["id"]
    assert client.post(URL, json={**body, "parcelId": other}, headers=HEADERS).status_code == 409
    assert count(session_factory) == 1


def test_el_redondeo_hace_idempotente_un_reenvio_con_mas_decimales(client, session_factory, parcel_id):
    body = aforamiento(parcel_id, areaHa=2.404, confidence=0.8734)
    assert client.post(URL, json=body, headers=HEADERS).status_code == 201
    assert client.post(URL, json=body, headers=HEADERS).status_code == 200
    with session_factory() as db:
        saved = db.get(Aforamiento, uuid.UUID(body["id"]))
        assert float(saved.area_ha) == 2.4
        assert float(saved.confidence) == 0.873


def test_varios_aforamientos_de_la_misma_parcela_conviven(client, session_factory, parcel_id):
    for date in ("2026-10-01", "2026-10-15", "2026-10-29"):
        assert client.post(URL, json=aforamiento(parcel_id, date=date), headers=HEADERS).status_code == 201
    assert count(session_factory) == 3


# --- Parcela ---

def test_parcela_inexistente_responde_404(client, session_factory):
    response = client.post(URL, json=aforamiento(new_uuid()), headers=HEADERS)
    assert response.status_code == 404
    assert "parcela" in response.json()["detail"].lower()
    assert count(session_factory) == 0


def test_acepta_una_parcela_de_cualquier_productor(client, session_factory, parcel_id):
    # El Equipo 1 no es un productor: no hay «dueño» que validar, solo que la parcela exista.
    assert client.post(URL, json=aforamiento(parcel_id), headers=HEADERS).status_code == 201


# --- Validaciones ---

@pytest.mark.parametrize("change", [
    {"date": "29/10/2026"},
    {"date": "2026-13-01"},
    {"date": ""},
    {"areaHa": 0},
    {"areaHa": -1},
    {"areaHa": 0.004},  # se redondea a 0,00
    {"areaHa": 100000},
    {"areaHa": "abc"},
    {"estimatedPlants": -1},
    {"estimatedPlants": 18.5},
    {"estimatedPlants": 2147483648},
    {"estimatedPlants": "muchas"},
    {"confidence": -0.01},
    {"confidence": 1.01},
    {"confidence": "alta"},
    {"id": "no-es-uuid"},
    {"parcelId": "no-es-uuid"},
])
def test_datos_invalidos_responden_422(client, session_factory, parcel_id, change):
    response = client.post(URL, json=aforamiento(parcel_id, **change), headers=HEADERS)
    assert response.status_code == 422, change
    assert count(session_factory) == 0


@pytest.mark.parametrize("missing", ["id", "parcelId", "date", "areaHa", "estimatedPlants"])
def test_campos_obligatorios(client, session_factory, parcel_id, missing):
    body = aforamiento(parcel_id)
    del body[missing]
    assert client.post(URL, json=body, headers=HEADERS).status_code == 422
    assert count(session_factory) == 0


@pytest.mark.parametrize("plants,confidence", [(0, 0), (0, 1), (1850, 0.5), (2147483647, 0.001)])
def test_valores_limite_validos(client, parcel_id, plants, confidence):
    body = aforamiento(parcel_id, estimatedPlants=plants, confidence=confidence)
    assert client.post(URL, json=body, headers=HEADERS).status_code == 201


def test_aparece_documentado_en_openapi(client):
    paths = client.get("/openapi.json").json()["paths"]
    assert "/api/v1/aforamientos" in paths
    assert {"201", "200", "401", "404", "409", "422", "503"} <= set(paths["/api/v1/aforamientos"]["post"]["responses"])
