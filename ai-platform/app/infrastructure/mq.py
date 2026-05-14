import logging
from typing import Optional, Callable, Awaitable
import aio_pika
from aio_pika.abc import AbstractIncomingMessage

logger = logging.getLogger(__name__)


class MqConnectionManager:
    def __init__(self, url: str):
        self._url = url
        self._connection: Optional[aio_pika.Connection] = None
        self._channel: Optional[aio_pika.Channel] = None

    async def connect(self):
        if self._connection is None or self._connection.is_closed:
            self._connection = await aio_pika.connect_robust(self._url)
            self._channel = await self._connection.channel()
            await self._channel.declare_queue("ai.tagging.submit", durable=True)
            await self._channel.declare_queue("ai.tagging.result", durable=True)
            await self._channel.declare_queue("ai.moderation.submit", durable=True)
            await self._channel.declare_queue("ai.moderation.result", durable=True)
            logger.info("Connected to RabbitMQ")

    async def close(self):
        if self._channel and not self._channel.is_closed:
            await self._channel.close()
        if self._connection and not self._connection.is_closed:
            await self._connection.close()
        logger.info("Disconnected from RabbitMQ")

    async def publish(self, queue_name: str, message: dict):
        await self.connect()
        await self._channel.default_exchange.publish(
            aio_pika.Message(body=message.encode() if isinstance(message, str) else message),
            routing_key=queue_name,
        )

    async def publish_json(self, queue_name: str, data: dict):
        import json
        await self.connect()
        await self._channel.default_exchange.publish(
            aio_pika.Message(
                body=json.dumps(data).encode(),
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            ),
            routing_key=queue_name,
        )

    async def consume(self, queue_name: str, callback: Callable[[AbstractIncomingMessage], Awaitable[None]]):
        await self.connect()
        queue = await self._channel.get_queue(queue_name)
        await queue.consume(callback)
        logger.info("Consuming from %s", queue_name)


_mq: Optional[MqConnectionManager] = None


def get_mq() -> MqConnectionManager:
    if _mq is None:
        raise RuntimeError("MQ not initialized")
    return _mq


def init_mq(url: str) -> MqConnectionManager:
    global _mq
    _mq = MqConnectionManager(url)
    return _mq
