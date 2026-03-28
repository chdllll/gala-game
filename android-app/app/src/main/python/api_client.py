import os
import json
import asyncio
import aiohttp
from typing import Optional, List, Dict, Any, AsyncGenerator

class DeepSeekClient:
    def __init__(self, api_key: str = None, base_url: str = None):
        self.api_key = api_key or os.environ.get("DEEPSEEK_API_KEY", "")
        self.base_url = base_url or "https://api.deepseek.com/v1"
        self.model = "deepseek-chat"

    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 2000
    ) -> str:
        if not self.api_key:
            return "错误: 未配置 API Key。请在设置中配置 DeepSeek API Key。"

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    f"{self.base_url}/chat/completions",
                    headers=headers,
                    json=payload,
                    timeout=aiohttp.ClientTimeout(total=60)
                ) as response:
                    if response.status == 200:
                        data = await response.json()
                        return data["choices"][0]["message"]["content"]
                    else:
                        error_text = await response.text()
                        return f"API 错误 ({response.status}): {error_text}"
        except asyncio.TimeoutError:
            return "请求超时，请稍后重试。"
        except Exception as e:
            return f"请求失败: {str(e)}"

    async def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 2000
    ) -> AsyncGenerator[str, None]:
        if not self.api_key:
            yield "错误: 未配置 API Key。"
            return

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": True
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    f"{self.base_url}/chat/completions",
                    headers=headers,
                    json=payload,
                    timeout=aiohttp.ClientTimeout(total=120)
                ) as response:
                    async for line in response.content:
                        line = line.decode("utf-8").strip()
                        if line.startswith("data: "):
                            data = line[6:]
                            if data == "[DONE]":
                                break
                            try:
                                chunk = json.loads(data)
                                if chunk["choices"][0].get("delta", {}).get("content"):
                                    yield chunk["choices"][0]["delta"]["content"]
                            except json.JSONDecodeError:
                                continue
        except Exception as e:
            yield f"\n[错误: {str(e)}]"

_api_client: Optional[DeepSeekClient] = None

def get_api_client() -> DeepSeekClient:
    global _api_client
    if _api_client is None:
        try:
            from db_manager import get_db_manager
            db = get_db_manager()
            config = db.get_api_config()
            if config and config.api1_key:
                _api_client = DeepSeekClient(api_key=config.api1_key)
                if config.api1_model:
                    _api_client.model = config.api1_model
            else:
                _api_client = DeepSeekClient()
        except Exception:
            _api_client = DeepSeekClient()
    return _api_client

def set_api_key(api_key: str, model: str = None):
    global _api_client
    _api_client = DeepSeekClient(api_key=api_key)
    if model:
        _api_client.model = model
