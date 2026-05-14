from pydantic import BaseModel
from typing import Optional


class EmbeddingTextRequest(BaseModel):
    text: str
    model: str = "clip"


class EmbeddingImageRequest(BaseModel):
    image_url: str
    model: str = "clip"


class EmbeddingResponse(BaseModel):
    vector: list[float]
    dimension: int
    model: str
    cached: bool = False
