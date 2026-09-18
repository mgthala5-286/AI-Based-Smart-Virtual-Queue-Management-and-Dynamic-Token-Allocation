# Queue and Waiting-Time Algorithms

## Active queue definition

The active queue contains only:

- `WAITING`
- `CALLED`
- `SERVING`

`COMPLETED`, `CANCELLED`, and `NO_SHOW` tokens are excluded from the active queue. Excluded tokens are reset to position `0` and estimated wait `0`.

## FIFO dynamic allocation

For one service, the allocation engine:

1. Filters tokens by service ID and active status.
2. Sorts by `createdAt` ascending.
3. Uses token ID as a deterministic tie-breaker.
4. Assigns positions from `1` through the active queue size.
5. Recalculates estimated waiting time for every active token.
6. Persists changed tokens.
7. Emits `POSITION_CHANGED` only when the old and new positions differ.

The `QueueAllocationStrategy` interface keeps this step replaceable. `DynamicTokenAllocationStrategy` currently delegates ordering to `FifoAllocationStrategy`, so Phase 6 dynamic recalculation preserves fairness and does not introduce arbitrary prioritization.

## Deterministic fallback formula

For `tokensAhead = position - 1`:

```text
fallbackWaitMinutes = tokensAhead × averageServiceTime / activeCounters
```

If `activeCounters == 0`, the implementation returns `0` instead of dividing by zero. The value is recalculated after token changes, queue reads, and counter capacity changes.

## ML waiting-time path

When enabled, Java sends these five features to the ML service:

```text
queueSize
tokensAhead
activeCounters
averageServiceTime
hourOfDay
```

The ML result is used only when it is finite, non-negative, and returned successfully. Otherwise the fallback formula is used. This makes ML an optional estimation input rather than a single point of failure.

## Token number generation

The first alphanumeric character of the service name becomes the uppercase prefix. For example, `General Service` produces `G001`, `G002`, and so on. The repository is checked for active collisions before the number is accepted. Cancelled token numbers do not block future numbering.

## Event rules

| Trigger | Event |
|---|---|
| Token creation | `TOKEN_CREATED` |
| Token cancellation | `TOKEN_CANCELLED` |
| Position actually changes | `POSITION_CHANGED` |

No position event is written when `oldPosition == newPosition`. This avoids duplicate noise during repeated polling and recalculation.

## Complexity and consistency

For a service with `n` active tokens, sorting is `O(n log n)`. JSON persistence adds file I/O for the affected records. Recalculation methods are synchronized to keep a single JVM from interleaving queue updates for the same operation. The JSON database is intentionally simple and is not designed for multi-instance production deployment.

