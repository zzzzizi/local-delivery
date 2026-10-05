import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import URL

ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


def get_database_url() -> URL:
    """Read local configuration; exported environment variables take precedence."""
    load_dotenv(ENV_FILE, override=False)
    required = ("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD")
    missing = [name for name in required if not os.getenv(name)]
    if missing:
        raise RuntimeError(
            f"Missing database settings: {', '.join(missing)}. "
            "Copy .env.example to local-delivery/.env and fill in the values."
        )

    # URL.create handles special characters in passwords without manual escaping.
    return URL.create(
        "postgresql+psycopg",
        username=os.environ["POSTGRES_USER"],
        password=os.environ["POSTGRES_PASSWORD"],
        host=os.getenv("POSTGRES_HOST", "127.0.0.1"),
        port=int(os.getenv("POSTGRES_PORT", "5433")),
        database=os.environ["POSTGRES_DB"],
    )
