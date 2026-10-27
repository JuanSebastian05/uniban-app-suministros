"""Recomendaciones del Equipo 2 (HU_12): armar la solicitud, llamar a su servicio y guardar lo que responde.

Reglas:
- La app nunca habla con el Equipo 2 (RT_11): lo hace este backend, con el token de la app ya validado (RT_13) y solo
  sobre parcelas del productor autenticado.
- **Sin aforamiento no se pide recomendación.** El servicio del Equipo 2 no exige ningún campo (todos tienen valores
  por defecto de demostración: 2,4 ha, confianza 0,87…), así que enviar la solicitud sin aforamiento devolvería una
  recomendación calculada con datos inventados. Se responde 409 ``NO_AFORAMIENTO`` con un mensaje claro.
- Un fallo del Equipo 2 nunca tumba nuestro backend: sin respuesta (tiempo agotado, red, 5xx) → 503; respuesta que no
  cumple su contrato o solicitud rechazada → 502. Su servicio también está en Render y tarda ≈ 1 min en despertar, por
  eso hay 90 s de espera y un reintento.
- Cada recomendación exitosa se guarda (solo inserción); ``GET`` sirve la última sin volver a llamarlo.
"""

import logging
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Callable

import httpx
from pydantic import ValidationError
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.models import Aforamiento, Inventory, Location, Movement, Parcel, ProductionCycle, Recommendation, Supply
from app.schemas.recommendations import (
    Equipo2Respuesta,
    RecommendationMetricsOut,
    RecommendationOut,
    RecommendationPlanOut,
    RecommendationSupplyOut,
)
from app.services.aforamientos import ParcelNotFound
from app.services.inventory_query import list_inventory

logger = logging.getLogger(__name__)

RECOMMEND_PATH = "/api/v1/recomendar"
HEALTH_PATH = "/salud"  # su endpoint de salud: sirve para despertarlo antes de pedir la recomendación

# Ventana de consumos y pérdidas que se envían al Equipo 2 (supuesto, ver docs/contrato-equipo2.md).
RECENT_DAYS = 90


class RecommendationError(Exception):
    """Error controlado: ``status_code`` HTTP, ``code`` para que la app elija el mensaje y ``message`` en español."""

    status_code = 500
    code = "RECOMMENDATION_ERROR"

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class NoAforamiento(RecommendationError):
    status_code = 409
    code = "NO_AFORAMIENTO"


class Equipo2Unavailable(RecommendationError):
    status_code = 503
    code = "EQUIPO2_UNAVAILABLE"


class Equipo2BadResponse(RecommendationError):
    status_code = 502
    code = "EQUIPO2_BAD_RESPONSE"


class NoRecommendation(RecommendationError):
    status_code = 404
    code = "NO_RECOMMENDATION"


class Equipo2Client:
    """Cliente HTTP del servicio de logística. Se sustituye en las pruebas: nunca se llama al servicio real."""

    def __init__(
        self,
        base_url: str,
        api_key: str | None = None,
        timeout: float = 90.0,
        retries: int = 1,
        retry_delay: float = 5.0,
        transport: httpx.BaseTransport | None = None,
        sleep: Callable[[float], None] = time.sleep,
        warm_up: bool = True,
        warm_up_timeout: float = 60.0,
    ):
        self._url = base_url.rstrip("/") + RECOMMEND_PATH
        self._health_url = base_url.rstrip("/") + HEALTH_PATH
        self._warm_up = warm_up
        self._warm_up_timeout = warm_up_timeout
        self._headers = {"Content-Type": "application/json"}
        if api_key:
            # Su OpenAPI no declara autenticación; si algún día la piden, la clave va por variable de entorno.
            self._headers["X-API-Key"] = api_key
        self._timeout = timeout
        self._attempts = retries + 1
        self._retry_delay = retry_delay
        self._transport = transport
        self._sleep = sleep

    def _wake_up(self) -> None:
        """Llamada de calentamiento: despierta su servicio (Render) para que el POST no gaste en ello su espera.

        Es de mejor esfuerzo: cualquier resultado, incluso un fallo, se ignora; el POST decide si hay servicio.
        """
        try:
            with httpx.Client(timeout=self._warm_up_timeout, transport=self._transport) as http:
                http.get(self._health_url)
        except httpx.HTTPError:
            logger.info("El calentamiento del Equipo 2 no obtuvo respuesta; se intenta igual la recomendación.")

    def recommend(self, body: dict) -> dict:
        """Calienta y hace POST con un reintento. Devuelve el JSON; lanza ``Equipo2Unavailable`` o ``Equipo2BadResponse``."""
        if self._warm_up:
            self._wake_up()
        last_problem = "sin respuesta"
        for attempt in range(1, self._attempts + 1):
            try:
                with httpx.Client(timeout=self._timeout, transport=self._transport) as http:
                    response = http.post(self._url, json=body, headers=self._headers)
            except httpx.TimeoutException:
                last_problem = "tiempo de espera agotado"
            except httpx.TransportError as error:
                last_problem = f"error de red ({type(error).__name__})"
            else:
                if response.status_code >= 500:
                    last_problem = f"respondió {response.status_code}"
                elif response.status_code >= 400:
                    # Nuestra solicitud no le gustó: reintentar igual no ayuda. No se registra el cuerpo enviado.
                    logger.warning("Equipo 2 rechazó la solicitud con %s", response.status_code)
                    raise Equipo2BadResponse("El servicio de recomendaciones rechazó la solicitud.")
                else:
                    try:
                        data = response.json()
                    except ValueError:
                        raise Equipo2BadResponse(
                            "El servicio de recomendaciones respondió algo que no se puede leer."
                        ) from None
                    if not isinstance(data, dict):
                        raise Equipo2BadResponse("El servicio de recomendaciones respondió algo inesperado.")
                    return data
            logger.warning("Equipo 2 no disponible (intento %s de %s): %s", attempt, self._attempts, last_problem)
            if attempt < self._attempts:
                self._sleep(self._retry_delay)
        raise Equipo2Unavailable(
            "El servicio de recomendaciones no está disponible en este momento. Intenta de nuevo en unos minutos."
        )


