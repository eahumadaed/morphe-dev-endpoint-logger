# Morphe Dev Endpoint Logger

Development-only fork for endpoint telemetry experiments on patched app traffic paths.

## Scope

- Adds request endpoint logging to `logpico/*-request.log`.
- Adds response payload logging (InputStream tap) to `logpico/*-response.log`.
- Keeps original behavior guards and exception safety to avoid app-flow interruption.

## Log location

Preferred:

- `Download/logpico/`

Fallback (if public downloads write is restricted):

- `Android/data/<target.package>/files/logpico/`

## Notes

- This repository is for local development experiments only.
- Response correlation to endpoint is best-effort (`ThreadLocal`), may be imperfect under highly async flows.
- Build requires Morphe plugin registry access configured in Gradle credentials.
