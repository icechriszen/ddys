# DDYS Watch Together Worker

Cloudflare Worker + Durable Object service for DDYS remote watch-together rooms.

## Local checks

```bash
npm install
npm run typecheck
```

## Deploy

```bash
npm run deploy
```

The current deployed Worker origin is:

```text
https://ddys-watch-together.icechriszen.workers.dev
```

The Android build reads `WATCH_TOGETHER_BASE_URL` from the root Gradle properties by
default. Override it for one-off builds with either an environment variable or a
Gradle property:

```bash
./gradlew :app:assembleRelease -PWATCH_TOGETHER_BASE_URL=https://<worker-host>
```
