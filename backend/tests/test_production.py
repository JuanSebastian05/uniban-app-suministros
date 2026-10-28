"""Contrato real FastAPI con SQLite temporal; sin contactar equipos ni bases compartidas."""
import copy
import uuid
from datetime import date, datetime, timezone
from decimal import Decimal
import pytest
from sqlalchemy import func, select
from tests.test_actividades import session_factory, identity, client, parcel, new_uuid
from app.models import Activity, Aforamiento, Dispatch, Harvest, Inventory, Movement, Recommendation
from app.services.production import packaging_targets, BOGOTA


@pytest.fixture()
def plan(session_factory, parcel):
    aforamiento_id, recommendation_id = uuid.uuid4(), uuid.uuid4()
    payload = {
        "id": str(recommendation_id), "parcelId": parcel["id"], "period": "2026-W44",
        "generatedAt": "2026-10-26T12:00:00Z", "sourceVersion": "test", "volumeBunches": 145.6,
        "cutDeadline": "2026-10-28T16:00:00", "meetsWindow": True,
        "plan": {"parcelId": parcel["id"], "cutDate": "2026-10-28T08:00:00", "order": 1,
                 "estimatedLoadBunches": 146, "vehicleId": "V-3", "meetsWindow": True, "reason": None},
        "supplies": [{"name": name, "date": "2026-10-28", "requiredQuantity": qty,
                      "availableQuantity": 500, "deficit": 0} for name, qty in (("cajas", 100), ("carton", 200), ("bolsas", 300))],
        "metrics": {"windowCompliance": 1, "capacityUse": 0.5},
        "aforamientoId": str(aforamiento_id), "cycleId": parcel["cycle"]["id"],
    }
    with session_factory() as db:
        db.add(Aforamiento(id=aforamiento_id, parcel_id=uuid.UUID(parcel["id"]), date=date(2026, 10, 26),
                          area_ha=Decimal("2.4"), estimated_plants=2000, confidence=Decimal("0.5")))
        db.flush()
        db.add(Recommendation(id=recommendation_id, parcel_id=uuid.UUID(parcel["id"]), period=payload["period"],
                              source_version="test", generated_at=datetime(2026, 10, 26, tzinfo=timezone.utc),
                              payload=payload, aforamiento_id=aforamiento_id, cycle_id=uuid.UUID(parcel["cycle"]["id"])))
        db.commit()
    return payload


@pytest.fixture()
def inventories(client):
    result = {}
    for name in ("cajas", "carton", "bolsas"):
        inventory = {"id": new_uuid(), "initialQuantity": 500,
                     "location": {"id": new_uuid(), "name": "Bodega"},
                     "supply": {"id": new_uuid(), "name": name, "category": "Empaque", "unit": "unidad"}}
        body = {"id": new_uuid(), "type": "ENTRADA", "quantity": 1, "date": "2026-10-26", "observation": "",
                "createdAt": "2026-10-26T12:00:00Z", "updatedAt": "2026-10-26T12:00:00Z", "inventory": inventory}
        assert client.post("/api/v1/movements", json=body).status_code == 201
        result[name] = inventory
    return result


def harvest(plan, quantity=100, **changes):
    result = {"id": new_uuid(), "parcelId": plan["parcelId"], "cycleId": plan["cycleId"],
              "recommendationId": plan["id"], "aforamientoId": plan["aforamientoId"],
              "date": "2026-10-28", "quantity": quantity, "observation": "Real", "exceptionConfirmed": False,
              "createdAt": "2026-10-28T12:00:00Z"}
    return dict(result, **changes)


def dispatch(plan, inventories, quantity=73, shipped=0, **changes):
    before, after = packaging_targets(plan, shipped), packaging_targets(plan, shipped + quantity)
    consumptions = [{"id": new_uuid(), "type": "CONSUMO", "quantity": float(after[name] - before[name]),
                     "date": "2026-10-28", "observation": "Empaque del despacho", "createdAt": "2026-10-28T13:00:00Z",
                     "updatedAt": "2026-10-28T13:00:00Z", "inventory": inventories[name],
                     "parcelId": plan["parcelId"], "cycleId": plan["cycleId"]} for name in after if after[name] > before[name]]
    result = {"id": new_uuid(), "parcelId": plan["parcelId"], "cycleId": plan["cycleId"], "recommendationId": plan["id"],
              "dispatchedAt": "2026-10-28T08:00:00-05:00", "quantity": quantity, "vehicleId": "V-3", "order": 1,
              "observation": "Enviado", "createdAt": "2026-10-28T13:00:00Z", "consumptions": consumptions}
    return dict(result, **changes)


