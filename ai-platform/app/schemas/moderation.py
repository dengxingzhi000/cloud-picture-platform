from pydantic import BaseModel
from typing import Optional


class ModerationTextRequest(BaseModel):
    text: str
    provider: Optional[str] = None


class ModerationImageRequest(BaseModel):
    image_url: str
    provider: Optional[str] = None


class ModerationCategory(BaseModel):
    name: str
    confidence: float


class ModerationResponse(BaseModel):
    safe: bool
    suggestion: str = "pass"
    categories: Optional[list[ModerationCategory]] = None
    provider: str
    cached: bool = False
