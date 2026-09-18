# Viva Questions and Answers

## 1. What problem does Smart Queue solve?

It lets a user join a virtual service queue, see a live position and estimated wait, and avoid standing in a physical line.

## 2. Why use JSON files instead of a relational database?

The project scope prioritizes a beginner-friendly local prototype with no external database dependency. `JsonFileRepository<T>` provides a replaceable persistence boundary.

## 3. How is FIFO fairness maintained?

Active tokens are sorted by creation time ascending, with token ID as a deterministic tie-breaker. Dynamic recalculation changes positions after queue state changes but does not arbitrarily reorder tokens.

## 4. Which token states are active?

`WAITING`, `CALLED`, and `SERVING` are active. `COMPLETED`, `CANCELLED`, and `NO_SHOW` are excluded.

## 5. What happens when a token is cancelled?

Its status becomes `CANCELLED`, its history remains in `tokens.json`, a cancellation event is recorded, and the remaining active queue is recalculated.

## 6. How is basic waiting time calculated?

`tokensAhead × averageServiceTime / activeCounters`. Zero active counters return zero to avoid division by zero.

## 7. Why is the allocation strategy an interface?

It separates queue ordering from controllers and services, allowing a future policy such as priority or ML-assisted allocation without rewriting the HTTP layer.

## 8. What does the ML model predict?

It predicts waiting time in minutes from queue size, tokens ahead, active counters, average service time, and hour of day.

## 9. What happens if the ML service is unavailable?

Java validates the failure and uses deterministic fallback waiting-time calculation. Queue access and token operations continue.

## 10. Why are the ML metrics so high?

The current dataset is synthetic and generated from a predictable formula. High validation metrics therefore show successful learning of that synthetic relationship, not guaranteed real-world accuracy.

## 11. How is authentication implemented?

Spring Security uses stateless JWT authentication. Passwords are hashed with BCrypt. Authorization is enforced by role and service-layer ownership checks.

## 12. Why does the frontend poll?

Polling is a simple Phase 9 choice that avoids WebSockets. User and staff dashboards refresh every ten seconds and stop timers on unload.

## 13. What is the main production limitation?

The JSON file database is not suitable for concurrent multi-instance writes. A production deployment would need transactional storage and stronger operational controls.

## 14. What is the main ML limitation?

The model was trained on synthetic data and lacks real queue dynamics such as arrivals, no-shows, breaks, service variation, and drift monitoring.

## 15. What would Phase 10 add technically?

Nothing technical in the runtime. Phase 10 is final validation, cleanup, documentation, demo preparation, and research/patent preparation.

