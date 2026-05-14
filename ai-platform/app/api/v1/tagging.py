import logging
from fastapi import APIRouter, HTTPException, Depends
from app.schemas.tagging import TaggingRequest, TaggingResponse, TagItem
from app.schemas.common import ApiResponse
from app.services.tagging_service import tagging_service
from app.api.v1.deps import verify_api_key

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(verify_api_key)])


@router.post("", response_model=ApiResponse)
async def tag_image(req: TaggingRequest):
    try:
        tags, cached = await tagging_service.tag_image(
            image_url=req.image_url,
            top_k=req.top_k,
            custom_labels=req.custom_labels,
        )
        return ApiResponse(data=TaggingResponse(
            image_url=req.image_url,
            tags=[TagItem(**t) for t in tags],
            model=req.model,
            cached=cached,
        ))
    except Exception as e:
        logger.exception("Tagging failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health():
    return {"status": "ok", "service": "tagging"}
