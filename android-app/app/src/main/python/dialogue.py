import os
import json
import asyncio
from typing import Optional, List, Dict, Any
from db_manager import DatabaseManager

_db_manager_instance: Optional[DatabaseManager] = None

def get_db_manager() -> DatabaseManager:
    global _db_manager_instance
    if _db_manager_instance is None:
        _db_manager_instance = DatabaseManager()
    return _db_manager_instance

SYSTEM_PROMPT = """你是一个沉浸式角色扮演游戏的 AI 主持人。你的任务是：

1. 创造一个生动、有趣的游戏世界
2. 根据玩家的选择推动剧情发展
3. 描述场景、人物和事件
4. 给玩家提供选择和挑战
5. 记住玩家的决定并影响后续剧情

请用生动的语言描述，让玩家身临其境。每次回复后，给玩家 2-4 个选项供选择。

回复格式：
[场景描述]
...

[选项]
1. ...
2. ...
3. ...
"""

CHARACTER_SYSTEM_PROMPT = """你正在扮演角色 {character_name}。

角色背景：
{background}

角色描述：
{description}

请完全沉浸在角色中，以第一人称回复。保持角色性格和说话方式的一致性。
记住与玩家的互动历史，发展你们之间的关系。
"""

class DialogueManager:
    def __init__(self, world_id: int, character_id: int = None):
        self.world_id = world_id
        self.character_id = character_id
        self.db: DatabaseManager = get_db_manager()
        self.world = self.db.get_world(world_id)
        self.history: List[Dict[str, str]] = []
        self.character = None
        self.session_id: Optional[int] = None
        self._load_character()
        self._init_session()
        self._load_history()

    def _load_character(self):
        if self.character_id:
            self.character = self.db.get_character(self.character_id)

    def _init_session(self):
        sessions = self.db.get_chat_sessions_by_world(self.world_id)
        if sessions:
            self.session_id = sessions[0].id
        else:
            session = self.db.create_chat_session(self.world_id, "默认会话")
            self.session_id = session.id

    def _load_history(self):
        if self.session_id:
            messages = self.db.get_chat_messages_by_session(self.session_id)
            for m in messages:
                if self.character_id:
                    if m.character_id == self.character_id or m.message_type == "user":
                        role = "user" if m.message_type == "user" else "assistant"
                        self.history.append({
                            "role": role,
                            "content": m.content
                        })
                else:
                    if m.message_type in ["user", "narrator"]:
                        role = "user" if m.message_type == "user" else "assistant"
                        self.history.append({
                            "role": role,
                            "content": m.content
                        })

    def get_history(self) -> str:
        result = []
        for h in self.history:
            role = "你" if h["role"] == "user" else (self.character.name if self.character else "AI")
            result.append(f"{role}: {h['content']}")
        return "\n\n".join(result)

    def send_message(self, message: str) -> str:
        self.history.append({"role": "user", "content": message})
        self._save_user_message(message)

        system_prompt = self._get_system_prompt()
        messages = [{"role": "system", "content": system_prompt}]
        messages.extend(self.history[-20:])

        response = self._call_api(messages)

        self.history.append({"role": "assistant", "content": response})
        self._save_response_message(response)

        return response

    def _get_system_prompt(self) -> str:
        if self.character:
            return CHARACTER_SYSTEM_PROMPT.format(
                character_name=self.character.name,
                background=self.character.background or "无",
                description=self.character.description or "无"
            )
        return SYSTEM_PROMPT

    def _call_api(self, messages: List[Dict[str, str]]) -> str:
        try:
            from api_client import get_api_client
            api = get_api_client()
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                response = loop.run_until_complete(api.chat_completion(messages))
            finally:
                loop.close()
            return response
        except Exception as e:
            return f"[API调用失败: {e}]"

    def _save_user_message(self, message: str):
        if self.session_id:
            self.db.create_chat_message(
                session_id=self.session_id,
                content=message,
                message_type="user",
                current_date=self.world.current_date if self.world else None,
                current_time=self.world.current_time if self.world else None,
                location=self.world.user_location if self.world else None
            )

    def _save_response_message(self, message: str):
        if self.session_id:
            if self.character_id and self.character:
                self.db.create_chat_message(
                    session_id=self.session_id,
                    character_id=self.character_id,
                    character_name=self.character.name,
                    content=message,
                    message_type="character",
                    current_date=self.world.current_date if self.world else None,
                    current_time=self.world.current_time if self.world else None,
                    location=self.world.user_location if self.world else None
                )
            else:
                self.db.create_chat_message(
                    session_id=self.session_id,
                    content=message,
                    message_type="narrator",
                    current_date=self.world.current_date if self.world else None,
                    current_time=self.world.current_time if self.world else None,
                    location=self.world.user_location if self.world else None
                )

    def clear_history(self):
        self.history = []

    def let_character_speak(self, location: str = None) -> str:
        characters = self.db.get_characters_by_world(self.world_id)
        if not characters:
            return "没有可用的角色"
        
        user_location = location or (self.world.user_location if self.world else None)
        
        if user_location:
            location_chars = [c for c in characters if c.location == user_location]
            if location_chars:
                characters = location_chars
        
        import random
        selected = max(characters, key=lambda c: c.activity_score + random.randint(0, 20))
        
        prompt = f"请以{selected.name}的身份说一句话或做出一个动作。"
        if selected.description:
            prompt += f"\n角色描述：{selected.description}"
        if selected.background:
            prompt += f"\n角色背景：{selected.background}"
        
        messages = [
            {"role": "system", "content": CHARACTER_SYSTEM_PROMPT.format(
                character_name=selected.name,
                background=selected.background or "无",
                description=selected.description or "无"
            )},
            {"role": "user", "content": prompt}
        ]
        
        response = self._call_api(messages)
        
        if self.session_id:
            self.db.create_chat_message(
                session_id=self.session_id,
                character_id=selected.id,
                character_name=selected.name,
                content=response,
                message_type="character",
                current_date=self.world.current_date if self.world else None,
                current_time=self.world.current_time if self.world else None,
                location=user_location
            )
        
        return f"{selected.name}: {response}"

_dialogue_managers: Dict[tuple, DialogueManager] = {}

def get_dialogue_manager(world_id: int, character_id: int = None) -> DialogueManager:
    key = (world_id, character_id)
    if key not in _dialogue_managers:
        _dialogue_managers[key] = DialogueManager(world_id, character_id)
    return _dialogue_managers[key]
