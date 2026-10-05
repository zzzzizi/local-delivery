from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError, OperationalError
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import DeliveryRequest, User
from app.schemas.delivery_request import DeliveryRequestCreate, DeliveryRequestRead

router = APIRouter(prefix="/requests", tags=["requests"])


@router.post(
    "",
    response_model=DeliveryRequestRead,
    status_code=status.HTTP_201_CREATED,
    responses={
        404: {"description": "Customer not found"},
        409: {"description": "Database constraint conflict"},
        503: {"description": "Database temporarily unavailable"},
    },
)
def create_request(
    payload: DeliveryRequestCreate,
    session: Annotated[Session, Depends(get_db)],
) -> DeliveryRequestRead:
    try:
        with session.begin():
            if session.get(User, payload.customer_id) is None:
                raise HTTPException(status_code=404, detail="Customer not found.")

            request = DeliveryRequest(**payload.model_dump())
            session.add(request)
            session.flush()
            # Read generated IDs/defaults before committing; avoid a second query
            # after the save has succeeded.
            result = DeliveryRequestRead.model_validate(request)
    except IntegrityError as error:
        raise HTTPException(
            status_code=409,
            detail="Request could not be saved because related data changed.",
        ) from error
    except OperationalError as error:
        raise HTTPException(
            status_code=503, detail="Database temporarily unavailable. Try again later."
        ) from error

    return result
