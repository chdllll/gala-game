from db_manager import DatabaseManager, get_data_dir, DATA_DIR, WORLDS_DIR
from dialogue import get_dialogue_manager as _get_dialogue_manager
from typing import Optional, List, Dict, Any
import os
import json

_db_manager: Optional[DatabaseManager] = None

def get_db_manager() -> DatabaseManager:
    global _db_manager
    if _db_manager is None:
        _db_manager = DatabaseManager()
    return _db_manager

def get_all_worlds() -> List[Dict[str, Any]]:
    db = get_db_manager()
    worlds = db.get_all_worlds()
    return [_world_to_dict(w) for w in worlds]

def get_world(world_id: int) -> Optional[Dict[str, Any]]:
    db = get_db_manager()
    world = db.get_world(world_id)
    return _world_to_dict(world) if world else None

def create_world(name: str, background: str = "", locations: List[str] = None) -> int:
    db = get_db_manager()
    world = db.create_world(name, background if background else None, locations)
    return world.id

def update_world(world_id: int, **kwargs) -> bool:
    db = get_db_manager()
    try:
        return db.update_world(world_id, **kwargs)
    except Exception as e:
        print(f"更新世界失败: {e}")
        return False

def delete_world(world_id: int) -> bool:
    db = get_db_manager()
    try:
        return db.delete_world(world_id)
    except Exception as e:
        print(f"删除世界失败: {e}")
        return False

def _world_to_dict(world) -> Dict[str, Any]:
    return {
        "id": world.id,
        "name": world.name,
        "background": world.background,
        "created_at": world.created_at,
        "current_date": world.current_date,
        "current_time": world.current_time,
        "user_location": world.user_location,
        "communication_character": world.communication_character,
        "user_name": world.user_name,
        "map_image": world.map_image,
        "script_enabled": world.script_enabled,
        "current_chapter_index": world.current_chapter_index,
    }

