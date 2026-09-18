# ML Results and Limitations

## Model

The Phase 7 service uses `RandomForestRegressor` with 100 estimators and `random_state=42`. It predicts waiting time in minutes from:

- queue size
- tokens ahead
- active counters
- average service time
- hour of day

The persisted training metadata reports:

| Metric | Value |
|---|---:|
| Dataset size | 5,000 synthetic records |
| MAE | 3.2208 minutes |
| RMSE | 6.7202 minutes |
| R² | 0.9964 |

The training script uses an 80/20 train/test split and persists both the model and metrics in `ml-service/model/`.

## Interpretation

The high R² indicates that the model learned the synthetic data-generation relationship well. It does not prove that the model will achieve the same accuracy in a real hospital, bank, campus, or public-service queue.

## Java integration

The Java backend validates request fields and prediction output. If the Python service is disabled, unreachable, times out, returns an invalid value, or reports an invalid request, Java falls back to:

```text
tokensAhead × averageServiceTime / activeCounters
```

This fallback is also covered by the backend test suite.

## Limitations

1. The dataset is synthetic and is not collected from real users or service counters.
2. The target is generated from a known formula plus noise, so the reported metrics are optimistic for the real world.
3. There is no calibration or confidence interval for individual predictions.
4. The feature set does not include staff-specific service speed, breaks, arrival rates, abandonment, no-show behavior, holidays, or time-varying counter efficiency.
5. The current model predicts waiting time; it does not reorder tokens or make fairness decisions.
6. The system does not yet provide model drift monitoring, retraining orchestration, feature governance, or production model versioning.
7. The ML endpoint is local and has no separate production authentication or rate-limiting layer.

## Recommended research evaluation

For a credible paper, replace or augment synthetic records with anonymized timestamped queue observations. Compare the ML model and fallback formula using a time-based holdout, report confidence intervals, measure error by service and traffic period, and include an outage test showing that fallback behavior preserves availability.

