from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
import pymysql

from app.context import authenticated_user_id, load_user_context
from app.database import list_chats, list_messages, load_history, save_exchange
from app.gemini import answer

app = FastAPI(title="TaskMind AI Service", version="0.1.0")


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    chat_id: int | None = Field(default=None, gt=0)


class ChatResponse(BaseModel):
    chat_id: int
    answer: str


def bearer_token(authorization: Annotated[str | None, Header()] = None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Vui lòng đăng nhập để dùng AI")
    token = authorization[7:].strip()
    if not token or len(token) > 4096:
        raise HTTPException(status_code=401, detail="Phiên đăng nhập không hợp lệ")
    return token


def mysql_http_error(error: pymysql.MySQLError) -> HTTPException:
    code = error.args[0] if error.args and isinstance(error.args[0], int) else 0
    if code == 1045:
        return HTTPException(
            status_code=503,
            detail="MySQL từ chối AI_DB_USERNAME/mật khẩu. Hãy dùng đúng tài khoản MySQL cho taskmind_ai_db.",
        )
    if code == 1044:
        return HTTPException(
            status_code=503,
            detail="Tài khoản MySQL AI chưa được cấp quyền trên taskmind_ai_db.",
        )
    if code == 1049:
        return HTTPException(status_code=503, detail="Không tìm thấy database taskmind_ai_db.")
    if code in (1054, 1146):
        return HTTPException(
            status_code=500,
            detail="Schema taskmind_ai_db thiếu bảng/cột mà AI Service cần. Kiểm tra aichats và aichatmessages.",
        )
    return HTTPException(status_code=503, detail=f"Lỗi MySQL AI (mã {code}). Kiểm tra kết nối và schema.")


@app.get("/health")
def health():
    return {"status": "ok", "service": "taskmind-ai-service"}


@app.get("/api/ai/chats")
def chats(token: Annotated[str, Depends(bearer_token)]):
    user_id = authenticated_user_id(token)
    try:
        return list_chats(user_id)
    except pymysql.MySQLError as error:
        raise mysql_http_error(error) from error


@app.get("/api/ai/chats/{chat_id}/messages")
def chat_messages(chat_id: int, token: Annotated[str, Depends(bearer_token)]):
    user_id = authenticated_user_id(token)
    try:
        return {"chat_id": chat_id, "messages": list_messages(user_id, chat_id)}
    except LookupError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except pymysql.MySQLError as error:
        raise mysql_http_error(error) from error


@app.post("/api/ai/chat", response_model=ChatResponse)
def chat(body: ChatRequest, token: Annotated[str, Depends(bearer_token)]):
    question = body.message.strip()
    if not question:
        raise HTTPException(status_code=422, detail="Nội dung câu hỏi không được để trống")
    user_id, context = load_user_context(token)
    try:
        history = load_history(user_id, body.chat_id) if body.chat_id else []
    except LookupError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except pymysql.MySQLError as error:
        raise mysql_http_error(error) from error
    response = answer(question, context, history)
    try:
        chat_id = save_exchange(user_id, body.chat_id, question, response)
    except LookupError as error:
        raise HTTPException(status_code=404, detail=str(error)) from error
    except pymysql.MySQLError as error:
        raise mysql_http_error(error) from error
    return ChatResponse(chat_id=chat_id, answer=response)
