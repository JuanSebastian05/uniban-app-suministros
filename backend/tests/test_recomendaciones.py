"""HU_12 en el backend: recomendaciones del Equipo 2 (`POST/GET /parcels/{id}/recommendation`).

El cliente del Equipo 2 se sustituye siempre (FakeEquipo2 o `httpx.MockTransport`): ninguna prueba toca la red ni el
servicio real. SQLite en memoria; el token de Firebase es una identidad que cada prueba puede cambiar.
"""

import copy
import uuid
from datetime import date, datetime, timedelta

import httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event, func, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models  # noqa: F401
from app.core.config import Settings
from app.core.security import FirebaseIdentity, get_current_identity
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import Aforamiento, Parcel, Recommendation
from app.services.recommendations import (
    Equipo2BadResponse,
    Equipo2Client,
    Equipo2Unavailable,
    build_request_body,
    get_equipo2_client,
    iso_period,
)

PARCELS = "/api/v1/parcels"
MOVEMENTS = "/api/v1/movements"
REGISTER = "/api/v1/users/register"
ANA = FirebaseIdentity(uid="uid-ana", email="ana@unibanagro.com")
LUIS = FirebaseIdentity(uid="uid-luis", email="luis@unibanagro.com")
TODAY = date.today()


def new_uuid() -> str:
    return str(uuid.uuid4())


def reply(**extra) -> dict:
    """Una `RespuestaRecomendacion` válida del Equipo 2."""
    parcel_id = extra.pop("parcela_id", "P-018")
    body = {
        "parcela_id": parcel_id, "periodo": "2026-W44", "volumen_racimos": 1850.0,
        "fecha_limite_corte": "2026-11-02T14:30:00", "cumple_ventana": True, "version_metodo": "v0.3.1",
        "item_plan": {
            "parcela_id": parcel_id, "fecha_corte": "2026-11-02T08:00:00", "vehiculo_id": "V-3", "orden": 1,
            "carga_estimada_racimos": 420, "cumple_ventana": True, "motivo": None,
        },
        "insumos": [
            {"insumo": "cajas", "fecha": "2026-11-01", "cantidad_requerida": 120.0,
             "inventario_disponible": 80.0, "deficit": 40.0},
            {"insumo": "bolsas", "fecha": "2026-11-01", "cantidad_requerida": 50.0,
             "inventario_disponible": 90.0, "deficit": 0.0},
        ],
        "metricas": {"cumplimiento_ventana": 1.0, "uso_capacidad": 0.88},
    }
    plan_overrides = extra.pop("item_plan", {})
    body["item_plan"].update(plan_overrides)
    body.update(extra)
    return body


class FakeEquipo2:
    """Sustituye al cliente real: anota lo recibido y responde lo que cada prueba disponga."""

    def __init__(self):
        self.bodies: list[dict] = []
        self.answer = lambda body: reply(parcela_id=body["parcela_id"])

    def recommend(self, body: dict) -> dict:
        self.bodies.append(copy.deepcopy(body))
        result = self.answer(body)
        if isinstance(result, Exception):
            raise result
        return result


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
def equipo2():
    return FakeEquipo2()


@pytest.fixture()
def client(session_factory, identity, equipo2):
    def override_get_db():
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_current_identity] = lambda: identity["value"]
    app.dependency_overrides[get_equipo2_client] = lambda: equipo2
    test_client = TestClient(app)
    assert test_client.post(REGISTER, json={"name": "Ana", "document": "100"}).status_code == 201
    yield test_client
    app.dependency_overrides.clear()


def create_parcel(client, name="La Esperanza", cycle=True) -> dict:
    body = {"id": new_uuid(), "name": name, "areaHa": 2.4}
    if cycle:
        body["cycle"] = {"id": new_uuid(), "startDate": "2026-01-15", "status": "PRODUCCION"}
    assert client.post(PARCELS, json=body).status_code == 201
    return body


def add_aforamiento(session_factory, parcel_id, day="2026-10-29", plants=1850, confidence=0.87, area=2.41):
    with session_factory() as db:
        db.add(Aforamiento(
            id=uuid.uuid4(), parcel_id=uuid.UUID(parcel_id), date=datetime.strptime(day, "%Y-%m-%d").date(),
            area_ha=area, estimated_plants=plants, confidence=confidence,
        ))
        db.commit()


