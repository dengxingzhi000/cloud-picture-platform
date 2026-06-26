import logging
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from typing import Optional
from app.schemas.tagging import TaggingRequest, TaggingResponse, CombinedTaggingResponse, TagItem
from app.schemas.common import ApiResponse
from app.services.tagging_service import tagging_service
from app.api.v1.deps import verify_api_key

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(verify_api_key)])


class DescriptionTagRequest(BaseModel):
    description: str
    picture_id: Optional[str] = None


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


@router.post("/combined", response_model=ApiResponse)
async def tag_image_combined(req: TaggingRequest):
    try:
        tags, description, detections, cached = await tagging_service.tag_image_combined(
            image_url=req.image_url,
            top_k=req.top_k,
            custom_labels=req.custom_labels,
        )
        return ApiResponse(data=CombinedTaggingResponse(
            image_url=req.image_url,
            tags=[TagItem(**t) for t in tags],
            model="combined",
            cached=cached,
            description=description,
            detections=detections,
        ))
    except Exception as e:
        logger.exception("Combined tagging failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/description", response_model=ApiResponse)
async def tag_by_description(req: DescriptionTagRequest):
    try:
        tags, cached = await tagging_service.tag_by_description(
            description=req.description,
            picture_id=req.picture_id,
        )
        return ApiResponse(data={
            "picture_id": req.picture_id,
            "tags": [TagItem(**t) for t in tags],
            "model": "deepseek",
            "cached": cached,
        })
    except Exception as e:
        logger.exception("Description tagging failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health():
    return {"status": "ok", "service": "tagging"}
