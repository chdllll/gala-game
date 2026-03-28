import os
import json
import sqlite3
import asyncio
from typing import Optional, List, Dict, Any

DATA_DIR = os.path.join(os.environ.get("HOME", "/sdcard"), "gala_game")
WORLDS_DIR = os.path.join(DATA_DIR, "worlds")
DB_PATH = os.path.join(DATA_DIR, "gala_game.db")

os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(WORLDS_DIR, exist_ok=True)

class DatabaseManager:
    def __init__(self):
        self.conn: Optional[sqlite3.Connection] = None
        self._init_db()

    def _get_conn(self) -> sqlite3.Connection:
        if self.conn is None:
            self.conn = sqlite3.connect(DB_PATH)
            self.conn.row_factory = sqlite3.Row
        return self.conn

    def _init_db(self):
        conn = self._get_conn()
        cursor = conn.cursor()

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS worlds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS dialogues (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                world_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (world_id) REFERENCES worlds (id)
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                world_id INTEGER NOT NULL,
                memory_type TEXT NOT NULL,
                content TEXT NOT NULL,
                importance INTEGER DEFAULT 5,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (world_id) REFERENCES worlds (id)
            )
        """)

        conn.commit()

    def create_world(self, name: str, description: str = "") -> int:
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO worlds (name, description) VALUES (?, ?)",
            (name, description)
        )
        conn.commit()
        world_id = cursor.lastrowid

        world_dir = os.path.join(WORLDS_DIR, str(world_id))
        os.makedirs(world_dir, exist_ok=True)

        return world_id

    def get_all_worlds(self) -> List[Dict[str, Any]]:
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM worlds ORDER BY updated_at DESC")
        rows = cursor.fetchall()
        return [dict(row) for row in rows]

    def get_world(self, world_id: int) -> Optional[Dict[str, Any]]:
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM worlds WHERE id = ?", (world_id,))
        row = cursor.fetchone()
        return dict(row) if row else None

    def delete_world(self, world_id: int):
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM dialogues WHERE world_id = ?", (world_id,))
        cursor.execute("DELETE FROM memories WHERE world_id = ?", (world_id,))
        cursor.execute("DELETE FROM worlds WHERE id = ?", (world_id,))
        conn.commit()

    def add_dialogue(self, world_id: int, role: str, content: str):
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO dialogues (world_id, role, content) VALUES (?, ?, ?)",
            (world_id, role, content)
        )
        cursor.execute(
            "UPDATE worlds SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (world_id,)
        )
        conn.commit()

    def get_dialogues(self, world_id: int, limit: int = 50) -> List[Dict[str, Any]]:
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute(
            """
            SELECT role, content, created_at FROM dialogues
            WHERE world_id = ?
            ORDER BY created_at DESC LIMIT ?
            """,
            (world_id, limit)
        )
        rows = cursor.fetchall()
        return [dict(row) for row in reversed(rows)]

    def add_memory(self, world_id: int, memory_type: str, content: str, importance: int = 5):
        conn = self._get_conn()
        cursor = conn.cursor()
        cursor.execute(
            """
            INSERT INTO memories (world_id, memory_type, content, importance)
            VALUES (?, ?, ?, ?)
            """,
            (world_id, memory_type, content, importance)
        )
        conn.commit()

    def get_memories(self, world_id: int, memory_type: str = None) -> List[Dict[str, Any]]:
        conn = self._get_conn()
        cursor = conn.cursor()
        if memory_type:
            cursor.execute(
                "SELECT * FROM memories WHERE world_id = ? AND memory_type = ? ORDER BY importance DESC",
                (world_id, memory_type)
            )
        else:
            cursor.execute(
                "SELECT * FROM memories WHERE world_id = ? ORDER BY importance DESC",
                (world_id,)
            )
        rows = cursor.fetchall()
        return [dict(row) for row in rows]

_db_manager: Optional[DatabaseManager] = None

def get_db_manager() -> DatabaseManager:
    global _db_manager
    if _db_manager is None:
        _db_manager = DatabaseManager()
    return _db_manager
