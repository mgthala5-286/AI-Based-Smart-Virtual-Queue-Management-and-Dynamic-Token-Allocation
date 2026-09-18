# Smart Queue ML Service

This is Phase 7 of the AI-Based Smart Virtual Queue and Dynamic Token Allocation System.
It provides a standalone Random Forest Regression service for predicting virtual queue waiting time. Java integration is intentionally deferred to Phase 8.

## Features

The model uses these inputs:

- `queueSize`: number of active tokens in the service queue.
- `tokensAhead`: number of active tokens ahead of the requested token.
- `activeCounters`: number of currently active service counters.
- `averageServiceTime`: average service time in minutes.
- `hourOfDay`: hour of day from 0 through 23.

The output is `predictedWaitingTime`, in minutes.

## Train the model

From this directory, install the dependencies and train the model:

```text
python -m pip install -r requirements.txt
python train.py
```

Training creates:

- `data/waiting_time_dataset.csv` with at least 5,000 synthetic records.
- `model/waiting_time_model.joblib` containing the trained Random Forest model.
- `model/model_info.json` containing the dataset size and actual evaluation metrics.

The training script uses `random_state=42`, an 80/20 train/test split, and 100 Random Forest estimators. It prints the calculated MAE, RMSE, and R² values.

## Start the ML service

Train first, then start FastAPI from this directory:

```text
uvicorn app:app --reload --port 8000
```

The model is loaded once when the application starts. Prediction requests do not retrain the model.

## API examples

Health check:

```text
GET http://localhost:8000/health
```

Model information:

```text
GET http://localhost:8000/model-info
```

Prediction:

```text
POST http://localhost:8000/predict/waiting-time
Content-Type: application/json

{
  "queueSize": 10,
  "tokensAhead": 4,
  "activeCounters": 2,
  "averageServiceTime": 10,
  "hourOfDay": 14
}
```

Invalid values such as a negative queue size or zero active counters return FastAPI validation errors with HTTP 422.

## Scope

This service implements only Random Forest waiting-time prediction. It does not reorder tokens, perform Java integration, predict no-shows, or implement later project phases.
