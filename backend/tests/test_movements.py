"""Pruebas de la sincronización de movimientos (HU_06, RT_07, RT_17) y del aislamiento por usuario.

Usan SQLite en memoria; no requieren PostgreSQL ni la base de Neon. La verificación del token
de Firebase se reemplaza por una identidad que cada prueba puede cambiar.
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
from app.models import Inventory, Location, Movement, Producer, Supply, User

URL = "/api/v1/movements"
INVENTORY_URL = "/api/v1/inventory"


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

    # SQLite no valida llaves foráneas por defecto; PostgreSQL sí. Se activan para que estas
    # pruebas detecten inserts en orden incorrecto.
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


def login_as(client, identity, uid: str) -> None:
    """Cambia la cuenta autenticada y la registra (cada cuenta tiene su propio productor)."""
    identity["value"] = FirebaseIdentity(uid=uid, email=f"{uid}@unibanagro.com")
    assert client.post("/api/v1/users/register", json={"name": uid, "document": "2"}).status_code == 201


def producer_of(session_factory, uid: str) -> uuid.UUID:
    with session_factory() as db:
        return db.scalar(select(User.producer_id).where(User.firebase_uid == uid))


def other_inventory() -> dict:
    """Inventario nuevo, con ubicación e insumo propios (como lo crea la app de otro usuario)."""
    location = {"id": new_uuid(), "name": "Inventario principal"}
    supply = {"id": new_uuid(), "name": "Fertilizante 15-15-15", "category": "Fertilizante", "unit": "kg"}
    return {"id": new_uuid(), "initialQuantity": 10, "location": location, "supply": supply}


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
    # SALIDA aún no se acepta (AJUSTE, CONSUMO y PERDIDA ya sí).
    response = client.post(URL, json=payload(1, type="SALIDA"))
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


# --- Aislamiento por usuario ---


def test_sin_token_responde_401(session_factory):
    app.dependency_overrides[get_db] = lambda: session_factory()
    try:
        response = TestClient(app).post(URL, json=payload(1))
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 401
    assert count(session_factory, Movement) == 0


def test_cuenta_sin_registrar_responde_403(client, identity, session_factory):
    identity["value"] = FirebaseIdentity(uid="uid-sin-perfil", email="x@unibanagro.com")
    assert client.post(URL, json=payload(1)).status_code == 403
    assert client.get(INVENTORY_URL).status_code == 403
    assert count(session_factory, Movement) == 0


def test_el_inventario_queda_a_nombre_del_productor_del_token(client, session_factory):
    # El productor que manda la app se ignora: el dueño sale del token.
    assert client.post(URL, json=payload(1)).status_code == 201
    owner = producer_of(session_factory, "uid-ana")
    with session_factory() as db:
        assert db.get(Location, uuid.UUID(LOCATION["id"])).producer_id == owner
        assert db.get(Supply, uuid.UUID(SUPPLY["id"])).producer_id == owner
        assert db.get(Producer, uuid.UUID(PRODUCER["id"])) is None


def test_otro_usuario_no_puede_mover_un_inventario_ajeno(client, identity, session_factory):
    client.post(URL, json=payload(1))
    login_as(client, identity, "uid-luis")

    response = client.post(URL, json=payload(5))

    assert response.status_code == 409
    assert stock(session_factory) == 121.0
    assert count(session_factory, Movement) == 1


def test_otro_usuario_no_puede_usar_un_insumo_ajeno(client, identity, session_factory):
    client.post(URL, json=payload(1))
    login_as(client, identity, "uid-luis")
    inventory = other_inventory()
    inventory["supply"] = copy.deepcopy(SUPPLY)

    response = client.post(URL, json=payload(1, inventory=inventory))

    assert response.status_code == 409
    assert count(session_factory, Inventory) == 1


def test_uuid_de_un_movimiento_ajeno_es_conflicto_sin_cambios(client, identity, session_factory):
    body = payload(1)
    client.post(URL, json=body)
    login_as(client, identity, "uid-luis")

    response = client.post(URL, json=body)

    assert response.status_code == 409
    assert stock(session_factory) == 121.0


def test_cada_usuario_ve_y_modifica_solo_su_inventario(client, identity, session_factory):
    client.post(URL, json=payload(1))
    login_as(client, identity, "uid-luis")
    inventory = other_inventory()
    assert client.post(URL, json=payload(4, inventory=inventory)).status_code == 201

    luis = client.get(INVENTORY_URL)
    assert luis.status_code == 200
    assert [item["id"] for item in luis.json()] == [inventory["id"]]
    assert float(luis.json()[0]["quantity"]) == 14.0

    identity["value"] = FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")
    ana = client.get(INVENTORY_URL).json()
    assert [item["id"] for item in ana] == [INVENTORY["id"]]
    assert float(ana[0]["quantity"]) == 121.0
    assert ana[0]["name"] == SUPPLY["name"]
    assert ana[0]["location"] == LOCATION["name"]


def test_inventario_vacio_para_un_usuario_nuevo(client):
    response = client.get(INVENTORY_URL)
    assert response.status_code == 200
    assert response.json() == []