SUPPLY = {"id": new_uuid(), "name": "Urea 46%", "category": "Fertilizante", "unit": "kg"}
LOCATION = {"id": new_uuid(), "name": "Inventario principal"}
INVENTORY = {"id": new_uuid(), "initialQuantity": 100, "location": LOCATION, "supply": SUPPLY}


def movement(kind: str, quantity: float, day: date, parcel: dict | None = None, **extra) -> dict:
    body = {
        "id": new_uuid(), "type": kind, "quantity": quantity, "date": day.isoformat(), "observation": "",
        "createdAt": "2026-10-24T10:00:00.000Z", "updatedAt": "2026-10-24T10:00:00.000Z",
        "inventory": copy.deepcopy(INVENTORY),
    }
    if parcel is not None:
        body["parcelId"] = parcel["id"]
        body["cycleId"] = parcel["cycle"]["id"]
    body.update(extra)
    return body


def post_recommendation(client, parcel_id):
    return client.post(f"{PARCELS}/{parcel_id}/recommendation")


@pytest.fixture()
def ready(client, session_factory):
    """Parcela de Ana con ciclo activo y un aforamiento."""
    parcel = create_parcel(client)
    add_aforamiento(session_factory, parcel["id"])
    return parcel


# --- POST: éxito ---

def test_pide_la_recomendacion_la_guarda_y_la_devuelve(client, session_factory, ready, equipo2):
    response = post_recommendation(client, ready["id"])

    assert response.status_code == 201
    data = response.json()
    assert data["parcelId"] == ready["id"]
    assert data["period"] == "2026-W44"
    assert data["sourceVersion"] == "v0.3.1"
    assert data["volumeBunches"] == 1850.0
    assert data["cutDeadline"].startswith("2026-11-02T14:30")
    assert data["meetsWindow"] is True
    assert data["plan"] == {
        "parcelId": ready["id"], "cutDate": "2026-11-02T08:00:00", "order": 1, "estimatedLoadBunches": 420,
        "meetsWindow": True, "vehicleId": "V-3", "reason": None,
    }
    assert [s["name"] for s in data["supplies"]] == ["cajas", "bolsas"]
    assert data["supplies"][0] == {
        "name": "cajas", "date": "2026-11-01", "requiredQuantity": 120.0, "availableQuantity": 80.0, "deficit": 40.0,
    }
    assert data["metrics"] == {"windowCompliance": 1.0, "capacityUse": 0.88}
    assert data["generatedAt"]
    assert len(equipo2.bodies) == 1
    with session_factory() as db:
        saved = db.scalar(select(Recommendation))
        assert str(saved.parcel_id) == ready["id"]
        assert saved.period == "2026-W44"
        assert saved.source_version == "v0.3.1"
        assert saved.payload["volumeBunches"] == 1850.0


def test_la_solicitud_al_equipo2_lleva_el_contrato_y_los_campos_directos(client, session_factory, ready, equipo2):
    post_recommendation(client, ready["id"])
    body = equipo2.bodies[0]

    assert body["parcel"]["id"] == ready["id"]
    assert body["parcel"]["name"] == "La Esperanza"
    assert body["parcel"]["areaHa"] == 2.4
    assert body["parcel"]["activeCycle"]["id"] == ready["cycle"]["id"]
    assert body["parcel"]["activeCycle"]["status"] == "PRODUCCION"
    assert body["aforamiento"] == {
        "date": "2026-10-29", "period": "2026-W44", "estimatedPlants": 1850, "confidence": 0.87,
    }
    assert body["producerId"]
    # Formato directo del Equipo 2.
    assert body["parcela_id"] == ready["id"]
    assert body["periodo"] == "2026-W44"
    assert body["num_plantas_estimado"] == 1850
    assert body["calidad_confianza"] == 0.87
    assert body["area_ha"] == 2.41  # el área del aforamiento, no la de la parcela
    assert body["ventana_despacho"] == {
        "inicio": f"{TODAY + timedelta(days=1)}T06:00:00",
        "fin": f"{TODAY + timedelta(days=1)}T16:00:00",
    }
    assert "losses" not in body
    # La ventana es sintética; vehículo, capacidad y acarreo siguen bajo los valores del Equipo 2.
    assert not {"vehiculo_id", "capacidad_vehiculo_racimos", "tiempo_acarreo_min"} & set(body)


