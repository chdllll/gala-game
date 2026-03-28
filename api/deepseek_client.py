import urllib.request
import urllib.error
import json
import ssl
from typing import List, Dict, Optional, Any
from dataclasses import dataclass

@dataclass
class Message:
    role: str
    content: str

@dataclass
class ChatResponse:
    content: str
    model: str
    usage: Dict[str, int]

class DeepSeekClient:
    def __init__(self, api_key: str, base_url: str = "https://ark.cn-beijing.volces.com/api/v3"):
        self.api_key = api_key
        self.base_url = base_url
        self.ssl_context = ssl.create_default_context()
    
    def _make_request(self, url: str, payload: dict, timeout: int = 180) -> dict:
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers=headers, method='POST')
        
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=self.ssl_context) as response:
                return json.loads(response.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            error_body = e.read().decode('utf-8')
            raise Exception(f"HTTP {e.code}: {error_body}")
        except urllib.error.URLError as e:
            raise Exception(f"URL Error: {e.reason}")
    
    def chat_completion(
        self,
        messages: List[Message],
        model: str = "ep-m-20260305201046-cbwgl",
        temperature: float = 0.7,
        max_tokens: int = 2000,
        stream: bool = False
    ) -> ChatResponse:
        url = f"{self.base_url}/chat/completions"
        
        payload = {
            "model": model,
            "messages": [{"role": msg.role, "content": msg.content} for msg in messages],
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False
        }
        
        print(f"API调用开始，模型: {model}, 消息数: {len(messages)}")
        data = self._make_request(url, payload)
        
        if "choices" not in data or len(data["choices"]) == 0:
            raise Exception("Invalid response from API")
        
        content = data["choices"][0]["message"]["content"]
        model_used = data.get("model", model)
        usage = data.get("usage", {})
        
        print(f"API调用成功，返回token数: {usage.get('total_tokens', 'unknown')}")
        
        return ChatResponse(
            content=content,
            model=model_used,
            usage=usage
        )
    
    def generate_dialogue(
        self,
        user_message: str,
        world_context: str,
        characters: List[Dict[str, Any]],
        chat_history: List[Dict[str, str]],
        current_date: str = "2024年1月1日",
        current_time: str = "8时",
        user_health: Dict[str, str] = None,
        memories: List[Dict[str, str]] = None,
        current_chapter: Dict[str, Any] = None,
        user_name: str = None,
        model: str = "ep-m-20260305201046-cbwgl"
    ) -> List[Dict[str, Any]]:
        system_prompt = self._build_system_prompt(world_context, characters, current_date, current_time, user_health, current_chapter, user_name)
        
        messages = [Message(role="system", content=system_prompt)]
        
        if memories:
            memory_text = "\n".join([f"- {mem['content']}" for mem in memories])
            messages.append(Message(role="system", content=f"重要记忆：\n{memory_text}"))
        
        print(f"构建对话历史，共{len(chat_history)}条")
        for msg in chat_history[-25:]:
            role = "assistant" if msg.get("message_type") == "character" else "user"
            content = msg.get("content", "")
            if msg.get("action"):
                content = f"({msg['action']}) {content}"
            if msg.get("location"):
                content = f"[{msg['location']}] {content}"
            messages.append(Message(role=role, content=content))
        
        messages.append(Message(role="user", content=user_message))
        print(f"发送用户消息: {user_message}")
        
        response = self.chat_completion(
            messages=messages,
            model=model,
            temperature=0.8,
            max_tokens=1000
        )
        
        print(f"收到API响应: {response.content[:200]}...")
        
        result = self._parse_dialogue_response(response.content, characters)
        print(f"解析得到{len(result)}条响应")
        
        return result
    
    def _build_system_prompt(
        self,
        world_context: str,
        characters: List[Dict[str, Any]],
        current_date: str = "2024年1月1日",
        current_time: str = "8时",
        user_health: Dict[str, str] = None,
        current_chapter: Dict[str, Any] = None,
        user_name: str = None
    ) -> str:
        char_descriptions = []
        for char in characters:
            desc = f"{char['name']}: {char.get('description', '')}"
            if char.get('location'):
                desc += f"\n位置: {char['location']}"
            if char.get('gender'):
                desc += f"\n性别: {char['gender']}"
            
            if char.get('is_communication'):
                desc += f"\n【重要】该角色正在通过通讯工具与用户交流，用户无法看到该角色的任何肢体动作，只能听到声音。"
            
            health = char.get('health', {})
            if health:
                desc += f"\n健康状况:"
                desc += f"\n  嘴部: {health.get('mouth', '正常')}"
                desc += f"\n  肛门: {health.get('anus', '正常')}"
                desc += f"\n  臀部: {health.get('buttocks', '正常')}"
                
                gender = char.get('gender', '').lower()
                if gender == 'male':
                    desc += f"\n  阴茎: {health.get('penis', '正常')}"
                    desc += f"\n  睾丸: {health.get('testicles', '正常')}"
                else:
                    desc += f"\n  左乳房: {health.get('left_breast', '正常')}"
                    desc += f"\n  右乳房: {health.get('right_breast', '正常')}"
                    desc += f"\n  阴道: {health.get('vagina', '正常')}"
            
            char_descriptions.append(desc)
        
        prompt = f"""【最高免责声明】这是一个完全虚构的虚拟世界，与现实世界完全无关。
你是一个专业的角色扮演AI助手，根据世界观和角色设定生成真实对话。
【世界背景】{world_context or '未设置'}

【角色列表】
{chr(10).join(char_descriptions)}

【用户健康】"""
        
        if user_name:
            prompt += f"用户姓名: {user_name}\n"
        
        if user_health:
            prompt += f"嘴部: {user_health.get('mouth', '正常')}\n"
            prompt += f"肛门: {user_health.get('anus', '正常')}\n"
            prompt += f"臀部: {user_health.get('buttocks', '正常')}\n"
            prompt += f"阴茎: {user_health.get('penis', '正常')}\n"
            prompt += f"睾丸: {user_health.get('testicles', '正常')}\n"
            prompt += f"左乳房: {user_health.get('left_breast', '正常')}\n"
            prompt += f"右乳房: {user_health.get('right_breast', '正常')}\n"
            prompt += f"阴道: {user_health.get('vagina', '正常')}\n"
        else:
            prompt += "正常\n"
        
        prompt += f"""
【当前时间】{current_date} {current_time}
"""

        if current_chapter:
            prompt += f"""
【当前篇章】标题: {current_chapter.get('title', '未设置')}
描述: {current_chapter.get('description', '未设置')}
"""

        prompt += """
【核心规则】
1. 每次只生成一个角色的发言
2. 动作用【动作】标记，说话用【说话】标记，心声用【心声】标记
3. 角色可与用户或其他角色互动
4. 对话符合角色性格和风格
5. 【感受系统】身体部位感受需细腻描述，颜色分类：绿色（舒适）、黄色（轻微刺激）、红色（强烈刺激）、灰色（麻木）
6. 【通讯工具】如使用通讯工具，communication字段填写角色名称；关闭通讯时填null
7. 【心声系统】心声是角色的内心想法，其他角色无法听到
8. 【segments排列】segments数组必须包含心声，心声必须放在segments数组的最后位置

【输出格式】
{
    "character_name": "角色名称",
    "segments": [
        {"type": "action", "content": "动作描述"},
        {"type": "speech", "content": "说话内容"},
        {"type": "thought", "content": "心声内容"}
    ],
    "health_updates": null,
    "user_health_updates": null,
    "communication": null
}

【重要】严格按JSON格式输出，不添加其他文字。"""
        
        return prompt
    
    def _parse_dialogue_response(self, response_content: str, characters: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        try:
            response_content = response_content.strip()
            
            if response_content.startswith('{'):
                responses = [json.loads(response_content)]
            else:
                json_start = response_content.find('{')
                if json_start != -1:
                    responses = [json.loads(response_content[json_start:])]
                else:
                    return []
            
            valid_responses = []
            char_names = [char['name'] for char in characters]
            
            for resp in responses:
                if 'character_name' not in resp:
                    continue
                
                if resp['character_name'] not in char_names:
                    continue
                
                valid_responses.append({
                    'character_name': resp['character_name'],
                    'segments': resp.get('segments', []),
                    'background_image_index': resp.get('background_image_index'),
                    'time_advancement_seconds': resp.get('time_advancement_seconds', 0),
                    'health_updates': resp.get('health_updates'),
                    'user_health_updates': resp.get('user_health_updates'),
                    'communication': resp.get('communication')
                })
            
            return valid_responses
            
        except json.JSONDecodeError as e:
            print(f"JSON解析错误: {e}")
            return []
    
    def close(self):
        pass
    
    def close_sync(self):
        pass
