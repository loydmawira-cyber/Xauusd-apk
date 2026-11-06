import os
import secrets
from typing import List

class Settings:
    PROJECT_NAME: str = "XAU Pilot Core Engine"
    API_V1_STR: str = "/api/v1"
    EA_V1_STR: str = "/ea/v1"
    VERSION: str = "2.4.0"
    
    # Database
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "postgresql://xau_user@localhost:5432/xau_pilot_db"
    )
    
    # Security & JWT
    # Local development gets an ephemeral key; production must set JWT_SECRET explicitly.
    JWT_SECRET: str = os.getenv("JWT_SECRET") or secrets.token_urlsafe(48)
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 # 24 hours
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    
    # CORS (Strict origin list to prevent CSRF)
    ALLOWED_ORIGINS: List[str] = [
        "https://app.xaupilot.com",
        "https://portal.xaupilot.com",
        "http://localhost:3000",
        "http://localhost:8000"
    ]
    
    # Default Risk Boundaries
    DEFAULT_MAX_DAILY_LOSS: float = 500.0
    DEFAULT_MAX_TOTAL_EXPOSURE_LOTS: float = 2.0
    DEFAULT_MAX_LOT_PER_TRADE: float = 0.50
    DEFAULT_MAX_OPEN_POSITIONS: int = 8
    DEFAULT_MAX_SPREAD_POINTS: float = 35.0
    DEFAULT_MIN_MARGIN_LEVEL: float = 200.0

    # Hosting adapter. Dry-run is mandatory by default to prevent accidental cloud spend.
    HOSTING_PROVIDER: str = os.getenv("HOSTING_PROVIDER", "simulator")
    HOSTING_DRY_RUN: bool = os.getenv("HOSTING_DRY_RUN", "true").lower() == "true"
    AWS_REGION: str = os.getenv("AWS_REGION", "us-east-1")
    AWS_AMI_ID: str = os.getenv("AWS_AMI_ID", "")
    AWS_INSTANCE_TYPE: str = os.getenv("AWS_INSTANCE_TYPE", "t3.medium")
    AWS_SUBNET_ID: str = os.getenv("AWS_SUBNET_ID", "")
    AWS_SECURITY_GROUP_ID: str = os.getenv("AWS_SECURITY_GROUP_ID", "")
    AWS_IAM_INSTANCE_PROFILE: str = os.getenv("AWS_IAM_INSTANCE_PROFILE", "")

settings = Settings()