@pytest.mark.parametrize("today,tomorrow", [
    (date(2026, 10, 29), "2026-10-30"),
    (date(2026, 11, 5), "2026-11-06"),
    (date(2026, 12, 31), "2027-01-01"),
])
def test_ventana_desde_la_solicitud_conserva_fecha_y_periodo_del_aforamiento(session_factory, ready, today, tomorrow):
    with session_factory() as db:
        parcel = db.get(Parcel, uuid.UUID(ready["id"]))
        aforamiento = db.scalar(select(Aforamiento).where(Aforamiento.parcel_id == parcel.id))
        body = build_request_body(db, parcel, aforamiento, parcel.producer_id, ANA.uid, today)

    assert body["ventana_despacho"] == {
        "inicio": f"{tomorrow}T06:00:00", "fin": f"{tomorrow}T16:00:00",
    }
    assert body["aforamiento"]["date"] == "2026-10-29"
    assert body["aforamiento"]["period"] == body["periodo"] == "2026-W44"


def test_solo_envia_empaques_y_sus_movimientos(client, session_factory, ready, equipo2):
    other = create_parcel(client, "El Mirador")
    assert client.post(MOVEMENTS, json=movement("CONSUMO", 12.5, TODAY - timedelta(days=5), ready)).status_code == 201
    assert client.post(MOVEMENTS, json=movement("CONSUMO", 3, TODAY - timedelta(days=4), other)).status_code == 201
    assert client.post(MOVEMENTS, json=movement("CONSUMO", 7, TODAY - timedelta(days=200), ready)).status_code == 201
    assert client.post(
        MOVEMENTS, json=movement("PERDIDA", 2, TODAY - timedelta(days=3), lossReason="CLIMA")
    ).status_code == 201
    assert client.post(
        MOVEMENTS, json=movement("PERDIDA", 1, TODAY - timedelta(days=300), lossReason="OTRO", observation="rotura")
    ).status_code == 201
    packaging_inventory = {
        "id": new_uuid(), "initialQuantity": 100,
        "location": copy.deepcopy(LOCATION),
        "supply": {"id": new_uuid(), "name": "cajas", "category": "Empaque", "unit": "unidad"},
    }
    assert client.post(
        MOVEMENTS,
        json=movement("CONSUMO", 1, TODAY - timedelta(days=2), ready, inventory=packaging_inventory),
    ).status_code == 201

    post_recommendation(client, ready["id"])
    body = equipo2.bodies[0]

    assert [(i["name"], i["quantity"], i["unit"]) for i in body["inventory"]] == [
        ("cajas", 99.0, "unidad"), ("carton", 0.0, "unidad"), ("bolsas", 0.0, "unidad"),
    ]
    # Solo se envían consumos recientes de empaque; las pérdidas no se comparten con el Equipo 2.
    assert [(c["name"], c["quantity"], c["date"]) for c in body["consumptions"]] == [
        ("cajas", 1.0, (TODAY - timedelta(days=2)).isoformat()),
    ]
    assert "losses" not in body
    assert "Urea 46%" not in str(body)


def test_no_envia_datos_de_otro_productor(client, session_factory, identity, equipo2):
    mine = create_parcel(client)
    add_aforamiento(session_factory, mine["id"])
    identity["value"] = LUIS
    assert client.post(REGISTER, json={"name": "Luis", "document": "200"}).status_code == 201
    theirs = create_parcel(client, "Suya")
    inventory = copy.deepcopy(INVENTORY)
    inventory.update(id=new_uuid(), location={"id": new_uuid(), "name": "Bodega"},
                     supply={"id": new_uuid(), "name": "Insumo de Luis", "category": "X", "unit": "kg"})
    assert client.post(MOVEMENTS, json=movement("CONSUMO", 9, TODAY, theirs, inventory=inventory)).status_code == 201
    identity["value"] = ANA

    post_recommendation(client, mine["id"])
    body = equipo2.bodies[0]

    assert [(item["name"], item["quantity"]) for item in body["inventory"]] == [
        ("cajas", 0.0), ("carton", 0.0), ("bolsas", 0.0),
    ]
    assert body["consumptions"] == [] and "losses" not in body
    assert "Insumo de Luis" not in str(body)


def test_sin_ciclo_activo_envia_ciclo_nulo(client, session_factory, equipo2):
    parcel = create_parcel(client, cycle=False)
    add_aforamiento(session_factory, parcel["id"])
    assert post_recommendation(client, parcel["id"]).status_code == 201
    assert equipo2.bodies[0]["parcel"]["activeCycle"] is None


