# Kinetic (kinetic-android)

Android companion for [`kcode --server`](https://github.com/tournierjc/kinetick-code) —
drive your kcode Sessions from your phone.

## Features
- **Session switch** — list, search, pin, and open any Session on the server.
- **Chat with live turns** — `POST /sessions/:id/prompt` streamed over SSE
  (deltas render live), with abort/steer.
- **Input-needed notifications** — a foreground service holds `GET /events`
  (SSE); `questionnaire.ask` / `permission.ask` raise heads-up notifications.
- **Permission replies** — allow once / deny straight from the session banner.
- **Subagent visibility** — delegation snapshot + stop (endpoints wired; UI in progress).
- **Skill list** — `GET /skills` rendered read-only.

## Build
```
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
minSdk 26, targetSdk 35. Requires the server write API (kinetick-code
`feat/server-write-api`, PR #78).

## Setup
1. On your machine: `kcode --server --port 8788` (loopback by default; a
   LAN bind is an execution surface — trust your network).
2. In the app, tap **Server**, enter `http://<machine-ip>:8788`, Test & save.

## Layout
- `api/KcodeClient.kt` — synchronous OkHttp client, one method per endpoint, tolerant DTOs.
- `events/EventStreamService.kt` — foreground SSE subscriber → notifications + `EventBus`.
- `ui/` — Compose screens: session list, chat (SSE bubbles), server settings.