def get_equipo2_client() -> Equipo2Client:
    """Dependencia de FastAPI: el cliente según la configuración (las pruebas la sustituyen)."""
    settings = get_settings()
    return Equipo2Client(settings.equipo2_base_url, settings.equipo2_api_key)


# --- Armado de la solicitud ---

def iso_period(value: date) -> str:
    """Semana ISO del aforamiento, como «2026-W44» (formato de ``contrato-equipo2.md``)."""
    iso = value.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


def _number(value) -> float:
    return float(value)


def _owned_parcel(db: Session, parcel_id: uuid.UUID, producer_id: uuid.UUID) -> Parcel:
    parcel = db.get(Parcel, parcel_id)
    if parcel is None or parcel.producer_id != producer_id:
        # Mismo error para «no existe» y «es de otro productor»: no se revela la existencia de parcelas ajenas.
        raise ParcelNotFound("La parcela no existe.")
    return parcel


def _latest_aforamiento_row(db: Session, parcel_id: uuid.UUID) -> Aforamiento | None:
    return db.scalar(
        select(Aforamiento).where(Aforamiento.parcel_id == parcel_id)
        .order_by(Aforamiento.date.desc(), Aforamiento.received_at.desc(), Aforamiento.id).limit(1)
    )


def _movements(db: Session, producer_id: uuid.UUID, movement_type: str, since: date, parcel_id: uuid.UUID | None):
    query = (
        select(Movement, Supply)
        .join(Inventory, Movement.inventory_id == Inventory.id)
        .join(Location, Inventory.location_id == Location.id)
        .join(Supply, Inventory.supply_id == Supply.id)
        .where(Location.producer_id == producer_id, Movement.type == movement_type, Movement.date >= since)
        .order_by(Movement.date, Movement.id)
    )
    if parcel_id is not None:
        query = query.where(Movement.parcel_id == parcel_id)
    return db.execute(query).all()


def build_request_body(
    db: Session, parcel: Parcel, aforamiento: Aforamiento, producer_id: uuid.UUID, today: date,
) -> dict:
    """Cuerpo de ``contrato-equipo2.md`` (nombres camelCase) más los campos directos del Equipo 2.

    El servicio acepta «formato directo o formato del Equipo 3»: se envían los dos para no depender de cómo mapee los
    objetos anidados (su OpenAPI solo dice ``object``). No se envían ``ventana_despacho``, vehículo, capacidad ni tiempo
    de acarreo: son datos operativos de ellos y usan sus valores por defecto.
    """
    cycle = db.scalar(
        select(ProductionCycle).where(ProductionCycle.parcel_id == parcel.id, ProductionCycle.end_date.is_(None))
    )
    period = iso_period(aforamiento.date)
    confidence = None if aforamiento.confidence is None else _number(aforamiento.confidence)
    since = today - timedelta(days=RECENT_DAYS)
    body: dict = {
        "producerId": str(producer_id),
        "parcel": {
            "id": str(parcel.id),
            "name": parcel.name,
            "areaHa": _number(parcel.area_ha),
            "activeCycle": None if cycle is None else {
                "id": str(cycle.id), "startDate": cycle.start_date.isoformat(), "status": cycle.status,
            },
        },
        "aforamiento": {
            "date": aforamiento.date.isoformat(),
            "period": period,
            "estimatedPlants": aforamiento.estimated_plants,
            "confidence": confidence,
        },
        "inventory": [
            {"supplyId": str(item.supply_id), "name": item.name, "category": item.category, "unit": item.unit,
             "quantity": _number(item.quantity)}
            for item in list_inventory(db, producer_id)
        ],
        "consumptions": [
            {"supplyId": str(supply.id), "name": supply.name, "unit": supply.unit,
             "quantity": _number(movement.quantity), "date": movement.date.isoformat()}
            for movement, supply in _movements(db, producer_id, "CONSUMO", since, parcel.id)
        ],
        "losses": [
            {"supplyId": str(supply.id), "name": supply.name, "unit": supply.unit,
             "quantity": _number(movement.quantity), "date": movement.date.isoformat(),
             "reason": movement.loss_reason}
            for movement, supply in _movements(db, producer_id, "PERDIDA", since, None)
        ],
        # Formato directo del Equipo 2.
        "parcela_id": str(parcel.id),
        "periodo": period,
        "num_plantas_estimado": aforamiento.estimated_plants,
        "area_ha": _number(aforamiento.area_ha),
    }
    if confidence is not None:
        body["calidad_confianza"] = confidence  # sin dato, el servicio usa su valor por defecto
    return body