def test_sin_confianza_no_se_envia_el_campo_directo(client, session_factory, equipo2):
    parcel = create_parcel(client)
    add_aforamiento(session_factory, parcel["id"], confidence=None)
    assert post_recommendation(client, parcel["id"]).status_code == 201
    body = equipo2.bodies[0]
    assert body["aforamiento"]["confidence"] is None
    assert "calidad_confianza" not in body


def test_usa_el_aforamiento_mas_reciente(client, session_factory, equipo2):
    parcel = create_parcel(client)
    add_aforamiento(session_factory, parcel["id"], day="2026-09-01", plants=900)
    add_aforamiento(session_factory, parcel["id"], day="2026-10-29", plants=1850)
    add_aforamiento(session_factory, parcel["id"], day="2026-10-01", plants=1200)
    post_recommendation(client, parcel["id"])
    assert equipo2.bodies[0]["num_plantas_estimado"] == 1850


@pytest.mark.parametrize("day,period", [
    ("2026-10-29", "2026-W44"), ("2026-01-01", "2026-W01"), ("2027-01-01", "2026-W53"),
    ("2024-12-30", "2025-W01"), ("2026-03-02", "2026-W10"),
])
def test_periodo_es_la_semana_iso_de_la_fecha(day, period):
    assert iso_period(datetime.strptime(day, "%Y-%m-%d").date()) == period


# --- POST: aforamiento ausente y propiedad de la parcela ---

def test_sin_aforamiento_responde_409_y_no_llama_al_equipo2(client, equipo2):
    parcel = create_parcel(client)
    response = post_recommendation(client, parcel["id"])
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "NO_AFORAMIENTO"
    assert "aforamiento" in response.json()["detail"]["message"].lower()
    assert equipo2.bodies == []


def test_parcela_de_otro_productor_responde_404_sin_llamar_al_equipo2(client, session_factory, identity, equipo2):
    parcel = create_parcel(client)
    add_aforamiento(session_factory, parcel["id"])
    identity["value"] = LUIS
    assert client.post(REGISTER, json={"name": "Luis", "document": "200"}).status_code == 201

    response = post_recommendation(client, parcel["id"])

    assert response.status_code == 404
    assert response.json() == post_recommendation(client, new_uuid()).json()  # no revela que existe
    assert equipo2.bodies == []


def test_sin_token_responde_401():
    app.dependency_overrides.clear()
    assert TestClient(app).post(f"{PARCELS}/{new_uuid()}/recommendation").status_code == 401
    assert TestClient(app).get(f"{PARCELS}/{new_uuid()}/recommendation").status_code == 401


def test_cuenta_sin_perfil_responde_403(client, identity):
    identity["value"] = FirebaseIdentity(uid="uid-sin-perfil", email="x@unibanagro.com")
    assert post_recommendation(client, new_uuid()).status_code == 403
    assert client.get(f"{PARCELS}/{new_uuid()}/recommendation").status_code == 403


def test_id_que_no_es_uuid_responde_422(client):
    assert client.post(f"{PARCELS}/no-es-uuid/recommendation").status_code == 422


# --- POST: fallos del Equipo 2 ---

def test_respuesta_con_forma_invalida_responde_502_y_no_guarda(client, session_factory, ready, equipo2):
    broken = reply()
    del broken["volumen_racimos"]
    wrong_type = reply(cumple_ventana="quizás")
    no_plan = reply()
    del no_plan["item_plan"]
    for answer in (broken, wrong_type, no_plan, {"detail": "otra cosa"}, reply(insumos="no es una lista")):
        equipo2.answer = lambda body, answer=answer: answer
        response = post_recommendation(client, ready["id"])
        assert response.status_code == 502, answer
        assert response.json()["detail"]["code"] == "EQUIPO2_BAD_RESPONSE"
    with session_factory() as db:
        assert db.scalar(select(func.count()).select_from(Recommendation)) == 0


def test_campos_extra_del_equipo2_se_ignoran(client, ready, equipo2):
    equipo2.answer = lambda body: reply(parcela_id=body["parcela_id"], campo_nuevo={"x": 1})
    assert post_recommendation(client, ready["id"]).status_code == 201


def test_equipo2_no_disponible_responde_503(client, session_factory, ready, equipo2):
    equipo2.answer = lambda body: Equipo2Unavailable("El servicio de recomendaciones no está disponible.")
    response = post_recommendation(client, ready["id"])
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "EQUIPO2_UNAVAILABLE"
    with session_factory() as db:
        assert db.scalar(select(func.count()).select_from(Recommendation)) == 0


