"""Consumos y pérdidas (HU_05): restan existencia, nunca la dejan negativa y guardan parcela,
ciclo o motivo. SQLite en memoria; el token de Firebase se reemplaza por una identidad fija.
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
from app.models import Inventory, Movement

URL = "/api/v1/movements"
PARCELS = "/api/v1/parcels"


def new_uuid() -> str:
    return str(uuid.uuid4())


LOCATION = {"id": new_uuid(), "name": "Inventario principal"}
SUPPLY = {"id": new_uuid(), "name": "Urea 46%", "category": "Fertilizante", "unit": "kg"}
INVENTORY = {"id": new_uuid(), "initialQuantity": 10, "location": LOCATION, "supply": SUPPLY}


def movement(type_: str, quantity: float, **extra) -> dict:
    body = {
        "id": new_uuid(), "type": type_, "quantity": quantity, "date": "2026-10-15", "observation": "",
        "createdAt": "2026-10-15T10:00:00.000Z", "updatedAt": "2026-10-15T10:00:00.000Z",
        "inventory": copy.deepcopy(INVENTORY),
    }
    body.update(extra)
    return body


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


def stock(session_factory) -> float:
    with session_factory() as db:
        return float(db.get(Inventory, uuid.UUID(INVENTORY["id"])).quantity)


def count(session_factory) -> int:
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(Movement))


def test_consumo_resta_y_guarda_parcela_y_ciclo(client, session_factory, parcel):
    body = movement("CONSUMO", 4, parcelId=parcel["id"], cycleId=parcel["cycle"]["id"])
    assert client.post(URL, json=body).status_code == 201
    assert stock(session_factory) == 6.0
    with session_factory() as db:
        saved = db.get(Movement, uuid.UUID(body["id"]))
        assert str(saved.parcel_id) == parcel["id"]
        assert str(saved.cycle_id) == parcel["cycle"]["id"]
        assert saved.loss_reason is None


def test_consumo_sin_ciclo_se_acepta(client, session_factory, parcel):
    assert client.post(URL, json=movement("CONSUMO", 1, parcelId=parcel["id"])).status_code == 201
    assert stock(session_factory) == 9.0


def test_perdida_resta_y_guarda_el_motivo(client, session_factory):
    body = movement("PERDIDA", 2.5, lossReason="CLIMA")
    assert client.post(URL, json=body).status_code == 201
    assert stock(session_factory) == 7.5
    with session_factory() as db:
        assert db.get(Movement, uuid.UUID(body["id"])).loss_reason == "CLIMA"


def test_puede_dejar_la_existencia_en_cero(client, session_factory):
    assert client.post(URL, json=movement("PERDIDA", 10, lossReason="DESPERDICIO")).status_code == 201
    assert stock(session_factory) == 0.0


def test_no_permite_existencia_negativa(client, session_factory, parcel):
    # El inventario se crea con el primer movimiento aceptado; un rechazo previo no deja nada guardado.
    assert client.post(URL, json=movement("CONSUMO", 1, parcelId=parcel["id"])).status_code == 201
    response = client.post(URL, json=movement("CONSUMO", 9.01, parcelId=parcel["id"]))
    assert response.status_code == 409
    assert stock(session_factory) == 9.0
    assert count(session_factory) == 1


def test_rechazo_del_primer_movimiento_no_deja_inventario(client, session_factory, parcel):
    response = client.post(URL, json=movement("CONSUMO", 10.01, parcelId=parcel["id"]))
    assert response.status_code == 409
    assert count(session_factory) == 0
    with session_factory() as db:
        assert db.get(Inventory, uuid.UUID(INVENTORY["id"])) is None


def test_entrada_y_luego_consumo(client, session_factory, parcel):
    assert client.post(URL, json=movement("ENTRADA", 5)).status_code == 201
    assert client.post(URL, json=movement("CONSUMO", 12, parcelId=parcel["id"])).status_code == 201
    assert stock(session_factory) == 3.0


def test_reenvio_del_consumo_no_resta_dos_veces(client, session_factory, parcel):
    body = movement("CONSUMO", 3, parcelId=parcel["id"])
    client.post(URL, json=body)
    second = client.post(URL, json=body)
    assert second.status_code == 200
    assert second.json()["status"] == "DUPLICATE"
    assert stock(session_factory) == 7.0


def test_reenvio_con_otra_parcela_es_conflicto(client, session_factory, parcel):
    body = movement("CONSUMO", 3, parcelId=parcel["id"])
    client.post(URL, json=body)
    other = {"id": new_uuid(), "name": "El Mirador", "areaHa": 1}
    client.post(PARCELS, json=other)
    assert client.post(URL, json={**body, "parcelId": other["id"]}).status_code == 409


@pytest.mark.parametrize(
    "body",
    [
        movement("CONSUMO", 1),                                   # consumo sin parcela
        movement("PERDIDA", 1),                                   # pérdida sin motivo
        movement("PERDIDA", 1, lossReason="OTRO"),                # «Otro» sin observación
        movement("PERDIDA", 1, lossReason="ROBO"),                # motivo desconocido
        movement("ENTRADA", 1, lossReason="CLIMA"),               # motivo en una entrada
        movement("PERDIDA", 1, lossReason="CLIMA", parcelId=new_uuid()),  # parcela en una pérdida
    ],
)
def test_datos_incompletos_o_mezclados_responden_422(client, session_factory, body):
    assert client.post(URL, json=body).status_code == 422
    assert count(session_factory) == 0


def test_perdida_otro_con_observacion(client, session_factory):
    body = movement("PERDIDA", 1, lossReason="OTRO", observation="Se mojó el bulto")
    assert client.post(URL, json=body).status_code == 201


def test_parcela_inexistente_es_conflicto(client, session_factory):
    assert client.post(URL, json=movement("CONSUMO", 1, parcelId=new_uuid())).status_code == 409
    assert stock_or_none(session_factory) in (None, 10.0)


def stock_or_none(session_factory):
    with session_factory() as db:
        inventory = db.get(Inventory, uuid.UUID(INVENTORY["id"]))
        return None if inventory is None else float(inventory.quantity)


def test_ciclo_de_otra_parcela_es_conflicto(client, parcel):
    other = {"id": new_uuid(), "name": "La Palma", "areaHa": 3,
             "cycle": {"id": new_uuid(), "startDate": "2026-02-01", "status": "DESCANSO"}}
    client.post(PARCELS, json=other)
    body = movement("CONSUMO", 1, parcelId=parcel["id"], cycleId=other["cycle"]["id"])
    assert client.post(URL, json=body).status_code == 409


def test_no_puede_consumir_en_parcela_ajena(client, identity, session_factory, parcel):
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")
    assert client.post("/api/v1/users/register", json={"name": "Luis", "document": "2"}).status_code == 201
    luis_inventory = {"id": new_uuid(), "initialQuantity": 10,
                      "location": {"id": new_uuid(), "name": "Bodega"},
                      "supply": {"id": new_uuid(), "name": "Urea", "category": "Fertilizante", "unit": "kg"}}
    body = movement("CONSUMO", 1, parcelId=parcel["id"], inventory=luis_inventory)
    assert client.post(URL, json=body).status_code == 409
