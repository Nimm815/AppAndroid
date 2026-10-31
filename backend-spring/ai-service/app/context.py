import httpx
from fastapi import HTTPException

from app.config import settings


def _get(client: httpx.Client, base: str, path: str, token: str):
    response = client.get(
        f"{base.rstrip('/')}{path}",
        headers={"Authorization": f"Bearer {token}"},
    )
    if response.status_code in (401, 403):
        raise HTTPException(status_code=401, detail="Phiên đăng nhập không hợp lệ")
    if response.is_error:
        raise HTTPException(status_code=502, detail="Không đọc được dữ liệu từ service nghiệp vụ")
    return response.json()


def authenticated_user_id(token: str) -> int:
    timeout = httpx.Timeout(12.0, connect=4.0)
    try:
        with httpx.Client(timeout=timeout) as client:
            user = _get(client, settings.auth_service_url, "/api/auth/me", token)
            user_id = user.get("userId")
            if not isinstance(user_id, int) or user_id < 1:
                raise HTTPException(status_code=502, detail="Auth Service tráº£ vá» tÃ i khoáº£n khÃ´ng há»£p lá»‡")
            return user_id
    except HTTPException:
        raise
    except httpx.HTTPError as error:
        raise HTTPException(status_code=503, detail="KhÃ´ng xÃ¡c thá»±c Ä‘Æ°á»£c tÃ i khoáº£n") from error


def load_user_context(token: str) -> tuple[int, dict]:
    timeout = httpx.Timeout(12.0, connect=4.0)
    try:
        with httpx.Client(timeout=timeout) as client:
            # Auth is checked first. Task and Productivity independently verify the
            # same bearer token and filter every result by its owner.
            user = _get(client, settings.auth_service_url, "/api/auth/me", token)
            data = {
                "tasks": _get(client, settings.task_service_url, "/api/tasks?status=ALL&sort=due", token),
                "task_lists": _get(client, settings.task_service_url, "/api/lists", token),
                "habits": _get(client, settings.productivity_service_url, "/api/habits", token),
                "journal_entries": _get(client, settings.productivity_service_url, "/api/journal-entries", token),
                "calendar_events": _get(client, settings.productivity_service_url, "/api/calendar-events", token),
                "reminders": _get(client, settings.productivity_service_url, "/api/reminders", token),
                "notifications": _get(client, settings.productivity_service_url, "/api/notifications", token),
            }
            # Exclude account identifiers and authentication-related profile fields.
            data["profile"] = {key: user[key] for key in ("fullName", "name") if key in user}
            user_id = user.get("userId")
            if not isinstance(user_id, int) or user_id < 1:
                raise HTTPException(status_code=502, detail="Auth Service trả về tài khoản không hợp lệ")
            return user_id, data
    except HTTPException:
        raise
    except httpx.HTTPError as error:
        raise HTTPException(status_code=503, detail="Không kết nối được các service dữ liệu") from error
