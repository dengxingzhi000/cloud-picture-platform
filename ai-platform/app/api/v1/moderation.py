import logging
from fastapi import APIRouter, HTTPException, Depends
from app.schemas.moderation import (
    ModerationTextRequest,
    ModerationImageRequest,
    ModerationResponse,
)
from app.schemas.common import ApiResponse
from app.services.moderation_service import moderation_service
from app.api.v1.deps import verify_api_key

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(verify_api_key)])


@router.post("/text", response_model=ApiResponse)
async def moderate_text(req: ModerationTextRequest):
    try:
        result, cached = await moderation_service.moderate_text(
            text=req.text, provider=req.provider
        )
        return ApiResponse(data=ModerationResponse(
            safe=result["safe"],
            suggestion=result.get("suggestion", "pass"),
            provider=result.get("provider", "unknown"),
            cached=cached,
        ))
    except Exception as e:
        logger.exception("Text moderation failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/image", response_model=ApiResponse)
async def moderate_image(req: ModerationImageRequest):
    try:
        result, cached = await moderation_service.moderate_image(
            image_url=req.image_url, provider=req.provider
        )
        return ApiResponse(data=ModerationResponse(
            safe=result["safe"],
            suggestion=result.get("suggestion", "pass"),
            provider=result.get("provider", "unknown"),
            cached=cached,
        ))
    except Exception as e:
        logger.exception("Image moderation failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health():
    return {"status": "ok", "service": "moderation"}