def test_equipo2_que_rechaza_la_solicitud_responde_502(client, ready, equipo2):
    equipo2.answer = lambda body: Equipo2BadResponse("El servicio de recomendaciones rechazó la solicitud.")
    assert post_recommendation(client, ready["id"]).status_code == 502


# --- GET: la última guardada ---

def test_sin_recomendaciones_responde_404(client, ready):
    response = client.get(f"{PARCELS}/{ready['id']}/recommendation")
    assert response.status_code == 404
    assert response.json()["detail"]["code"] == "NO_RECOMMENDATION"


def test_devuelve_la_ultima_guardada(client, ready, equipo2, session_factory):
    first = post_recommendation(client, ready["id"]).json()
    # Fechas distintas sin depender de la resolución del reloj en Windows.
    with session_factory() as db:
        saved = db.get(Recommendation, uuid.UUID(first["id"]))
        saved.generated_at -= timedelta(seconds=1)
        db.commit()
    equipo2.answer = lambda body: reply(
        parcela_id=body["parcela_id"], periodo="2026-W45", volumen_racimos=2100.0, version_metodo="v0.4.0",
    )
    second = post_recommendation(client, ready["id"]).json()
    assert first["id"] != second["id"]

    latest = client.get(f"{PARCELS}/{ready['id']}/recommendation")

    assert latest.status_code == 200
    assert latest.json()["id"] == second["id"]
    assert latest.json()["period"] == "2026-W45"
    assert latest.json()["volumeBunches"] == 2100.0
    assert latest.json()["sourceVersion"] == "v0.4.0"
    assert latest.json() == second


def test_leer_no_llama_al_equipo2(client, ready, equipo2):
    post_recommendation(client, ready["id"])
    client.get(f"{PARCELS}/{ready['id']}/recommendation")
    client.get(f"{PARCELS}/{ready['id']}/recommendation")
    assert len(equipo2.bodies) == 1


def test_get_de_parcela_ajena_o_inexistente_responde_404(client, session_factory, identity, ready):
    post_recommendation(client, ready["id"])
    identity["value"] = LUIS
    assert client.post(REGISTER, json={"name": "Luis", "document": "200"}).status_code == 201
    mine = client.get(f"{PARCELS}/{ready['id']}/recommendation")
    missing = client.get(f"{PARCELS}/{new_uuid()}/recommendation")
    assert mine.status_code == missing.status_code == 404
    assert mine.json() == missing.json()
    assert "volumeBunches" not in mine.text


def test_aparece_documentado_en_openapi(client):
    ops = client.get("/openapi.json").json()["paths"]["/api/v1/parcels/{parcel_id}/recommendation"]
    assert {"201", "404", "409", "502", "503"} <= set(ops["post"]["responses"])
    assert {"200", "404"} <= set(ops["get"]["responses"])


# --- Cliente HTTP del Equipo 2 (transporte simulado: sin red) ---

URL = "https://equipo2.invalid/api/v1/recomendar"


def make_client(handler, **kwargs) -> tuple[Equipo2Client, list]:
    sleeps: list[float] = []
    options = dict(retry_delay=5.0, transport=httpx.MockTransport(handler), sleep=sleeps.append, warm_up=False)
    options.update(kwargs)
    return Equipo2Client("https://equipo2.invalid/", **options), sleeps


def test_el_cliente_hace_post_json_a_la_ruta_correcta():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen.update(url=str(request.url), method=request.method, type=request.headers["content-type"],
                    key=request.headers.get("x-api-key"), body=request.content)
        return httpx.Response(200, json=reply())

    client, sleeps = make_client(handler)
    assert client.recommend({"producerId": "p"})["periodo"] == "2026-W44"
    assert seen["url"] == URL and seen["method"] == "POST"
    assert seen["type"].startswith("application/json")
    assert seen["key"] is None  # su API no pide clave: no se manda ninguna
    assert b'"producerId"' in seen["body"]
    assert sleeps == []


def test_la_clave_opcional_va_en_la_cabecera_solo_si_esta_configurada():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["key"] = request.headers.get("x-api-key")
        return httpx.Response(200, json=reply())

    client, _ = make_client(handler, api_key="clave-falsa-de-prueba")
    client.recommend({})
    assert seen["key"] == "clave-falsa-de-prueba"


def test_por_defecto_calienta_espera_90_segundos_y_reintenta_una_vez():
    client = Equipo2Client("https://equipo2.invalid")
    assert client._timeout == 90.0
    assert client._attempts == 2
    assert client._warm_up is True
    assert client._health_url == "https://equipo2.invalid/salud"


