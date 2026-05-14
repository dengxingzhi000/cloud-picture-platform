from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.models.registry import init_registry
from app.config import get_config
from app.api.v1 import tagging, moderation, embedding, assistant
from app.api import health
from app.workers.tagging_worker import TaggingWorker
from app.workers.moderation_worker import ModerationWorker
from app.infrastructure.metrics import setup_metrics
import asyncio
import logging

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    config = get_config()
    registry = init_registry(config)
    logger.info("Model registry initialized")

    tagging_worker = TaggingWorker()
    moderation_worker = ModerationWorker()
    tasks = [
        asyncio.create_task(tagging_worker.start()),
        asyncio.create_task(moderation_worker.start()),
    ]
    setup_metrics(app)
    logger.info("AI Platform started")
    yield
    for task in tasks:
        task.cancel()
    logger.info("AI Platform shutdown")

app = FastAPI(title="Cloud Picture AI Platform", version="1.0.0", lifespan=lifespan)
app.include_router(tagging.router, prefix="/api/v1/tagging", tags=["Tagging"])
app.include_router(moderation.router, prefix="/api/v1/moderation", tags=["Moderation"])
app.include_router(embedding.router, prefix="/api/v1/embedding", tags=["Embedding"])
app.include_router(assistant.router, prefix="/api/v1/assistant", tags=["Assistant"])
app.include_router(health.router, prefix="/health", tags=["Health"])