def count(session_factory, model):
    with session_factory() as db:
        return db.scalar(select(func.count()).select_from(model))


def test_cosecha_real_no_reaplica_confianza_y_no_modifica_aforamiento(client, plan, session_factory):
    body = harvest(plan)
    assert client.post("/api/v1/harvests", json=body).status_code == 201
    assert client.post("/api/v1/harvests", json=body).status_code == 200
    assert count(session_factory, Harvest) == count(session_factory, Activity) == 1
    with session_factory() as db:
        assert db.get(Aforamiento, uuid.UUID(plan["aforamientoId"])).estimated_plants == 2000
    assert client.post("/api/v1/harvests", json=dict(body, quantity=99)).status_code == 409


def test_parciales_respetan_cupo_decimal_y_excepcion_explicita(client, plan):
    assert client.post("/api/v1/harvests", json=harvest(plan, 100)).status_code == 201
    assert client.post("/api/v1/harvests", json=harvest(plan, 45)).status_code == 201
    assert client.post("/api/v1/harvests", json=harvest(plan, 1, createdAt=datetime.now(timezone.utc).isoformat())).status_code == 409
    assert client.post("/api/v1/harvests", json=harvest(plan, 1, exceptionConfirmed=True)).status_code == 201


@pytest.mark.parametrize("quantity", [0, -1, 1.5, "10"])
def test_cosecha_exige_enteros_positivos(client, plan, quantity):
    assert client.post("/api/v1/harvests", json=harvest(plan, quantity)).status_code == 422


def test_repetir_recomendacion_no_crea_otro_cupo(client, plan, session_factory):
    assert client.post("/api/v1/harvests", json=harvest(plan, 100)).status_code == 201
    other = copy.deepcopy(plan)
    other["id"] = new_uuid()
    other["volumeBunches"] = 1000
    with session_factory() as db:
        db.add(Recommendation(id=uuid.UUID(other["id"]), parcel_id=uuid.UUID(other["parcelId"]), period=other["period"],
                              source_version="new", generated_at=datetime.now(timezone.utc), payload=other,
                              aforamiento_id=uuid.UUID(other["aforamientoId"]), cycle_id=uuid.UUID(other["cycleId"])))
        db.commit()
    assert client.post("/api/v1/harvests", json=harvest(other, 100)).status_code == 409
    assert client.post("/api/v1/harvests", json=harvest(plan, 45)).status_code == 201


def test_despachos_parciales_consumen_el_plan_una_sola_vez(client, plan, inventories, session_factory):
    assert client.post("/api/v1/harvests", json=harvest(plan, 146, exceptionConfirmed=True)).status_code == 201
    first = dispatch(plan, inventories)
    assert client.post("/api/v1/dispatches", json=first).status_code == 201
    assert client.post("/api/v1/dispatches", json=first).status_code == 200
    second = dispatch(plan, inventories, shipped=73)
    assert client.post("/api/v1/dispatches", json=second).status_code == 201
    with session_factory() as db:
        for name, required in (("cajas", 100), ("carton", 200), ("bolsas", 300)):
            assert db.get(Inventory, uuid.UUID(inventories[name]["id"])).quantity == 501 - required
        assert db.scalar(select(func.sum(Dispatch.quantity))) == 146
        assert db.scalar(select(func.count()).select_from(Movement).where(Movement.dispatch_id.is_not(None))) == 6
    assert client.post("/api/v1/dispatches", json=dispatch(plan, inventories, 1, shipped=146)).status_code == 409


def test_faltante_revierte_despacho_y_todos_los_empaques(client, plan, inventories, session_factory):
    assert client.post("/api/v1/harvests", json=harvest(plan)).status_code == 201
    with session_factory() as db:
        db.get(Inventory, uuid.UUID(inventories["bolsas"]["id"])).quantity = 0
        db.commit()
    body = dispatch(plan, inventories)
    reply = client.post("/api/v1/dispatches", json=body)
    assert reply.status_code == 409 and "entrada" in reply.json()["detail"]
    assert count(session_factory, Dispatch) == 0
    with session_factory() as db:
        assert db.get(Inventory, uuid.UUID(inventories["cajas"]["id"])).quantity == 501
        assert db.scalar(select(func.count()).select_from(Movement).where(Movement.dispatch_id.is_not(None))) == 0


