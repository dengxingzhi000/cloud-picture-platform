from pydantic import BaseModel
from typing import Optional


class TaggingRequest(BaseModel):
    image_url: str
    model: str = "clip"
    top_k: int = 5
    custom_labels: Optional[list[str]] = None


class TagItem(BaseModel):
    label: str
    confidence: float


class TaggingResponse(BaseModel):
    image_url: str
    tags: list[TagItem]
    model: str
    cached: bool = False


class CombinedTaggingResponse(BaseModel):
    image_url: str
    tags: list[TagItem]
    model: str
    cached: bool = False
    description: Optional[str] = None
    detections: Optional[list[str]] = None
