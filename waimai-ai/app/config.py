"""AI 服务配置：读取环境变量。"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # LLM 提供商：deepseek / dashscope
    llm_provider: str = "deepseek"
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    dashscope_api_key: str = ""
    # 本地 Ollama 降级
    ollama_base_url: str = "http://host.docker.internal:11434"
    ollama_model: str = "qwen2.5:7b-instruct"
    # 内部鉴权（与后端一致）
    internal_token: str = "internal-secret"
    # 超时（秒）
    cloud_timeout: float = 15.0
    ollama_timeout: float = 30.0

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
