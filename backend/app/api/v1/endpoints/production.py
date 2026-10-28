from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError
from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import User
from app.schemas.production import DispatchIn, HarvestIn, ProductionHistoryOut
from app.schemas.activities import ActivitySyncOut
from app.services.production import ProductionConflict, production_history, sync_dispatch, sync_harvest
from app.services.movement_sync import MovementConflict
import uuid

router = APIRouter()


def _receive(db, payload, user, response, action):
    try:
        result = action(db, payload, user.producer_id)
    except (ProductionConflict, MovementConflict) as error:
        raise HTTPException(409, detail=str(error)) from error
    except IntegrityError as error:
        raise HTTPException(409, detail="Conflicto de integridad. Actualiza el avance antes de reintentar.") from error
    response.status_code = 200 if result == "DUPLICATE" else 201
    return {"id": str(payload.id), "status": result}


@router.post("/harvests", status_code=201, response_model=ActivitySyncOut,
             responses={200: {"description": "Cosecha ya recibida, idéntica."}, 409: {"description": "Conflicto de cupo, etapa, contexto o UUID."}})
def harvest(payload: HarvestIn, response: Response, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _receive(db, payload, user, response, sync_harvest)


@router.post("/dispatches", status_code=201, response_model=ActivitySyncOut,
             responses={200: {"description": "Despacho ya recibido, idéntico."}, 409: {"description": "Faltante de producto o empaques, etapa, contexto o UUID."}})
def dispatch(payload: DispatchIn, response: Response, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _receive(db, payload, user, response, sync_dispatch)


@router.get("/production/{parcel_id}", response_model=ProductionHistoryOut,
            responses={404: {"description": "Parcela inexistente o ajena."}})
def history(parcel_id: uuid.UUID, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    try:
        return production_history(db, parcel_id, user.producer_id)
    except ProductionConflict as error:
        raise HTTPException(404, detail=str(error)) from error
