"""Pruebas del registro de usuarios (HU_01).

La verificación del token de Firebase se reemplaza por una identidad fija: no se usa la red
ni un proyecto real. La base es SQLite en memoria.
"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.config import get_settings
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import Producer, User
from app.models.user import document_key

REGISTER = "/api/v1/users/register"
ME = "/api/v1/users/me"
BODY = {"name": "  Ana Pérez ", "document": "1020304050", "phone": "3001234567"}


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
    yield TestClient(app)
    app.dependency_overrides.clear()


def count(session_factory, model) -> int:
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(model))


def test_registro_crea_usuario_y_su_productor(client, session_factory):
    response = client.post(REGISTER, json=BODY)
    assert response.status_code == 201
    data = response.json()
    assert data["email"] == "ana@unibanagro.com"
    assert data["name"] == "Ana Pérez"
    assert data["document"] == "1020304050"
    assert data["phone"] == "3001234567"
    assert data["role"] == "PRODUCTOR"
    with session_factory() as db:
        user = db.scalar(select(User))
        assert user.firebase_uid == "uid-ana"
        producer = db.get(Producer, user.producer_id)
        assert producer.name == "Ana Pérez"
        assert str(producer.id) == data["producerId"]


def test_reintentar_el_registro_no_duplica(client, session_factory):
    first = client.post(REGISTER, json=BODY)
    second = client.post(REGISTER, json={**BODY, "name": "Otro nombre"})
    assert second.status_code == 200
    assert second.json() == first.json()
    assert count(session_factory, User) == 1
    assert count(session_factory, Producer) == 1


def test_cada_cuenta_tiene_su_propio_productor(client, session_factory, identity):
    client.post(REGISTER, json=BODY)
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")
    other = client.post(REGISTER, json={**BODY, "name": "Luis", "document": "52123456"})
    assert other.status_code == 201
    assert count(session_factory, User) == 2
    assert count(session_factory, Producer) == 2


def _second_account(identity):
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")


@pytest.mark.parametrize("document", ["1020304050", " 1020304050 ", "1.020.304.050", "1 020 304 050", "1020-304-050"])
def test_documento_repetido_se_rechaza_con_409(client, session_factory, identity, document):
    assert client.post(REGISTER, json=BODY).status_code == 201
    _second_account(identity)
    response = client.post(REGISTER, json={**BODY, "name": "Luis", "document": document})
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "DUPLICATE_DOCUMENT"
    # No queda una cuenta ni un productor a medias.
    assert count(session_factory, User) == 1
    assert count(session_factory, Producer) == 1


def test_documento_con_letras_ignora_mayusculas(client, identity):
    assert client.post(REGISTER, json={**BODY, "document": "ab-123456"}).status_code == 201
    _second_account(identity)
    response = client.post(REGISTER, json={**BODY, "name": "Luis", "document": "AB 123456"})
    assert response.status_code == 409


def test_reintento_de_la_misma_cuenta_con_su_documento_no_es_conflicto(client, session_factory):
    assert client.post(REGISTER, json=BODY).status_code == 201
    retry = client.post(REGISTER, json={**BODY, "document": "1.020.304.050"})
    assert retry.status_code == 200
    assert count(session_factory, User) == 1


def test_documento_distinto_se_acepta(client, identity):
    assert client.post(REGISTER, json=BODY).status_code == 201
    _second_account(identity)
    assert client.post(REGISTER, json={**BODY, "name": "Luis", "document": "1020304051"}).status_code == 201


@pytest.mark.parametrize("document", ["---", "...", "  .  "])
def test_documento_sin_letras_ni_numeros_es_invalido(client, session_factory, document):
    assert client.post(REGISTER, json={**BODY, "document": document}).status_code == 422
    assert count(session_factory, User) == 0


def test_la_base_impide_dos_usuarios_con_el_mismo_documento(session_factory):
    with session_factory() as db:
        for index, uid in enumerate(("uid-a", "uid-b")):
            producer = Producer(id=uuid.uuid4(), name=f"P{index}")
            db.add(producer)
            db.flush()
            db.add(User(firebase_uid=uid, email=f"{uid}@x.co", name="N", document="1.0", document_key="10",
                        producer_id=producer.id))
            if index == 0:
                db.commit()
        with pytest.raises(IntegrityError):
            db.commit()


@pytest.mark.parametrize(
    ("document", "key"),
    [("1.020.304.050", "1020304050"), (" ab-12 ", "AB12"), ("é 1", "É1"), ("---", "")],
)
def test_clave_del_documento(document, key):
    assert document_key(document) == key


def test_telefono_es_opcional(client):
    response = client.post(REGISTER, json={"name": "Ana", "document": "123", "phone": "  "})
    assert response.status_code == 201
    assert response.json()["phone"] is None


@pytest.mark.parametrize("body", [{"name": "", "document": "1"}, {"name": "Ana", "document": ""}, {"name": "Ana"}])
def test_datos_obligatorios(client, session_factory, body):
    assert client.post(REGISTER, json=body).status_code == 422
    assert count(session_factory, User) == 0


def test_token_sin_correo_se_rechaza(client, identity):
    identity["value"] = FirebaseIdentity(uid="uid-x", email=None)
    assert client.post(REGISTER, json=BODY).status_code == 422


def test_perfil_del_usuario_autenticado(client):
    assert client.get(ME).status_code == 404
    created = client.post(REGISTER, json=BODY).json()
    response = client.get(ME)
    assert response.status_code == 200
    assert response.json() == created


def test_sin_token_responde_401(session_factory):
    app.dependency_overrides[get_db] = lambda: session_factory()
    try:
        response = TestClient(app).post(REGISTER, json=BODY)
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 401


def test_sin_proyecto_de_firebase_responde_503(session_factory, monkeypatch):
    monkeypatch.setattr(get_settings(), "firebase_project_id", None)
    app.dependency_overrides[get_db] = lambda: session_factory()
    try:
        response = TestClient(app).post(REGISTER, json=BODY, headers={"Authorization": "Bearer abc"})
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 503


def _claims(**overrides):
    claims = {"iss": "https://securetoken.google.com/uniban-test", "aud": "uniban-test",
              "sub": "uid-ana", "email": "ana@unibanagro.com"}
    claims.update(overrides)
    return claims


@pytest.fixture()
def real_identity(session_factory, monkeypatch):
    """Cliente que usa la verificación real del token, con google-auth simulado (sin red)."""
    monkeypatch.setattr(get_settings(), "firebase_project_id", "uniban-test")
    calls = {}

    def fake_verify(token, request, audience=None, clock_skew_in_seconds=0):
        calls["audience"] = audience
        if token != "token-valido":
            raise ValueError("firma inválida")
        return calls.get("claims", _claims())

    from google.oauth2 import id_token

    monkeypatch.setattr(id_token, "verify_firebase_token", fake_verify)
    app.dependency_overrides[get_db] = lambda: session_factory()
    yield TestClient(app), calls
    app.dependency_overrides.clear()


def test_token_valido_registra_con_uid_del_token(real_identity, session_factory):
    client, calls = real_identity
    response = client.post(REGISTER, json=BODY, headers={"Authorization": "Bearer token-valido"})
    assert response.status_code == 201
    assert calls["audience"] == "uniban-test"
    with session_factory() as db:
        assert db.scalar(select(User)).firebase_uid == "uid-ana"


def test_token_con_firma_invalida_responde_401(real_identity):
    client, _ = real_identity
    response = client.post(REGISTER, json=BODY, headers={"Authorization": "Bearer otro"})
    assert response.status_code == 401


def test_token_de_otro_proyecto_responde_401(real_identity):
    client, calls = real_identity
    calls["claims"] = _claims(iss="https://securetoken.google.com/otro-proyecto")
    response = client.post(REGISTER, json=BODY, headers={"Authorization": "Bearer token-valido"})
    assert response.status_code == 401
