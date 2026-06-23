"""配置:全部走环境变量,默认 Mock 不外联。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="SELLM_", env_file=".env")

    # LLM
    ai_provider: str = "mock"  # mock | openai
    ai_base_url: str = ""
    ai_api_key: str = ""
    ai_model: str = "gpt-4o-mini"
    ai_timeout: int = 60

    # 文生媒体(可切换,默认 mock 不外联)
    media_provider: str = "mock"  # mock | openai
    media_base_url: str = ""
    media_api_key: str = ""
    media_model: str = "dall-e-3"
    media_timeout: int = 120

    # 文生视频(可切换,默认 mock 不外联;真实视频生成通常异步:提交→轮询→下载)
    video_provider: str = "mock"  # mock | openai
    video_base_url: str = ""
    video_api_key: str = ""
    video_model: str = "sora-1"
    video_timeout: int = 300       # 单次 HTTP 超时
    video_poll_interval: int = 5   # 轮询间隔秒
    video_max_polls: int = 60      # 最大轮询次数(× interval = 最长等待)

    # Milvus
    milvus_host: str = "localhost"
    milvus_port: int = 19530

    # Java 业务层 gRPC/REST
    assessment_service_url: str = "http://localhost:8080"
    teaching_service_url: str = "http://localhost:8081"


settings = Settings()
