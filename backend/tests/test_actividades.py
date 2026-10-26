"""Actividades productivas (HU_08): recepción idempotente, aislamiento entre usuarios y consumos ligados
a una actividad. SQLite en memoria; el token de Firebase es una identidad que cada prueba puede cambiar.
"""

import copy
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
from app.models import Activity, Inventory, Movement

URL = "/api/v1/activities"
MOVEMENTS = "/api/v1/movements"
PARCELS = "/api/v1/parcels"


def new_uuid() -> str:
    return str(uuid.uuid4())


LOCATION = {"id": new_uuid(), "name": "Inventario principal"}
SUPPLY = {"id": new_uuid(), "name": "Urea 46%", "category": "Fertilizante", "unit": "kg"}
INVENTORY = {"id": new_uuid(), "initialQuantity": 10, "location": LOCATION, "supply": SUPPLY}


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
    return {"value": FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")}


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
    assert test_client.post("/api/v1/users/register", json={"name": "Ana", "document": "1"}).status_code == 201
    yield test_client
    app.dependency_overrides.clear()


@pytest.fixture()
def parcel(client):
    """Parcela de Ana con un ciclo activo."""
    body = {"id": new_uuid(), "name": "La Esperanza", "areaHa": 2.4,
            "cycle": {"id": new_uuid(), "startDate": "2026-01-15", "status": "PRODUCCION"}}
    assert client.post(PARCELS, json=body).status_code == 201
    return body


def activity(parcel: dict, **extra) -> dict:
    body = {
        "id": new_uuid(), "parcelId": parcel["id"], "cycleId": parcel["cycle"]["id"], "type": "FERTILIZACION",
        "date": "2026-10-24", "observation": "Primera aplicación",
        "createdAt": "2026-10-24T10:00:00.000Z", "updatedAt": "2026-10-24T10:00:00.000Z",
    }
    body.update(extra)
    return body


def consumption(parcel: dict, quantity: float, **extra) -> dict:
    body = {
        "id": new_uuid(), "type": "CONSUMO", "quantity": quantity, "date": "2026-10-24", "observation": "",
        "createdAt": "2026-10-24T10:00:00.000Z", "updatedAt": "2026-10-24T10:00:00.000Z",
        "inventory": copy.deepcopy(INVENTORY), "parcelId": parcel["id"], "cycleId": parcel["cycle"]["id"],
    }
    body.update(extra)
    return body


def count(session_factory, model) -> int:
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(model))


# --- Actividades ---

def test_crea_la_actividad(client, session_factory, parcel):
    body = activity(parcel)
    response = client.post(URL, json=body)
    assert response.status_code == 201
    assert response.json() == {"id": body["id"], "status": "CREATED"}
    with session_factory() as db:
        saved = db.get(Activity, uuid.UUID(body["id"]))
        assert str(saved.parcel_id) == parcel["id"]
        assert str(saved.cycle_id) == parcel["cycle"]["id"]
        assert saved.type == "FERTILIZACION"
        assert saved.observation == "Primera aplicación"


@pytest.mark.parametrize(
    "type_", ["FERTILIZACION", "SIEMBRA", "COSECHA", "APLICACION_INSUMO", "CONTROL_FITOSANITARIO", "OTRA"],
)
def test_acepta_los_seis_tipos(client, parcel, type_):
    assert client.post(URL, json=activity(parcel, type=type_)).status_code == 201


def test_observacion_es_opcional(client, parcel):
    assert client.post(URL, json=activity(parcel, observation="")).status_code == 201


def test_reenviar_la_misma_actividad_es_idempotente(client, session_factory, parcel):
    body = activity(parcel)
    assert client.post(URL, json=body).status_code == 201
    again = client.post(URL, json=body)
    assert again.status_code == 200
    assert again.json()["status"] == "DUPLICATE"
    assert count(session_factory, Activity) == 1


def test_mismo_id_con_otro_contenido_es_conflicto(client, session_factory, parcel):
    body = activity(parcel)
    assert client.post(URL, json=body).status_code == 201
    assert client.post(URL, json={**body, "type": "COSECHA"}).status_code == 409
    assert count(session_factory, Activity) == 1


@pytest.mark.parametrize("extra", [
    {"type": "RIEGO"},
    {"observation": "x" * 501},
    {"cycleId": None},
    {"date": "no es fecha"},
])
def test_datos_invalidos_son_422(client, parcel, extra):
    assert client.post(URL, json=activity(parcel, **extra)).status_code == 422


def test_exige_ciclo(client, parcel):
    body = activity(parcel)
    del body["cycleId"]
    assert client.post(URL, json=body).status_code == 422


def test_parcela_inexistente_es_conflicto(client):
    ghost = {"id": new_uuid(), "cycle": {"id": new_uuid()}}
    assert client.post(URL, json=activity(ghost)).status_code == 409


