"""Ajustes de inventario (correcciones): suman o restan según el sentido, exigen motivo, nunca dejan
la existencia negativa y son idempotentes. SQLite en memoria; el token de Firebase es una identidad fija.
"""

import copy
import uuid

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
from app.models import Inventory, Movement

URL = "/api/v1/movements"


def new_uuid() -> str:
    return str(uuid.uuid4())


LOCATION = {"id": new_uuid(), "name": "Inventario principal"}
SUPPLY = {"id": new_uuid(), "name": "Urea 46%", "category": "Fertilizante", "unit": "kg"}
INVENTORY = {"id": new_uuid(), "initialQuantity": 10, "location": LOCATION, "supply": SUPPLY}


def movement(type_: str, quantity: float, **extra) -> dict:
    body = {
        "id": new_uuid(), "type": type_, "quantity": quantity, "date": "2026-10-24", "observation": "",
        "createdAt": "2026-10-24T10:00:00.000Z", "updatedAt": "2026-10-24T10:00:00.000Z",
        "inventory": copy.deepcopy(INVENTORY),
    }
    body.update(extra)
    return body


def adjustment(quantity: float, direction: str = "AUMENTO", reason: str = "Conteo físico", **extra) -> dict:
    return movement("AJUSTE", quantity, adjustDirection=direction, observation=reason, **extra)


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
    app.dependency_overrides[get_current_identity] = lambda: FirebaseIdentity(
        uid="uid-ana", email="ana@unibanagro.com",
    )
    test_client = TestClient(app)
    assert test_client.post("/api/v1/users/register", json={"name": "Ana", "document": "1"}).status_code == 201
    yield test_client
    app.dependency_overrides.clear()


def stock(session_factory) -> float:
    with session_factory() as db:
        return float(db.get(Inventory, uuid.UUID(INVENTORY["id"])).quantity)


def test_ajuste_de_aumento_suma_y_guarda_el_sentido(client, session_factory):
    body = adjustment(2.5, "AUMENTO")
    assert client.post(URL, json=body).status_code == 201
    assert stock(session_factory) == 12.5
    with session_factory() as db:
        saved = db.get(Movement, uuid.UUID(body["id"]))
        assert saved.type == "AJUSTE"
        assert saved.adjust_direction == "AUMENTO"
        assert saved.observation == "Conteo físico"


def test_ajuste_de_disminucion_resta(client, session_factory):
    assert client.post(URL, json=adjustment(4, "DISMINUCION")).status_code == 201
    assert stock(session_factory) == 6.0


def test_ajuste_puede_dejar_la_existencia_en_cero(client, session_factory):
    assert client.post(URL, json=adjustment(10, "DISMINUCION")).status_code == 201
    assert stock(session_factory) == 0.0


def test_ajuste_de_disminucion_no_deja_existencia_negativa(client, session_factory):
    assert client.post(URL, json=adjustment(1, "AUMENTO")).status_code == 201  # crea el inventario
    response = client.post(URL, json=adjustment(11.01, "DISMINUCION"))
    assert response.status_code == 409
    assert stock(session_factory) == 11.0


def test_ajuste_de_aumento_no_supera_el_maximo(client, session_factory):
    assert client.post(URL, json=adjustment(1, "AUMENTO")).status_code == 201
    assert client.post(URL, json=adjustment(99999.99, "AUMENTO")).status_code == 409
    assert stock(session_factory) == 11.0


def test_ajuste_exige_sentido(client):
    body = movement("AJUSTE", 1, observation="Conteo físico")
    assert client.post(URL, json=body).status_code == 422


def test_ajuste_exige_motivo_en_la_observacion(client):
    assert client.post(URL, json=adjustment(1, reason="")).status_code == 422
    assert client.post(URL, json=adjustment(1, reason="   ")).status_code == 422


def test_ajuste_rechaza_sentido_desconocido(client):
    assert client.post(URL, json=adjustment(1, "SUBIR")).status_code == 422


@pytest.mark.parametrize("extra", [{"lossReason": "CLIMA"}, {"parcelId": new_uuid()}])
def test_ajuste_no_lleva_motivo_de_perdida_ni_parcela(client, extra):
    assert client.post(URL, json=adjustment(1, **extra)).status_code == 422


def test_otros_tipos_no_llevan_sentido(client):
    assert client.post(URL, json=movement("ENTRADA", 1, adjustDirection="AUMENTO")).status_code == 422


def test_reenviar_el_mismo_ajuste_no_vuelve_a_aplicarlo(client, session_factory):
    body = adjustment(3, "DISMINUCION")
    assert client.post(URL, json=body).status_code == 201
    again = client.post(URL, json=body)
    assert again.status_code == 200
    assert stock(session_factory) == 7.0


def test_mismo_id_con_otro_sentido_es_conflicto(client, session_factory):
    body = adjustment(3, "DISMINUCION")
    assert client.post(URL, json=body).status_code == 201
    body["adjustDirection"] = "AUMENTO"
    assert client.post(URL, json=body).status_code == 409
    assert stock(session_factory) == 7.0
