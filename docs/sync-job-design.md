# Durable synchronization job design

The V2 migration introduces durable sync runs. No worker is started by this change.

## State transitions
- PENDING -> RUNNING
- RUNNING -> COMPLETED / RETRY_WAIT / FAILED
- RETRY_WAIT -> RUNNING
- Expired RUNNING lease -> RUNNING (reclaimed by worker)

## Worker implementation contract (next change)
- Claim in a short database transaction using `FOR UPDATE SKIP LOCKED`.
- Set `lease_until` and increment `attempts` on claim.
- Renew lease while processing; ensure only the lease owner may finish.
- Reclaim expired leases.
- Retry transient failures with exponential backoff + jitter.
- Permanent authorization failures transition to FAILED and require reconnect.
- Never hold a database transaction open while calling Google APIs.

Do not expose a public sync-trigger endpoint before creator authentication and channel ownership authorization are implemented.