# --- Respuesta ---

def _to_out(recommendation_id: uuid.UUID, parcel_id: uuid.UUID, generated_at: datetime, reply: Equipo2Respuesta):
    plan = reply.item_plan
    return RecommendationOut(
        id=recommendation_id, parcel_id=parcel_id, period=reply.periodo, generated_at=generated_at,
        source_version=reply.version_metodo, volume_bunches=reply.volumen_racimos,
        cut_deadline=reply.fecha_limite_corte, meets_window=reply.cumple_ventana,
        plan=RecommendationPlanOut(
            cut_date=plan.fecha_corte, order=plan.orden, estimated_load_bunches=plan.carga_estimada_racimos,
            meets_window=plan.cumple_ventana, vehicle_id=plan.vehiculo_id, reason=plan.motivo,
        ),
        supplies=[
            RecommendationSupplyOut(
                name=item.insumo, date=item.fecha, required_quantity=item.cantidad_requerida,
                available_quantity=item.inventario_disponible, deficit=item.deficit,
            )
            for item in reply.insumos
        ],
        metrics=RecommendationMetricsOut(
            window_compliance=reply.metricas.cumplimiento_ventana, capacity_use=reply.metricas.uso_capacidad,
        ),
    )


def request_recommendation(
    db: Session, client: Equipo2Client, parcel_id: uuid.UUID, producer_id: uuid.UUID, today: date | None = None,
) -> RecommendationOut:
    """Pide una recomendación nueva al Equipo 2, la guarda y la devuelve."""
    parcel = _owned_parcel(db, parcel_id, producer_id)
    aforamiento = _latest_aforamiento_row(db, parcel_id)
    if aforamiento is None:
        raise NoAforamiento(
            "Esta parcela todavía no tiene aforamiento. Cuando el Equipo 1 lo envíe podrás pedir la recomendación."
        )
    body = build_request_body(db, parcel, aforamiento, producer_id, today or date.today())
    # La llamada puede tardar minutos (el servicio se duerme): se suelta la conexión de la base mientras tanto.
    db.rollback()

    raw = client.recommend(body)
    try:
        reply = Equipo2Respuesta.model_validate(raw)
    except ValidationError:
        logger.warning("Respuesta del Equipo 2 con forma inesperada", exc_info=True)
        raise Equipo2BadResponse("El servicio de recomendaciones respondió con un formato inesperado.") from None

    generated_at = datetime.now(timezone.utc)
    out = _to_out(uuid.uuid4(), parcel_id, generated_at, reply)
    db.add(Recommendation(
        id=out.id, parcel_id=parcel_id, period=out.period, source_version=out.source_version,
        generated_at=generated_at, payload=out.model_dump(mode="json", by_alias=True),
    ))
    db.commit()
    return out


def latest_recommendation(db: Session, parcel_id: uuid.UUID, producer_id: uuid.UUID) -> RecommendationOut:
    """La última recomendación guardada de una parcela del productor; ``NoRecommendation`` si no hay."""
    _owned_parcel(db, parcel_id, producer_id)
    row = db.scalar(
        select(Recommendation).where(Recommendation.parcel_id == parcel_id)
        .order_by(Recommendation.generated_at.desc(), Recommendation.id).limit(1)
    )
    if row is None:
        raise NoRecommendation("Esta parcela todavía no tiene recomendaciones.")
    return RecommendationOut.model_validate(row.payload)
