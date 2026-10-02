"""统一 LLM 客户端：云端(DeepSeek/通义) → 本地 Ollama 两级降级。"""
import json
import httpx

from ..config import settings


class LlmClient:
    """统一调用入口。全部失败时抛 LlmError，由上层返回 degraded 标记。"""

    def __init__(self):
        self._client = httpx.Client(timeout=settings.cloud_timeout)

    def chat(self, messages, json_mode=False, temperature=0.7, max_tokens=500):
        """messages: [{"role": "system"|"user"|"assistant", "content": "..."}]"""
        # 1. 云端
        try:
            return self._cloud_chat(messages, json_mode, temperature, max_tokens)
        except Exception as e:
            cloud_err = str(e)
        # 2. 本地 Ollama
        try:
            return self._ollama_chat(messages, json_mode, temperature, max_tokens)
        except Exception as e:
            raise LlmError(f"cloud={cloud_err}; ollama={e}") from e

    def _cloud_chat(self, messages, json_mode, temperature, max_tokens):
        if settings.llm_provider == "dashscope":
            return self._dashscope_chat(messages, json_mode, temperature, max_tokens)
        return self._deepseek_chat(messages, json_mode, temperature, max_tokens)

    def _deepseek_chat(self, messages, json_mode, temperature, max_tokens):
        url = f"{settings.deepseek_base_url}/chat/completions"
        payload = {
            "model": "deepseek-chat",
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
        }
        if json_mode:
            payload["response_format"] = {"type": "json_object"}
        headers = {
            "Authorization": f"Bearer {settings.deepseek_api_key}",
            "Content-Type": "application/json",
        }
        r = self._client.post(url, json=payload, headers=headers)
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]

    def _dashscope_chat(self, messages, json_mode, temperature, max_tokens):
        url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        payload = {
            "model": "qwen-plus",
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        headers = {
            "Authorization": f"Bearer {settings.dashscope_api_key}",
            "Content-Type": "application/json",
        }
        r = self._client.post(url, json=payload, headers=headers)
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]

    def _ollama_chat(self, messages, json_mode, temperature, max_tokens):
        url = f"{settings.ollama_base_url}/api/chat"
        # Ollama 消息格式转成 prompt 拼接（简化）
        prompt = "\n".join(f"{m['role']}: {m['content']}" for m in messages)
        payload = {
            "model": settings.ollama_model,
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": temperature},
        }
        r = httpx.post(url, json=payload, timeout=settings.ollama_timeout)
        r.raise_for_status()
        return r.json()["message"]["content"]


class LlmError(Exception):
    pass


_llm = LlmClient()


def get_llm() -> LlmClient:
    return _llm
