# Final Validation Report

## Scope

This report covers final Phase 10 validation of the existing Phase 1–9 implementation. No new runtime feature was added.

## Automated validation

Command:

```text
cd smart-queue/backend
mvn test
```

Recorded result:

```text
Tests run: 63
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

The suite covers JSON persistence, health, authentication and authorization, service/counter management, token lifecycle, queue behavior, dynamic allocation, ML integration, and ML fallback.

## Manual validation checklist

| Area | Result |
|---|---|
| Landing page and navigation | Passed |
| USER registration/login | Passed |
| USER token generation | Passed |
| USER queue position and estimated wait | Passed |
| USER token cancellation | Passed |
| STAFF read-only queue | Passed |
| ADMIN service/counter dashboard | Passed |
| Cross-origin frontend-to-Java requests | Passed |
| Temporary test data cleanup | Passed |
| Frontend direct calls to Python service | None found |

## End-to-end smoke evidence

The final local smoke run used a temporary USER account and the existing demo ADMIN account. It produced `G001`, `G002`, and `G003` in FIFO positions `1`, `2`, and `3`; after cancelling `G002`, the remaining active queue was `G001` at `1` and `G003` at `2`. Deactivating one of two active counters increased the later token's wait, and reactivating the counter restored the capacity-dependent estimate. A USER request to the ADMIN counter endpoint returned `403`.

The Java health endpoint and ML health endpoint both returned `UP`. The ML metadata endpoint reported 5,000 training records with MAE `3.2208`, RMSE `6.7202`, and R² `0.9964`.

## Final data state

The checked-in local demo data contains two development accounts, one active service, two active counters, and no temporary tokens or queue events. Prediction history was cleared after the smoke test so it does not refer to deleted demo tokens.

## Known operational note

The frontend expects Java on port 8080, the optional ML service on port 8000, and the static frontend on port 5500. If a port is already occupied, stop the existing process or change the local configuration before running the demo.
