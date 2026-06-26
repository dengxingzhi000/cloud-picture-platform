from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    rabbitmq_url: str = "amqp://guest:guest@192.168.80.133:5672"
    redis_url: str = "redis://192.168.0.179:6379"
    clip_model_name: str = "ViT-L-14"
    clip_pretrained: str = "openai"
    moderation_provider: str = "deepseek"
    qwen_vl_enabled: bool = False
    qwen_vl_path: str = ""
    aliyun_ak: str = ""
    aliyun_sk: str = ""

    florence_model_name: str = "microsoft/Florence-2-base"
    florence_enabled: bool = True
    yolo_model_name: str = "yolov8n.pt"
    yolo_enabled: bool = True
    yolo_confidence_threshold: float = 0.3

    deepseek_api_key: str = "sk-14808c35406947f2828bfdb51a044cd8"
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-chat"

    java_backend_url: str = "http://localhost:8080"

    class Config:
        env_file = ".env"

def get_config() -> Settings:
    return Settings()
