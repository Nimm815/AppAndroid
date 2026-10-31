from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_port: int = 8084
    ai_db_host: str = "localhost"
    ai_db_port: int = 3306
    ai_db_username: str
    ai_db_password: str
    auth_service_url: str = "http://127.0.0.1:8081"
    task_service_url: str = "http://127.0.0.1:8082"
    productivity_service_url: str = "http://127.0.0.1:8083"
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.8-flash"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
