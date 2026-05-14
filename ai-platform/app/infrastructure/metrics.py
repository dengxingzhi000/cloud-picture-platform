from fastapi import FastAPI
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST
from starlette.responses import Response
from starlette.middleware.base import BaseHTTPMiddleware
import time
import logging

logger = logging.getLogger(__name__)

ai_requests_total = Counter(
    "ai_requests_total",
    "Total AI platform requests",
    ["service", "status"],
)

ai_request_duration_seconds = Histogram(
    "ai_request_duration_seconds",
    "AI request duration in seconds",
    ["service"],
    buckets=(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0),
)

ai_models_loaded = Gauge(
    "ai_models_loaded",
    "Number of AI models currently loaded",
    ["model_name"],
)

ai_queue_messages = Gauge(
    "ai_queue_messages",
    "Number of messages in AI queues",
    ["queue"],
)


class MetricsMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        start = time.time()
        response = await call_next(request)
        duration = time.time() - start
        service = request.url.path.split("/")[3] if len(request.url.path.split("/")) > 3 else "unknown"
        status = "success" if response.status_code < 400 else "error"
        ai_requests_total.labels(service=service, status=status).inc()
        ai_request_duration_seconds.labels(service=service).observe(duration)
        return response


def setup_metrics(app: FastAPI):
    app.add_middleware(MetricsMiddleware)

    @app.get("/metrics")
    async def metrics():
        return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)

    logger.info("Prometheus metrics configured")