def test_ciclo_de_otra_parcela_es_conflicto(client, parcel):
    other = {"id": new_uuid(), "name": "El Mirador", "areaHa": 1.5,
             "cycle": {"id": new_uuid(), "startDate": "2026-02-01", "status": "PRODUCCION"}}
    assert client.post(PARCELS, json=other).status_code == 201
    assert client.post(URL, json=activity(parcel, cycleId=other["cycle"]["id"])).status_code == 409


def test_no_se_registra_sobre_la_parcela_de_otro_usuario(client, identity, session_factory, parcel):
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")
    assert client.post("/api/v1/users/register", json={"name": "Luis", "document": "2"}).status_code == 201
    assert client.post(URL, json=activity(parcel)).status_code == 409
    assert count(session_factory, Activity) == 0


def test_otro_usuario_no_puede_reenviar_una_actividad_ajena(client, identity, parcel):
    body = activity(parcel)
    assert client.post(URL, json=body).status_code == 201
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")
    assert client.post("/api/v1/users/register", json={"name": "Luis", "document": "2"}).status_code == 201
    assert client.post(URL, json=body).status_code == 409


def test_exige_token_y_perfil(client, identity):
    app.dependency_overrides[get_current_identity] = lambda: FirebaseIdentity(uid="uid-sin-perfil", email="x@x.co")
    parcel_stub = {"id": new_uuid(), "cycle": {"id": new_uuid()}}
    assert client.post(URL, json=activity(parcel_stub)).status_code == 403


# --- Consumos de una actividad ---

def test_consumo_ligado_a_la_actividad(client, session_factory, parcel):
    act = activity(parcel)
    assert client.post(URL, json=act).status_code == 201
    body = consumption(parcel, 4, activityId=act["id"])
    assert client.post(MOVEMENTS, json=body).status_code == 201
    with session_factory() as db:
        assert str(db.get(Movement, uuid.UUID(body["id"])).activity_id) == act["id"]
        assert float(db.get(Inventory, uuid.UUID(INVENTORY["id"])).quantity) == 6.0


def test_consumo_sin_actividad_sigue_valido(client, parcel):
    assert client.post(MOVEMENTS, json=consumption(parcel, 1)).status_code == 201


def test_consumo_con_actividad_inexistente_es_conflicto(client, session_factory, parcel):
    response = client.post(MOVEMENTS, json=consumption(parcel, 1, activityId=new_uuid()))
    assert response.status_code == 409
    assert count(session_factory, Movement) == 0


def test_consumo_de_otra_parcela_que_la_actividad_es_conflicto(client, parcel):
    act = activity(parcel)
    assert client.post(URL, json=act).status_code == 201
    other = {"id": new_uuid(), "name": "El Mirador", "areaHa": 1.5,
             "cycle": {"id": new_uuid(), "startDate": "2026-02-01", "status": "PRODUCCION"}}
    assert client.post(PARCELS, json=other).status_code == 201
    assert client.post(MOVEMENTS, json=consumption(other, 1, activityId=act["id"])).status_code == 409


def test_consumo_con_otro_ciclo_que_la_actividad_es_conflicto(client, parcel):
    act = activity(parcel)
    assert client.post(URL, json=act).status_code == 201
    assert client.post(MOVEMENTS, json=consumption(parcel, 1, activityId=act["id"], cycleId=None)).status_code == 409


def test_solo_los_consumos_pertenecen_a_una_actividad(client, parcel):
    act = activity(parcel)
    assert client.post(URL, json=act).status_code == 201
    entrada = consumption(parcel, 1, activityId=act["id"], type="ENTRADA")
    entrada.pop("parcelId")
    entrada.pop("cycleId")
    assert client.post(MOVEMENTS, json=entrada).status_code == 422


def test_consumo_de_una_actividad_ajena_es_conflicto(client, identity, parcel):
    act = activity(parcel)
    assert client.post(URL, json=act).status_code == 201
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")
    assert client.post("/api/v1/users/register", json={"name": "Luis", "document": "2"}).status_code == 201
    luis_parcel = {"id": parcel["id"], "cycle": parcel["cycle"]}
    assert client.post(MOVEMENTS, json=consumption(luis_parcel, 1, activityId=act["id"])).status_code == 409


def test_reenviar_el_consumo_con_su_actividad_no_resta_dos_veces(client, session_factory, parcel):
    act = activity(parcel)
    assert client.post(URL, json=act).status_code == 201
    body = consumption(parcel, 3, activityId=act["id"])
    assert client.post(MOVEMENTS, json=body).status_code == 201
    assert client.post(MOVEMENTS, json=body).status_code == 200
    with session_factory() as db:
        assert float(db.get(Inventory, uuid.UUID(INVENTORY["id"])).quantity) == 7.0
