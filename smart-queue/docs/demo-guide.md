# Final Demo Guide

## Start the local system

From the repository root:

```powershell
cd smart-queue\backend
mvn spring-boot:run
```

Optional ML service:

```powershell
cd smart-queue\ml-service
python train.py
uvicorn app:app --reload --port 8000
```

Frontend:

```powershell
cd smart-queue\frontend
python -m http.server 5500
```

Open `http://localhost:5500`.

## Demo accounts

Development accounts are initialized by the backend:

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@smartqueue.com` | `admin123` |
| STAFF | `staff@smartqueue.com` | `staff123` |

Register a temporary USER account through the registration page. Do not use these development credentials outside a local demonstration.

## Recommended five-minute workflow

1. Open `/api/health` and show `status: UP`.
2. Register and log in as USER.
3. Select `General Service` and create three tokens.
4. Show the user dashboard queue positions `1`, `2`, and `3` and estimated waits.
5. Cancel the middle token. Refresh the queue and show the remaining positions shift without reordering FIFO tokens.
6. Log in as STAFF and show the same queue in read-only mode.
7. Log in as ADMIN and deactivate one counter.
8. Return to the queue view and show waiting times recalculated using the reduced capacity.
9. Reactivate the counter and show the capacity-dependent wait recalculation again.
10. Open `data/tokens.json`, `data/queue_events.json`, and `data/predictions.json` to show persisted state and audit events.

## Optional API checkpoints

```text
GET  /api/health
POST /api/auth/register
POST /api/auth/login
GET  /api/services
POST /api/tokens
GET  /api/queue/{serviceId}
GET  /api/queue/{serviceId}/position/{tokenId}
DELETE /api/tokens/{tokenId}
POST /api/admin/counters/{id}/deactivate
POST /api/admin/counters/{id}/activate
```

## What to explain during the demo

- Token cancellation changes status; it does not physically delete history.
- FIFO ordering is based on creation time.
- Queue positions and waits are recalculated after state changes.
- ML improves estimation when available, but Java fallback keeps the queue operational.
- The current JSON database is intentionally suitable for a local academic prototype, not concurrent production deployment.

