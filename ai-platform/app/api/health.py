import logging
from fastapi import APIRouter, Depends
from app.models.registry import get_registry

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("")
async def health_check():
    registry = get_registry()
    models = registry.health_report()
    all_ready = all(
        info.get("ready", False) for info in models.values()
    )
    return {
        "status": "ok" if all_ready else "degraded",
        "models": models,
    }
