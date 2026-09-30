"""Configuración de la aplicación mediante variables de entorno.

Los valores se leen del entorno o de un archivo ``.env`` (no versionado).
Ver ``.env.example`` para la lista de variables.
"""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "Uniban Suministros API"
    app_version: str = "0.1.0"
    environment: str = "development"
    api_v1_prefix: str = "/api/v1"

    # Cadena de conexión SQLAlchemy a PostgreSQL. Sin valor por defecto real:
    # debe definirse en .env, p. ej. postgresql+psycopg://usuario:clave@localhost:5432/uniban
    database_url: str | None = None

    # Orígenes permitidos para CORS (separados por coma). Solo necesario en desarrollo web.
    cors_origins: str = Field(default="")

    @property
    def cors_origins_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
