"""Pruebas de la sincronización de movimientos (HU_06, RT_07, RT_17).

Usan SQLite en memoria; no requieren PostgreSQL ni la base de Neon.
"""

import copy
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import Inventory, Movement, Producer, Supply

URL = "/api/v1/movements"


def new_uuid() -> str:
    return str(uuid.uuid4())


PRODUCER = {"id": new_uuid(), "name": "Productor de demostración"}
LOCATION = {"id": new_uuid(), "name": "Inventario principal", "producer": PRODUCER}
SUPPLY = {"id": new_uuid(), "name": "Fertilizante 15-15-15", "category": "Fertilizante", "unit": "kg"}
INVENTORY = {"id": new_uuid(), "initialQuantity": 120.0, "location": LOCATION, "supply": SUPPLY}


def payload(quantity: float = 1.5, movement_id: str | None = None, **overrides) -> dict:
    body = {
        "id": movement_id or new_uuid(),
        "type": "ENTRADA",
        "quantity": quantity,
        "date": "2026-09-29",
        "observation": "compra",
        "createdAt": "2026-09-29T21:40:00.123Z",
        "updatedAt": "2026-09-29T21:40:00.123Z",
        "inventory": copy.deepcopy(INVENTORY),
    }
    body.update(overrides)
    return body


@pytest.fixture()
def session_factory():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
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
    yield TestClient(app)
    app.dependency_overrides.clear()


def stock(session_factory) -> float:
    with session_factory() as db:
        return float(db.get(Inventory, uuid.UUID(INVENTORY["id"])).quantity)


def count(session_factory, model) -> int:
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(model))


def test_movimiento_nuevo_se_guarda_y_suma_existencia(client, session_factory):
    body = payload(1.5)
    response = client.post(URL, json=body)
    assert response.status_code == 201
    assert response.json() == {"id": body["id"], "status": "CREATED"}
    assert count(session_factory, Movement) == 1
    assert stock(session_factory) == 121.5


def test_datos_de_referencia_se_insertan_una_sola_vez(client, session_factory):
    client.post(URL, json=payload(1))
    client.post(URL, json=payload(2))
    assert count(session_factory, Producer) == 1
    assert count(session_factory, Supply) == 1
    assert count(session_factory, Inventory) == 1
    assert stock(session_factory) == 123.0


def test_reenvio_del_mismo_uuid_no_duplica_ni_suma(client, session_factory):
    body = payload(1.5)
    assert client.post(URL, json=body).status_code == 201
    second = client.post(URL, json=body)
    assert second.status_code == 200
    assert second.json()["status"] == "DUPLICATE"
    assert count(session_factory, Movement) == 1
    assert stock(session_factory) == 121.5


def test_mismo_uuid_con_contenido_distinto_es_conflicto(client, session_factory):
    body = payload(1.5)
    client.post(URL, json=body)
    body["quantity"] = 9
    response = client.post(URL, json=body)
    assert response.status_code == 409
    assert stock(session_factory) == 121.5


def test_datos_de_referencia_existentes_no_se_sobrescriben(client, session_factory):
    client.post(URL, json=payload(1))
    other = payload(1)
    other["inventory"]["supply"]["name"] = "Nombre alterado"
    other["inventory"]["initialQuantity"] = 5
    assert client.post(URL, json=other).status_code == 201
    with session_factory() as db:
        assert db.get(Supply, uuid.UUID(SUPPLY["id"])).name == SUPPLY["name"]
        assert float(db.get(Inventory, uuid.UUID(INVENTORY["id"])).initial_quantity) == 120.0
    assert stock(session_factory) == 122.0


@pytest.mark.parametrize("quantity", [0, -1, 1.234, 100000])
def test_cantidad_invalida_es_rechazada(client, session_factory, quantity):
    response = client.post(URL, json=payload(quantity))
    assert response.status_code == 422
    assert count(session_factory, Movement) == 0


def test_tipo_no_soportado_es_conflicto(client, session_factory):
    response = client.post(URL, json=payload(1, type="CONSUMO"))
    assert response.status_code == 409
    assert count(session_factory, Movement) == 0


def test_existencia_resultante_sobre_el_limite_es_conflicto(client, session_factory):
    client.post(URL, json=payload(99000))
    response = client.post(URL, json=payload(1000))
    assert response.status_code == 409
    assert count(session_factory, Movement) == 1
    assert stock(session_factory) == 99120.0


def test_uuid_invalido_es_rechazado(client):
    assert client.post(URL, json=payload(1, movement_id="no-es-uuid")).status_code == 422
