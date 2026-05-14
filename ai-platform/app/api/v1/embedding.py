import logging
from fastapi import APIRouter, HTTPException, Depends
from app.schemas.embedding import (
    EmbeddingTextRequest,
    EmbeddingImageRequest,
    EmbeddingResponse,
)
from app.schemas.common import ApiResponse
from app.services.embedding_service import embedding_service
from app.api.v1.deps import verify_api_key

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(verify_api_key)])


@router.post("/text", response_model=ApiResponse)
async def embed_text(req: EmbeddingTextRequest):
    try:
        vector, cached = await embedding_service.embed_text(req.text)
        return ApiResponse(data=EmbeddingResponse(
            vector=vector,
            dimension=len(vector),
            model=req.model,
            cached=cached,
        ))
    except Exception as e:
        logger.exception("Text embedding failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/image", response_model=ApiResponse)
async def embed_image(req: EmbeddingImageRequest):
    try:
        vector, cached = await embedding_service.embed_image(req.image_url)
        return ApiResponse(data=EmbeddingResponse(
            vector=vector,
            dimension=len(vector),
            model=req.model,
            cached=cached,
        ))
    except Exception as e:
        logger.exception("Image embedding failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health():
    return {"status": "ok", "service": "embedding"}
