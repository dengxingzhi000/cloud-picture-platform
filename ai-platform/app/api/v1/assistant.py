import logging
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from typing import Optional
from app.schemas.common import ApiResponse
from app.services.assistant_service import assistant_service
from app.api.v1.deps import verify_api_key

logger = logging.getLogger(__name__)

router = APIRouter(dependencies=[Depends(verify_api_key)])


class ChatRequest(BaseModel):
    sessionId: str = "default"
    message: str
    contextPictureIds: Optional[list[str]] = None


class ChatResponse(BaseModel):
    sessionId: str
    reply: str
    intent: str
    suggestedActions: list[str]


@router.post("/chat", response_model=ApiResponse)
async def chat(req: ChatRequest):
    try:
        result = await assistant_service.chat(
            message=req.message,
            session_id=req.sessionId,
            context_picture_ids=req.contextPictureIds,
        )
        return ApiResponse(data=ChatResponse(
            sessionId=req.sessionId,
            reply=result["reply"],
            intent=result["intent"],
            suggestedActions=result["suggested_actions"],
        ))
    except Exception as e:
        logger.exception("Chat failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/history/{session_id}", response_model=ApiResponse)
async def get_history(session_id: str):
    try:
        history = await assistant_service.get_history(session_id)
        return ApiResponse(data={"sessionId": session_id, "messages": history})
    except Exception as e:
        logger.exception("Get history failed")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/health")
async def health():
    return {"status": "ok", "service": "assistant"}