def test_no_se_aceptan_cantidades_de_empaque_manipuladas(client, plan, inventories):
    assert client.post("/api/v1/harvests", json=harvest(plan)).status_code == 201
    body = dispatch(plan, inventories)
    body["consumptions"][0]["quantity"] = 1
    assert client.post("/api/v1/dispatches", json=body).status_code == 409


def test_no_despacha_sin_cosecha_ni_antes_de_la_fecha_de_cosecha(client, plan, inventories):
    assert client.post("/api/v1/dispatches", json=dispatch(plan, inventories)).status_code == 409
    assert client.post("/api/v1/harvests", json=harvest(plan)).status_code == 201
    assert client.post("/api/v1/dispatches", json=dispatch(plan, inventories, dispatchedAt="2026-10-27T08:00:00-05:00")).status_code == 409


def test_historial_incluye_fuentes_y_aisla_productores(client, plan, identity):
    body = harvest(plan)
    assert client.post("/api/v1/harvests", json=body).status_code == 201
    result = client.get(f"/api/v1/production/{plan['parcelId']}")
    assert result.status_code == 200
    assert result.json()["harvests"][0]["id"] == body["id"]
    assert result.json()["recommendations"][0]["volumeBunches"] == 145.6
    from app.core.security import FirebaseIdentity
    identity["value"] = FirebaseIdentity(uid="uid-luis", email="luis@example.com")
    assert client.post("/api/v1/users/register", json={"name": "Luis", "document": "2"}).status_code == 201
    assert client.get(f"/api/v1/production/{plan['parcelId']}").status_code == 404
    assert client.post("/api/v1/harvests", json=body).status_code == 409


def test_descanso_permite_despachar_producto_del_ciclo_cerrado(client, plan, inventories):
    assert client.post("/api/v1/harvests", json=harvest(plan, date="2026-10-27")).status_code == 201
    assert client.post(f"/api/v1/parcels/{plan['parcelId']}/cycle/finish", json={"endDate": "2026-10-28"}).status_code == 200
    assert client.post("/api/v1/harvests", json=harvest(plan, 1, createdAt=datetime.now(timezone.utc).isoformat())).status_code == 409
    assert client.post("/api/v1/dispatches", json=dispatch(plan, inventories)).status_code == 201


def test_sincronizar_cosecha_offline_despues_del_cierre_respeta_su_fecha_real(client, plan):
    assert client.post(f"/api/v1/parcels/{plan['parcelId']}/cycle/finish", json={"endDate": "2026-10-28"}).status_code == 200
    assert client.post("/api/v1/harvests", json=harvest(plan, date="2026-10-27")).status_code == 201


def test_cierre_posterior_en_el_mismo_dia_no_invalida_una_cosecha_offline(client, plan):
    body = harvest(plan, date=datetime.now(BOGOTA).date().isoformat(), createdAt=datetime.now(timezone.utc).isoformat())
    assert client.post(f"/api/v1/parcels/{plan['parcelId']}/cycle/finish", json={"endDate": body["date"]}).status_code == 200
    assert client.post("/api/v1/harvests", json=body).status_code == 201


def test_siembra_solo_en_preparacion_y_cosecha_generica_no_crea_saldo(client, parcel):
    from tests.test_actividades import activity
    body = activity(parcel, type="SIEMBRA")
    assert client.post("/api/v1/activities", json=body).status_code == 409
    response = client.patch(f"/api/v1/parcels/{parcel['id']}/cycle/stage", json={"status": "EN_PREPARACION", "effectiveDate": "2026-10-20"})
    assert response.status_code == 200
    assert client.post("/api/v1/activities", json=body).status_code == 201
    assert client.post("/api/v1/activities", json=activity(parcel, type="COSECHA")).status_code == 409


def test_peticiones_incompatibles_no_crean_cosechas(client, plan, session_factory):
    for field in ("cycleId", "recommendationId", "aforamientoId"):
        assert client.post("/api/v1/harvests", json=harvest(plan, **{field: new_uuid()})).status_code == 409
    assert count(session_factory, Harvest) == count(session_factory, Activity) == 0


def test_fracciones_de_empaque_se_redondean_acumuladas_sin_perder_centesimas():
    payload = {"plan": {"estimatedLoadBunches": 3}, "supplies": [{"name": "cajas", "requiredQuantity": 1}]}
    assert [packaging_targets(payload, count)["cajas"] for count in (0, 1, 2, 3, 4)] == [Decimal("0.00"), Decimal("0.33"), Decimal("0.67"), Decimal("1.00"), Decimal("1.00")]
