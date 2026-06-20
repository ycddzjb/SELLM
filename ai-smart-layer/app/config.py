"""配置:全部走环境变量,默认 Mock 不外联。"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # LLM
    ai_provider: str = "mock"  # mock | openai
    ai_base_url: str = ""
    ai_api_key: str = ""
    ai_model: str = "gpt-4o-mini"
    ai_timeout: int = 60

    # Milvus
    milvus_host: str = "localhost"
    milvus_port: int = 19530

    # Java 业务层 gRPC/REST
    assessment_service_url: str = "http://localhost:8080"
    teaching_service_url: str = "http://localhost:8081"

    class Config:
        env_prefix = "SELLM_"
        env_file = ".env"


settings = Settings()
