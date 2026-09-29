# Kinetic (kinetic-android)

Android companion for [`kcode --server`](https://github.com/tournierjc/kinetick-code) —
drive your kcode Sessions from your phone.

## Features

**Conversation**
- **Session switch** — list, pin, archive, create and open any Session on the server.
- **Live turns** — `POST /sessions/:id/prompt` streamed over SSE. Handles the full
  `TuiStreamEvent` contract: `delta` chunks, `message` upserts, `messages-replaced`,
  `messages-rewound`, `resync-required`, `session-status`, `error`, `done`.
- **Abort / steer** — Send steers a running turn; Abort stops it; Queue defers a
  follow-up into the server-side queue.
- **Tool cards** — collapsible tool calls with status, duration, input/output and
  the server's **structured preview**: unified diffs (`+`/`−` coloured per line),
  file contents, and non-renderable summaries.
- **Thinking blocks** — collapsible reasoning with its duration.

**Input needed**
- **Foreground event stream** — a `dataSync` service holds `GET /events` open and
  raises heads-up notifications on `questionnaire.ask` / `permission.ask`;
  tapping one opens the session.
- **Questionnaires** — multi-step dialog honouring single/multiple selection,
  `allowOther` free text, required steps, Skip and Dismiss.
- **Permissions** — dialog showing the tool, its input, the rule list and the diff
  preview, with Allow once / Always / Deny.

**Agents & resources** (per-session tabs)
- **Subagents** — delegation snapshot with queued/running/completed/failed/stopped
  status, `Stop tree`, plus background tasks.
- **Queue** — snapshot, continue, steer an item into the live turn, drop an item.
- **Info** — token/cost usage, context-window gauge, model picker from the server
  roster, rename/pin/archive, fork, and the skill list.

Every capability-optional endpoint answers `404` when the active Runtime lacks it;
the client surfaces that instead of silently failing.

## Build

```
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew assembleDebug   # APK
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew testDebugUnitTest
```

`WireTest` decodes captured server payloads and always runs. `KcodeClientLiveTest`
drives a real server end to end and is skipped unless you point it at one:

```
KCODE_BASE_URL=http://127.0.0.1:8788 \
KCODE_SERVER_TOKEN="$(tr -d '\n' < ~/.kinetick/run/session-server.token)" \
  ./gradlew testDebugUnitTest
```

CI (`.github/workflows/android.yml`) builds the APK and runs the offline tests on
every push.

minSdk 26, targetSdk 35. Requires the server write API (kinetick-code
`feat/server-write-api`) and the bearer token added in kinetick-code 0.6.8.

## Setup

1. On your machine: `kcode --server --host 0.0.0.0 --port 8788`. The default
   bind is loopback; this publishes the port so the phone can reach it.
   Anyone who has the token can drive the agent, and there is no TLS.
2. Read the token. When `--server-token` is omitted, kcode writes one to
   `~/.kinetick/run/session-server.token` and does not print it.
3. In the app, open **Settings**, enter `http://<machine-ip>:8788` and that
   token, then Test & save. Every request, including `GET /health` and the
   `GET /events` notification stream, sends `Authorization: Bearer`.

## Layout

- `api/Models.kt` — wire models mirroring `TuiMessage` / `TuiStreamEvent` /
  `TuiStructuredPreview` / questionnaire and permission payloads.
- `api/Wire.kt` — tolerant JSON decoding; dispatches the stream on `type` because
  `message` is an object on `message` events and a string on `session-status`.
- `api/KcodeClient.kt` — synchronous OkHttp client, one method per endpoint, SSE
  entry points for `/prompt` and `/events`. Attaches `Authorization: Bearer`
  from the token saved in Settings.
- `ui/ChatStore.kt` — turn reducer (message upserts, delta appends, rewind/replace,
  resync flag).
- `ui/DiffView.kt`, `ui/ToolCard.kt` — preview rendering and tool cards.
- `ui/QuestionnaireSheet.kt`, `ui/PermissionSheet.kt` — the input-needed dialogs.
- `ui/SessionScreen.kt`, `ui/SessionTabs.kt` — chat, agents, queue and info tabs.
- `events/EventStreamService.kt` — foreground `/events` subscriber → notifications
  + `EventBus`.
- `app/src/test/.../WireTest.kt` — decoder tests against captured server payloads.
