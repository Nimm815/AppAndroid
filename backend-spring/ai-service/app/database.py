import pymysql
from pymysql.cursors import DictCursor

from app.config import settings


def connect():
    return pymysql.connect(
        host=settings.ai_db_host,
        port=settings.ai_db_port,
        user=settings.ai_db_username,
        password=settings.ai_db_password,
        database="taskmind_ai_db",
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=False,
        connect_timeout=5,
        read_timeout=20,
        write_timeout=20,
    )


def save_exchange(user_id: int, chat_id: int | None, question: str, answer: str) -> int:
    """Write only to the two existing AI chat tables; never creates or alters schema."""
    connection = connect()
    try:
        with connection.cursor() as cursor:
            if chat_id is None:
                title = question.strip()[:200] or "Cuộc trò chuyện mới"
                cursor.execute(
                    "INSERT INTO aichats (UserID, Title, CreatedAt, UpdatedAt) "
                    "VALUES (%s, %s, UTC_TIMESTAMP(), UTC_TIMESTAMP())",
                    (user_id, title),
                )
                chat_id = cursor.lastrowid
            else:
                cursor.execute(
                    "SELECT ChatID FROM aichats WHERE ChatID=%s AND UserID=%s",
                    (chat_id, user_id),
                )
                if cursor.fetchone() is None:
                    raise LookupError("Không tìm thấy cuộc trò chuyện")
                cursor.execute(
                    "UPDATE aichats SET UpdatedAt=UTC_TIMESTAMP() WHERE ChatID=%s",
                    (chat_id,),
                )
            cursor.executemany(
                "INSERT INTO aichatmessages (ChatID, Role, Content, CreatedAt) "
                "VALUES (%s, %s, %s, UTC_TIMESTAMP())",
                [(chat_id, "user", question), (chat_id, "assistant", answer)],
            )
        connection.commit()
        return int(chat_id)
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def load_history(user_id: int, chat_id: int, limit: int = 12) -> list[dict]:
    connection = connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT ChatID FROM aichats WHERE ChatID=%s AND UserID=%s",
                (chat_id, user_id),
            )
            if cursor.fetchone() is None:
                raise LookupError("Không tìm thấy cuộc trò chuyện")
            cursor.execute(
                "SELECT m.Role, m.Content FROM aichatmessages m "
                "JOIN aichats c ON c.ChatID=m.ChatID "
                "WHERE m.ChatID=%s AND c.UserID=%s "
                "ORDER BY m.CreatedAt DESC, m.MessageID DESC LIMIT %s",
                (chat_id, user_id, limit),
            )
            rows = list(reversed(cursor.fetchall()))
            return [
                {"role": "model" if row["Role"] == "assistant" else "user",
                 "parts": [{"text": row["Content"]}]}
                for row in rows
            ]
    finally:
        connection.close()


def list_chats(user_id: int, limit: int = 50) -> list[dict]:
    connection = connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT ChatID, Title, UpdatedAt FROM aichats "
                "WHERE UserID=%s ORDER BY UpdatedAt DESC, ChatID DESC LIMIT %s",
                (user_id, limit),
            )
            return [
                {
                    "chat_id": row["ChatID"],
                    "title": row["Title"],
                    "updated_at": row["UpdatedAt"].isoformat() if row["UpdatedAt"] else "",
                }
                for row in cursor.fetchall()
            ]
    finally:
        connection.close()


def list_messages(user_id: int, chat_id: int, limit: int = 500) -> list[dict]:
    connection = connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT ChatID FROM aichats WHERE ChatID=%s AND UserID=%s",
                (chat_id, user_id),
            )
            if cursor.fetchone() is None:
                raise LookupError("Không tìm thấy cuộc trò chuyện")
            cursor.execute(
                "SELECT m.Role, m.Content, m.CreatedAt FROM aichatmessages m "
                "JOIN aichats c ON c.ChatID=m.ChatID "
                "WHERE m.ChatID=%s AND c.UserID=%s "
                "ORDER BY m.CreatedAt ASC, m.MessageID ASC LIMIT %s",
                (chat_id, user_id, limit),
            )
            return [
                {
                    "role": row["Role"],
                    "content": row["Content"],
                    "created_at": row["CreatedAt"].isoformat() if row["CreatedAt"] else "",
                }
                for row in cursor.fetchall()
            ]
    finally:
        connection.close()
