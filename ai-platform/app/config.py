from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672"
    redis_url: str = "redis://localhost:6379"
    clip_model_name: str = "ViT-L-14"
    clip_pretrained: str = "openai"
    moderation_provider: str = "aliyun"
    qwen_vl_enabled: bool = False
    qwen_vl_path: str = ""
    aliyun_ak: str = ""
    aliyun_sk: str = ""

    class Config:
        env_file = ".env"

def get_config() -> Settings:
    return Settings()
