import json
import logging
from abc import ABC, abstractmethod
from typing import Optional
from aio_pika.abc import AbstractIncomingMessage

from app.infrastructure.mq import init_mq, get_mq, MqConnectionManager

logger = logging.getLogger(__name__)


class BaseWorker(ABC):
    def __init__(self, config=None):
        self._config = config
        self._mq: Optional[MqConnectionManager] = None

    @property
    @abstractmethod
    def queue_name(self) -> str:
        pass

    @abstractmethod
    async def process(self, data: dict) -> Optional[dict]:
        pass

    async def start(self):
        from app.config import get_config

        cfg = get_config()
        self._mq = init_mq(cfg.rabbitmq_url)
        await self._mq.connect()

        async def on_message(message: AbstractIncomingMessage):
            async with message.process():
                try:
                    data = json.loads(message.body.decode())
                    logger.info("Worker %s received: %s", self.__class__.__name__, data)
                    result = await self.process(data)
                    if result and self.result_queue:
                        await self._mq.publish_json(self.result_queue, result)
                except Exception as e:
                    logger.exception("Worker %s processing failed", self.__class__.__name__)

        await self._mq.consume(self.queue_name, on_message)
        logger.info("Worker %s listening on %s", self.__class__.__name__, self.queue_name)
        await self._keep_alive()

    async def _keep_alive(self):
        import asyncio
        while True:
            await asyncio.sleep(3600)

    @property
    def result_queue(self) -> Optional[str]:
        return None
