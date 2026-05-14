import os
import logging
from fastapi import Header, HTTPException, status

logger = logging.getLogger(__name__)

API_KEY = os.getenv("AI_API_KEY", "")


async def verify_api_key(x_api_key: str = Header(default="")) -> str:
    if not API_KEY:
        return ""
    if x_api_key != API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing API key",
        )
    return x_api_key