def get_characters_by_world(world_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    characters = db.get_characters_by_world(world_id)
    return [_character_to_dict(c) for c in characters]

def get_character(character_id: int) -> Optional[Dict[str, Any]]:
    db = get_db_manager()
    character = db.get_character(character_id)
    return _character_to_dict(character) if character else None

def create_character(world_id: int, name: str, background: str = "", 
                     description: str = "", location: str = "", 
                     gender: str = "female") -> int:
    db = get_db_manager()
    character = db.create_character(
        world_id=world_id,
        name=name,
        background=background if background else None,
        description=description if description else None,
        location=location if location else None,
        gender=gender
    )
    return character.id

def update_character(character_id: int, **kwargs) -> bool:
    db = get_db_manager()
    try:
        db.update_character(character_id, **kwargs)
        return True
    except Exception as e:
        print(f"更新角色失败: {e}")
        return False

def delete_character(character_id: int) -> bool:
    db = get_db_manager()
    try:
        db.delete_character(character_id)
        return True
    except Exception as e:
        print(f"删除角色失败: {e}")
        return False

def _character_to_dict(character) -> Dict[str, Any]:
    return {
        "id": character.id,
        "world_id": character.world_id,
        "name": character.name,
        "background": character.background,
        "description": character.description,
        "avatar_path": character.avatar_path,
        "location": character.location,
        "gender": character.gender,
        "activity_score": character.activity_score,
        "event_frequency": character.event_frequency,
        "relationship_with_user": character.relationship_with_user,
    }

def get_locations(world_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    locations = db.get_locations(world_id)
    return [_location_to_dict(loc) for loc in locations]

def get_location(location_id: int) -> Optional[Dict[str, Any]]:
    db = get_db_manager()
    location = db.get_location(location_id)
    return _location_to_dict(location) if location else None

def get_primary_locations(world_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    locations = db.get_primary_locations(world_id)
    return [_location_to_dict(loc) for loc in locations]

def get_sub_locations(parent_location_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    locations = db.get_sub_locations(parent_location_id)
    return [_location_to_dict(loc) for loc in locations]

def create_location(world_id: int, name: str, image_path: str = "", 
                    parent_location_id: int = None) -> int:
    db = get_db_manager()
    location = db.create_location(
        world_id=world_id,
        name=name,
        image_path=image_path if image_path else None,
        parent_location_id=parent_location_id
    )
    return location.id

def update_location(location_id: int, **kwargs) -> bool:
    db = get_db_manager()
    try:
        db.update_location(location_id, **kwargs)
        return True
    except Exception as e:
        print(f"更新位置失败: {e}")
        return False

def delete_location(location_id: int) -> bool:
    db = get_db_manager()
    try:
        db.delete_location(location_id)
        return True
    except Exception as e:
        print(f"删除位置失败: {e}")
        return False

def _location_to_dict(location) -> Dict[str, Any]:
    return {
        "id": location.id,
        "world_id": location.world_id,
        "name": location.name,
        "image_path": location.image_path,
        "parent_location_id": location.parent_location_id,
    }

def get_memories_by_world(world_id: int, limit: int = 20) -> List[Dict[str, Any]]:
    db = get_db_manager()
    memories = db.get_memories_by_world(world_id, limit=limit)
    return [_memory_to_dict(m) for m in memories]

def create_memory(world_id: int, content: str, memory_type: str = "general",
                  importance: int = 5, character_id: int = None) -> int:
    db = get_db_manager()
    memory = db.create_memory(
        world_id=world_id,
        content=content,
        memory_type=memory_type,
        importance=importance,
        character_id=character_id
    )
    return memory.id

def delete_memory(memory_id: int) -> bool:
    db = get_db_manager()
    try:
        db.delete_memory(memory_id)
        return True
    except Exception as e:
        print(f"删除记忆失败: {e}")
        return False

def _memory_to_dict(memory) -> Dict[str, Any]:
    return {
        "id": memory.id,
        "world_id": memory.world_id,
        "character_id": memory.character_id,
        "memory_type": memory.memory_type,
        "content": memory.content,
        "importance": memory.importance,
        "created_at": memory.created_at,
    }

def get_active_calls_by_world(world_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    calls = db.get_active_calls_by_world(world_id)
    return [_active_call_to_dict(c) for c in calls]

def create_active_call(world_id: int, character_id: int, character_name: str,
                       original_location: str, call_start_date: str, 
                       call_start_time: str) -> int:
    db = get_db_manager()
    call = db.create_active_call(
        world_id=world_id,
        character_id=character_id,
        character_name=character_name,
        original_location=original_location,
        call_start_date=call_start_date,
        call_start_time=call_start_time
    )
    return call.id

def end_active_call(call_id: int) -> bool:
    db = get_db_manager()
    try:
        db.end_active_call(call_id)
        return True
    except Exception as e:
        print(f"结束通话失败: {e}")
        return False

def _active_call_to_dict(call) -> Dict[str, Any]:
    return {
        "id": call.id,
        "world_id": call.world_id,
        "character_id": call.character_id,
        "character_name": call.character_name,
        "original_location": call.original_location,
        "call_start_date": call.call_start_date,
        "call_start_time": call.call_start_time,
    }

def get_pending_call_requests(world_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    requests = db.get_pending_call_requests(world_id)
    return [_call_request_to_dict(r) for r in requests]

def accept_call_request(request_id: int) -> bool:
    db = get_db_manager()
    try:
        db.mark_call_request_as_handled(request_id)
        return True
    except Exception as e:
        print(f"接听通话失败: {e}")
        return False

def reject_call_request(request_id: int) -> bool:
    db = get_db_manager()
    try:
        db.dismiss_call_request(request_id)
        return True
    except Exception as e:
        print(f"拒绝通话失败: {e}")
        return False

def _call_request_to_dict(request) -> Dict[str, Any]:
    return {
        "id": request.id,
        "world_id": request.world_id,
        "character_id": request.character_id,
        "character_name": request.character_name,
        "request_date": request.request_date,
        "request_time": request.request_time,
    }

def get_call_history(world_id: int, limit: int = 50) -> List[Dict[str, Any]]:
    db = get_db_manager()
    history = db.get_call_history(world_id, limit)
    return history

def get_chat_sessions_by_world(world_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    sessions = db.get_chat_sessions_by_world(world_id)
    return [_chat_session_to_dict(s) for s in sessions]

def create_chat_session(world_id: int, name: str = "默认会话") -> int:
    db = get_db_manager()
    session = db.create_chat_session(world_id, name)
    return session.id

def get_chat_messages_by_session(session_id: int, limit: int = 50, 
                                  location: str = None) -> List[Dict[str, Any]]:
    db = get_db_manager()
    messages = db.get_chat_messages_by_session(session_id, limit, location)
    return [_chat_message_to_dict(m) for m in messages]

def create_chat_message(session_id: int, character_id: int = None,
                        character_name: str = None, content: str = "",
                        action: str = None, message_type: str = "user",
                        current_date: str = None, current_time: str = None,
                        location: str = None) -> int:
    db = get_db_manager()
    message = db.create_chat_message(
        session_id=session_id,
        character_id=character_id,
        character_name=character_name,
        content=content,
        action=action,
        message_type=message_type,
        current_date=current_date,
        current_time=current_time,
        location=location
    )
    return message.id

def delete_chat_messages_after(session_id: int, message_id: int, 
                                location: str = None) -> bool:
    db = get_db_manager()
    try:
        db.delete_chat_messages_after(session_id, message_id, location)
        return True
    except Exception as e:
        print(f"删除消息失败: {e}")
        return False

def _chat_session_to_dict(session) -> Dict[str, Any]:
    return {
        "id": session.id,
        "world_id": session.world_id,
        "name": session.name,
        "created_at": session.created_at,
    }

def _chat_message_to_dict(message) -> Dict[str, Any]:
    return {
        "id": message.id,
        "session_id": message.session_id,
        "character_id": message.character_id,
        "character_name": message.character_name,
        "content": message.content,
        "action": message.action,
        "message_type": message.message_type,
        "current_date": message.current_date,
        "current_time": message.current_time,
        "location": message.location,
        "created_at": message.created_at,
    }

def get_api_config() -> Optional[Dict[str, Any]]:
    db = get_db_manager()
    config = db.get_api_config()
    if config:
        return {
            "api1_key": config.api1_key,
            "api1_model": config.api1_model,
            "api2_key": config.api2_key,
            "api2_model": config.api2_model,
        }
    return None

def update_api_config(api1_key: str = None, api1_model: str = None,
                      api2_key: str = None, api2_model: str = None) -> bool:
    db = get_db_manager()
    try:
        db.save_api_config(api1_key, api1_model, api2_key, api2_model)
        return True
    except Exception as e:
        print(f"更新API配置失败: {e}")
        return False

def get_background_images(character_id: int) -> List[Dict[str, Any]]:
    db = get_db_manager()
    images = db.get_background_images(character_id)
    return [_background_image_to_dict(img) for img in images]

def create_background_image(character_id: int, image_path: str,
                            description: str = "", tags: str = "") -> int:
    db = get_db_manager()
    image = db.create_background_image(
        character_id=character_id,
        image_path=image_path,
        description=description if description else None,
        tags=tags if tags else None
    )
    return image.id

def _background_image_to_dict(image) -> Dict[str, Any]:
    return {
        "id": image.id,
        "character_id": image.character_id,
        "image_path": image.image_path,
        "description": image.description,
        "tags": image.tags,
    }

def get_dialogue_manager(world_id: int, character_id: int = None):
    return _get_dialogue_manager(world_id, character_id)

def refresh_api_client():
    import api_client
    api_client._api_client = None
    return True