def test_calienta_con_un_get_a_salud_antes_del_post():
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append((request.method, request.url.path))
        return httpx.Response(200, json=reply() if request.method == "POST" else {"estado": "ok"})

    client, sleeps = make_client(handler, warm_up=True)
    assert client.recommend({})["periodo"] == "2026-W44"
    assert seen == [("GET", "/salud"), ("POST", "/api/v1/recomendar")]
    assert sleeps == []


@pytest.mark.parametrize("failure", [
    httpx.ReadTimeout("dormido"), httpx.ConnectError("sin red"), httpx.Response(503, text="despertando"),
])
def test_si_el_calentamiento_falla_igual_se_intenta_la_recomendacion(failure):
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request.method)
        if request.method == "GET":
            if isinstance(failure, Exception):
                raise failure
            return failure
        return httpx.Response(200, json=reply())

    client, _ = make_client(handler, warm_up=True)
    assert client.recommend({})["periodo"] == "2026-W44"
    assert seen == ["GET", "POST"]


def test_tiempo_agotado_se_reintenta_y_luego_funciona():
    calls = []

    def handler(request):
        calls.append(1)
        if len(calls) == 1:
            raise httpx.ReadTimeout("dormido", request=request)
        return httpx.Response(200, json=reply())

    client, sleeps = make_client(handler)
    assert client.recommend({})["periodo"] == "2026-W44"
    assert len(calls) == 2 and sleeps == [5.0]


def test_un_503_mientras_despierta_se_reintenta():
    answers = iter([httpx.Response(503, text="Service Unavailable"), httpx.Response(200, json=reply())])
    client, sleeps = make_client(lambda request: next(answers))
    assert client.recommend({})["version_metodo"] == "v0.3.1"
    assert len(sleeps) == 1


def test_sin_respuesta_tras_el_reintento_es_no_disponible():
    calls = []

    def handler(request):
        calls.append(1)
        raise httpx.ConnectTimeout("sin respuesta", request=request)

    client, sleeps = make_client(handler)
    with pytest.raises(Equipo2Unavailable) as error:
        client.recommend({})
    assert len(calls) == 2 and len(sleeps) == 1
    assert "no está disponible" in error.value.message


@pytest.mark.parametrize("status", [500, 502, 503, 504])
def test_error_5xx_dos_veces_es_no_disponible(status):
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(status, text="boom")

    client, _ = make_client(handler)
    with pytest.raises(Equipo2Unavailable):
        client.recommend({})
    assert len(calls) == 2


def test_error_de_red_se_reintenta():
    answers = iter([httpx.ConnectError("sin red"), httpx.Response(200, json=reply())])

    def handler(request):
        item = next(answers)
        if isinstance(item, Exception):
            raise item
        return item

    client, sleeps = make_client(handler)
    assert client.recommend({})
    assert len(sleeps) == 1


@pytest.mark.parametrize("status", [400, 404, 422])
def test_un_4xx_no_se_reintenta_y_es_respuesta_invalida(status):
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(status, json={"detail": [{"msg": "dato secreto del productor"}]})

    client, sleeps = make_client(handler)
    with pytest.raises(Equipo2BadResponse) as error:
        client.recommend({})
    assert len(calls) == 1 and sleeps == []
    assert "dato secreto" not in error.value.message  # el detalle ajeno no se reenvía a la app


@pytest.mark.parametrize("response", [
    httpx.Response(200, text="<html>no es json</html>"),
    httpx.Response(200, json=["una", "lista"]),
    httpx.Response(200, text=""),
])
def test_200_que_no_es_un_objeto_json_es_respuesta_invalida(response):
    client, _ = make_client(lambda request: response)
    with pytest.raises(Equipo2BadResponse):
        client.recommend({})


def test_el_cliente_por_defecto_sale_de_la_configuracion(monkeypatch):
    import app.services.recommendations as service

    monkeypatch.setattr(service, "get_settings", lambda: Settings(
        _env_file=None, equipo2_base_url="https://otro.invalid/", equipo2_api_key="clave-falsa",
    ))
    client = get_equipo2_client()
    assert client._url == "https://otro.invalid/api/v1/recomendar"
    assert client._headers["X-API-Key"] == "clave-falsa"


def test_la_url_por_defecto_es_la_del_equipo2():
    assert Settings(_env_file=None).equipo2_base_url == "https://platano-logistica-api.onrender.com"
    assert Settings(_env_file=None).equipo2_api_key is None
