from datetime import date, datetime
from enum import Enum
from typing import Optional
from pydantic import BaseModel


# ---------- Input: cloud-cost-events (consumed) ----------

class CloudCostEvent(BaseModel):
    provider: str            # AWS | GCP | Azure
    account_id: str
    service: str
    resource_id: str
    cost_amount: float
    usage_quantity: float
    period_start: date
    period_end: date
    tags: dict[str, str] = {}


# ---------- Output: insight-events (produced) ----------

class InsightType(str, Enum):
    ANOMALY = "ANOMALY"
    FORECAST = "FORECAST"
    RIGHTSIZING = "RIGHTSIZING"


class Severity(str, Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class AnomalyDetails(BaseModel):
    resource_id: str
    cost: float
    expected_cost: float
    zscore: float


class ForecastDetails(BaseModel):
    account_id: str
    horizon_days: int
    forecast: dict[str, float]     # date-string -> predicted cost
    month_end_runrate: float


class RightsizingDetails(BaseModel):
    resource_id: str
    resource_type: str
    avg_usage_percent: float
    action: str                    # TERMINATE | DOWNSIZE | UPSIZE | NONE
    est_monthly_savings: float


class InsightEvent(BaseModel):
    insight_type: InsightType
    severity: Severity
    confidence: float              # 0-1
    message: str
    generated_at: datetime
    details: AnomalyDetails | ForecastDetails | RightsizingDetails
