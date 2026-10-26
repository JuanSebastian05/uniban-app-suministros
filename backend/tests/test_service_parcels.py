"""Rutas de servicio para el Equipo 1: clave ``X-API-Key`` y ``GET /api/v1/service/parcels``.

SQLite en memoria. Las claves son valores falsos de prueba: nunca se usa la clave real.
"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.config import Settings, get_settings
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app

URL = "/api/v1/service/parcels"
PARCELS = "/api/v1/parcels"
REGISTER = "/api/v1/users/register"
KEY = "clave-falsa-de-prueba"
ANA = FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")
LUIS = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")


def new_uuid() -> str:
    return str(uuid.uuid4())


def settings_with(key: str | None):
    # _env_file=None: la prueba no debe leer el .env real de quien la corre.
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
    app.dependency_overrides[get_settings] = settings_with(KEY)
    yield TestClient(app)
    app.dependency_overrides.clear()


def register_and_create(client, identity, who, document, parcels):
    identity["value"] = who
    assert client.post(REGISTER, json={"name": who.uid, "document": document}).status_code == 201
    for name, area in parcels:
        body = {"id": new_uuid(), "name": name, "areaHa": area,
                "cycle": {"id": new_uuid(), "startDate": "2026-01-15", "status": "PRODUCCION"}}
        assert client.post(PARCELS, json=body).status_code == 201


def test_sin_cabecera_responde_401(client):
    response = client.get(URL)
    assert response.status_code == 401


@pytest.mark.parametrize("sent", ["", "otra-clave", KEY.upper(), KEY + " ", "clave-ñandú"])
def test_clave_incorrecta_responde_401(client, sent):
    # Bytes latin-1: un HTTP real puede traer caracteres no ASCII y no debe romper la comparación (500).
    assert client.get(URL, headers={"X-API-Key": sent.encode("latin-1")}).status_code == 401


@pytest.mark.parametrize("configured", [None, "", "   "])
def test_sin_clave_configurada_responde_503_incluso_con_cabecera_vacia(client, configured):
    app.dependency_overrides[get_settings] = settings_with(configured)
    assert client.get(URL).status_code == 503
    assert client.get(URL, headers={"X-API-Key": ""}).status_code == 503
    assert client.get(URL, headers={"X-API-Key": "cualquiera"}).status_code == 503


def test_con_clave_correcta_sin_parcelas_devuelve_lista_vacia(client):
    response = client.get(URL, headers={"X-API-Key": KEY})
    assert response.status_code == 200
    assert response.json() == []


def test_no_acepta_el_token_de_firebase_en_su_lugar(client, identity):
    # La ruta es de servicio: un productor autenticado con Firebase pero sin clave no entra.
    register_and_create(client, identity, ANA, "100", [("La Esperanza", 2.4)])
    assert client.get(URL, headers={"Authorization": "Bearer token-de-firebase"}).status_code == 401


def test_devuelve_las_parcelas_de_todos_los_productores_solo_con_id_nombre_y_area(client, identity):
    register_and_create(client, identity, ANA, "100", [("La Esperanza", 2.4), ("El Mirador", 10)])
    register_and_create(client, identity, LUIS, "200", [("Buenavista", 0.5)])

    response = client.get(URL, headers={"X-API-Key": KEY})

    assert response.status_code == 200
    data = response.json()
    assert [p["name"] for p in data] == ["Buenavista", "El Mirador", "La Esperanza"]
    assert all(set(p) == {"id", "name", "areaHa"} for p in data)  # nada de productor, documento ni ciclo
    assert [float(p["areaHa"]) for p in data] == [0.5, 10.0, 2.4]
    assert [p["areaHa"] for p in data] == ["0.50", "10.00", "2.40"]  # dos decimales, como ParcelOut
    assert all(uuid.UUID(p["id"]) for p in data)


def test_el_formato_del_area_coincide_con_el_de_la_app(client, identity):
    register_and_create(client, identity, ANA, "100", [("La Esperanza", 2.4)])
    own = client.get(PARCELS).json()[0]
    service = client.get(URL, headers={"X-API-Key": KEY}).json()[0]
    assert service["id"] == own["id"]
    assert service["areaHa"] == own["areaHa"]


def test_la_ruta_aparece_documentada_en_openapi(client):
    paths = client.get("/openapi.json").json()["paths"]
    assert "/api/v1/service/parcels" in paths
    assert {"401", "503"} <= set(paths["/api/v1/service/parcels"]["get"]["responses"])
