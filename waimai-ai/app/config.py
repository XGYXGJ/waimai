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
    # 与后端 application.yml 的 waimai.ai.internal-token 必须一致，否则内部接口全部 401
    internal_token: str = "dev-token-123"
    # 超时（秒）
    cloud_timeout: float = 15.0
    ollama_timeout: float = 30.0
    # 模型池来源：MySQL ai_model 表；不可用时回退 data/ai_models.json 与内置默认
    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "waimai123"
    mysql_db: str = "waimai"
    # 模型列表缓存秒数（管理端改动后最多延迟该时间生效）
    ai_model_cache_ttl: int = 30

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
