# CreatorRx data-access POC

Java 25 + Micronaut + Gradle. Read-only, single-user API feasibility experiment for YouTube creators. No database, frontend, AI, recommendation engine or automatic video edits.

## Run

Use the existing checkout; cloud tasks are isolated and do not require a Git worktree.

```sh
cd /workspace/PilotProject
export JAVA_HOME=/workspace/tools/jdk-25.0.4.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
./gradlew test
./gradlew run
```

The local API listens on 127.0.0.1:8080. `GET /health` should return `status: ok`. Processes must be restarted after restoring an environment. Java 25 and Gradle 9.1 are required; the wrapper installs verified Gradle automatically.

## Google configuration (required for live checks)

1. Create/select a Google Cloud project. Enable **YouTube Data API v3**, **YouTube Analytics API**, and **YouTube Reporting API**.
2. Configure Google Auth Platform consent, select testing, and add your Google account as a test user. That account must own a YouTube channel.
3. Create an OAuth **Web application** client. Register the exact redirect URI `http://localhost:8080/oauth/callback` for local execution.
4. Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and optionally `GOOGLE_REDIRECT_URI` in your runtime using secure local configuration. Never commit credentials. Restart the app after setting variables.
5. Open `/auth/google` in a browser that can reach the running app. On a remote machine, use an authenticated port tunnel and a matching redirect URI; localhost on your laptop does not automatically reach the cloud machine. Do not expose this single-user app publicly.

Scopes: `youtube.readonly` and `yt-analytics.readonly`. There are no upload/edit/delete scopes. Tokens are kept in memory, refreshed when possible, and lost on restart. OAuth uses expiring single-use state and PKCE. This POC has no multi-user session isolation; all callers to the local process share the connected account.

## Verify the workflow

After OAuth completes, use these endpoints (raw Google JSON unless noted):

| Method | Endpoint | Check |
| --- | --- | --- |
| GET | `/youtube/channel` | Owned channel and uploads playlist |
| GET | `/youtube/videos?pageToken=...` | Up to 50 uploaded videos; follow nextPageToken |
| GET | `/youtube/videos/VIDEO_ID/details` | Metadata, duration and public statistics |
| GET | `/youtube/analytics/VIDEO_ID?startDate=2026-01-01&endDate=2026-02-01` | Daily views, watch minutes, duration, percentage viewed, subscribers gained |
| GET | `/youtube/analytics/VIDEO_ID/retention?startDate=2026-01-01&endDate=2026-02-01` | Retention curve by elapsedVideoTimeRatio |
| GET | `/youtube/reporting/types?pageToken=...` | Discover available report IDs; follow pagination |
| GET | `/youtube/reporting/jobs?pageToken=...` | List existing jobs before creating one |
| POST | `/youtube/reporting/reach?reportTypeId=AVAILABLE_REACH_ID` | Create a job only for an advertised Reach type |
| GET | `/youtube/reporting/reports?jobId=JOB_ID&pageToken=...` | List generated reports; follow pagination |
| GET | `/youtube/reporting/download/JOB_ID/REPORT_ID` | Download raw CSV using Google's returned downloadUrl |

Example job creation: `curl -X POST 'http://127.0.0.1:8080/youtube/reporting/reach?reportTypeId=AVAILABLE_REACH_ID'`.

The CSV is returned unchanged; inspect its header for `video_thumbnail_impressions` and `video_thumbnail_impressions_ctr`. Preserve raw CTR units until confirmed against the report schema. Downloads are restricted to HTTPS on youtubereporting.googleapis.com so access tokens cannot be sent to arbitrary URLs. This in-memory download is suitable for small POC reports, not large production exports.

Report generation is asynchronous; an empty report list immediately after job creation is not proof of failure. Reuse an existing job rather than repeatedly creating jobs. Retention and analytics may be empty for low-volume videos or unprocessed dates. Use a known watched video and completed dates; zero rows do not prove metric access.

**Feasibility is not established until live OAuth, owned video data, nonempty analytics/retention, and an actual Reach CSV containing impressions/CTR succeed.** Planning notes about specific report IDs, backfill windows, quotas or latency are hypotheses, not verified guarantees. If Reach types are unavailable, record that blocker rather than substituting views for impressions.

Official references:
- https://developers.google.com/identity/protocols/oauth2/web-server
- https://developers.google.com/youtube/v3/docs
- https://developers.google.com/youtube/analytics/reference/reports/query
- https://developers.google.com/youtube/analytics/metrics
- https://developers.google.com/youtube/reporting/v1/reports
- https://developers.google.com/youtube/reporting/v1/reference/rest

## Validation and limits

`./gradlew test` checks request construction, invalid query rejection, OAuth state rejection, mocked API flows, and download destination restrictions. These checks do not substitute for live Google validation. `/health` only proves the server started.

This is intentionally local and single-user. Production work needs account-specific encrypted token storage, application authentication, consent verification, retries/backoff, streaming downloads, report ingestion, and action-result tracking.

### Cloud proxy builds

On this cloud machine, use `JAVA_HOME=/workspace/tools/jdk-25.0.4.1 python3 scripts/gradle-cloud.py test` (or `run`). The helper passes the existing HTTPS proxy to Gradle and uses the machine's Java CA trust store. It preserves TLS verification and does not print proxy configuration or credentials. On a normal local workstation, use `./gradlew` directly.
