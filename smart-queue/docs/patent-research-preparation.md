# Patent and Research-Paper Preparation

## Important qualification

This is a technical preparation document, not a legal opinion and not a conclusion that the system is patentable. Patentability depends on jurisdiction, prior art, claim drafting, disclosure timing, and professional review by a qualified patent attorney or technology-transfer office.

## Working title

**A Capacity-Aware Virtual Queue with Dynamic FIFO Reallocation and Optional Predictive Waiting-Time Estimation**

## Problem statement

Traditional virtual queues often expose a static token position or require manual recalculation after cancellations and counter changes. The project addresses this by recalculating active positions and waiting estimates whenever queue state or service capacity changes, while preserving explainable FIFO ordering.

## Candidate contribution statement

The implementation combines:

1. A service-isolated virtual token queue backed by a simple persistence layer.
2. Dynamic FIFO position recalculation after creation, cancellation, completion/no-show state changes, and counter-capacity changes.
3. Capacity-aware waiting-time recalculation with a deterministic fallback.
4. An optional prediction adapter that can improve waiting-time estimation without taking over queue ordering or authorization.
5. Event records for explainability and audit of position changes.

These elements should be treated as candidate technical contributions for investigation, not as guaranteed novel claims.

## Why novelty must be investigated

Virtual queues, FIFO scheduling, estimated wait times, dynamic staffing, and predictive service-time systems are individually well-established areas. The defensible research question is whether the specific combination, trigger behavior, replaceable strategy boundary, and fallback/audit mechanism provide a non-obvious technical improvement over identified prior art.

## Prior-art search plan

Search patent and scholarly databases using combinations of:

- virtual queue dynamic position recalculation
- token queue counter capacity waiting time
- FIFO queue cancellation position update
- predictive waiting time service counters
- queue allocation strategy fallback prediction
- virtual appointment queue audit events

For each relevant result, record publication number, date, applicant/authors, independent claims or method description, overlap, and a short distinction. Do not rely on titles alone.

## Research-paper outline

1. Abstract
2. Introduction and motivation
3. Related work
4. System requirements and threat model
5. Architecture and JSON persistence boundary
6. Dynamic FIFO allocation algorithm
7. Waiting-time baseline and Random Forest model
8. Experimental design and metrics
9. Results and outage/fallback analysis
10. Security and privacy considerations
11. Limitations and future work
12. Conclusion

## Reproducible evaluation plan

- Publish the exact feature contract and deterministic random seed.
- Compare the fallback formula with the Random Forest model.
- Use a time-based holdout when real data becomes available.
- Measure MAE, RMSE, R², position-update latency, and fallback recovery.
- Test one, two, and zero active counters.
- Test independent service queues and repeated cancellations.
- Report results separately for normal and peak periods.
- Never include plaintext passwords, personal identifiers, or raw user records in a paper or patent submission.

## Evidence to preserve

- Source revision or archive snapshot.
- Test report showing all backend tests passed.
- Model metadata and training script.
- Example queue-event traces.
- Screenshots or screen recording of the demo with synthetic accounts.
- A data dictionary and reproducible run instructions.

## Disclosure checklist

Before a public presentation, paper submission, or repository publication, confirm whether a patent filing should occur first. Have counsel review the proposed abstract, diagrams, claim concepts, and any public disclosure date.

