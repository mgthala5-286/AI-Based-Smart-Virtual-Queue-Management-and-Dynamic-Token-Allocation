"""Generate training data and train the waiting-time Random Forest model."""

from __future__ import annotations

import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
MODEL_DIR = BASE_DIR / "model"
DATASET_PATH = DATA_DIR / "waiting_time_dataset.csv"
MODEL_PATH = MODEL_DIR / "waiting_time_model.joblib"
MODEL_INFO_PATH = MODEL_DIR / "model_info.json"

FEATURES = [
    "queueSize",
    "tokensAhead",
    "activeCounters",
    "averageServiceTime",
    "hourOfDay",
]
TARGET = "waitingTime"
DATASET_SIZE = 5_000
RANDOM_STATE = 42


def generate_dataset(record_count: int = DATASET_SIZE) -> pd.DataFrame:
    """Create deterministic synthetic queue observations with realistic noise."""

    rng = np.random.default_rng(RANDOM_STATE)
    queue_size = rng.integers(0, 41, size=record_count)
    tokens_ahead = rng.integers(0, queue_size + 1)
    active_counters = rng.integers(1, 6, size=record_count)
    average_service_time = np.round(rng.uniform(5.0, 30.0, size=record_count), 2)
    hour_of_day = rng.integers(0, 24, size=record_count)

    rush_hour = np.isin(hour_of_day, [9, 10, 11, 14, 15, 16]).astype(float)
    base_waiting_time = (
        tokens_ahead * average_service_time / active_counters
        + queue_size * 0.05
        + rush_hour * 1.5
    )
    noise = rng.normal(loc=0.0, scale=1.25, size=record_count)
    waiting_time = np.maximum(0.0, base_waiting_time + noise)

    return pd.DataFrame(
        {
            "queueSize": queue_size,
            "tokensAhead": tokens_ahead,
            "activeCounters": active_counters,
            "averageServiceTime": average_service_time,
            "hourOfDay": hour_of_day,
            "waitingTime": np.round(waiting_time, 2),
        }
    )


def train_model(dataset: pd.DataFrame) -> dict[str, float | int | list[str]]:
    """Train, evaluate, and persist the Random Forest model."""

    X = dataset[FEATURES]
    y = dataset[TARGET]
    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.20,
        random_state=RANDOM_STATE,
    )

    model = RandomForestRegressor(
        n_estimators=100,
        random_state=RANDOM_STATE,
        # A single worker keeps training portable on restricted Windows hosts.
        n_jobs=1,
    )
    model.fit(X_train, y_train)
    predictions = model.predict(X_test)

    metrics = {
        "mae": float(mean_absolute_error(y_test, predictions)),
        "rmse": float(np.sqrt(mean_squared_error(y_test, predictions))),
        "r2": float(r2_score(y_test, predictions)),
    }

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "model": model,
            "features": FEATURES,
        },
        MODEL_PATH,
    )

    model_info = {
        "model": "RandomForestRegressor",
        "datasetSize": int(len(dataset)),
        "features": FEATURES,
        **metrics,
    }
    MODEL_INFO_PATH.write_text(
        json.dumps(model_info, indent=2) + "\n",
        encoding="utf-8",
    )
    return model_info


def main() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if DATASET_PATH.exists():
        dataset = pd.read_csv(DATASET_PATH)
    else:
        dataset = generate_dataset()
        dataset.to_csv(DATASET_PATH, index=False)

    if len(dataset) < DATASET_SIZE:
        raise ValueError(
            f"The dataset must contain at least {DATASET_SIZE} records; "
            f"found {len(dataset)}."
        )

    model_info = train_model(dataset)
    print("Model: Random Forest Regression")
    print(f"Dataset: {model_info['datasetSize']} records")
    print(f"MAE: {model_info['mae']:.4f} minutes")
    print(f"RMSE: {model_info['rmse']:.4f} minutes")
    print(f"R2: {model_info['r2']:.4f}")
    print("Saved model: model/waiting_time_model.joblib")


if __name__ == "__main__":
    main()
