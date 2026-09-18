"""FastAPI prediction service for the waiting-time Random Forest model."""

from __future__ import annotations

import json
from pathlib import Path

import joblib
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field


BASE_DIR = Path(__file__).resolve().parent
MODEL_DIR = BASE_DIR / "model"
MODEL_PATH = MODEL_DIR / "waiting_time_model.joblib"
MODEL_INFO_PATH = MODEL_DIR / "model_info.json"
FEATURES = [
    "queueSize",
    "tokensAhead",
    "activeCounters",
    "averageServiceTime",
    "hourOfDay",
]


def load_model_package() -> dict:
    if not MODEL_PATH.exists():
        raise RuntimeError(
            f"Model file not found at {MODEL_PATH}. Run 'python train.py' first."
        )
    return joblib.load(MODEL_PATH)


MODEL_PACKAGE = load_model_package()
MODEL = MODEL_PACKAGE["model"]
MODEL_FEATURES = MODEL_PACKAGE.get("features", FEATURES)
if MODEL_FEATURES != FEATURES:
    raise RuntimeError(
        "The saved model feature order does not match the prediction contract."
    )

if not MODEL_INFO_PATH.exists():
    raise RuntimeError(
        f"Model information file not found at {MODEL_INFO_PATH}. Run 'python train.py' first."
    )
MODEL_INFO = json.loads(MODEL_INFO_PATH.read_text(encoding="utf-8"))

app = FastAPI(
    title="Smart Queue Waiting Time Predictor",
    version="phase-7",
)


class WaitingTimeRequest(BaseModel):
    queueSize: int = Field(ge=0)
    tokensAhead: int = Field(ge=0)
    activeCounters: int = Field(gt=0)
    averageServiceTime: float = Field(gt=0)
    hourOfDay: int = Field(ge=0, le=23)


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "model": "Random Forest Waiting Time Predictor",
    }


@app.post("/predict/waiting-time")
def predict_waiting_time(request: WaitingTimeRequest) -> dict[str, float]:
    input_values = pd.DataFrame(
        [[
            request.queueSize,
            request.tokensAhead,
            request.activeCounters,
            request.averageServiceTime,
            request.hourOfDay,
        ]],
        columns=MODEL_FEATURES,
    )
    prediction = float(MODEL.predict(input_values)[0])
    return {"predictedWaitingTime": round(max(0.0, prediction), 2)}


@app.get("/model-info")
def model_info() -> dict:
    return MODEL_INFO
