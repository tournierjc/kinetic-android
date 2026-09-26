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

minSdk 26, targetSdk 35. Requires the server write API (kinetick-code
`feat/server-write-api`).

## Setup

1. On your machine: `kcode --server --port 8788` (loopback by default; a LAN bind
   is an execution surface — trust your network).
2. In the app, tap **Server**, enter `http://<machine-ip>:8788`, Test & save.

## Layout

- `api/Models.kt` — wire models mirroring `TuiMessage` / `TuiStreamEvent` /
  `TuiStructuredPreview` / questionnaire and permission payloads.
- `api/Wire.kt` — tolerant JSON decoding; dispatches the stream on `type` because
  `message` is an object on `message` events and a string on `session-status`.
- `api/KcodeClient.kt` — synchronous OkHttp client, one method per endpoint, SSE
  entry points for `/prompt` and `/events`.
- `ui/ChatStore.kt` — turn reducer (message upserts, delta appends, rewind/replace,
  resync flag).
- `ui/DiffView.kt`, `ui/ToolCard.kt` — preview rendering and tool cards.
- `ui/QuestionnaireSheet.kt`, `ui/PermissionSheet.kt` — the input-needed dialogs.
- `ui/SessionScreen.kt`, `ui/SessionTabs.kt` — chat, agents, queue and info tabs.
- `events/EventStreamService.kt` — foreground `/events` subscriber → notifications
  + `EventBus`.
- `app/src/test/.../WireTest.kt` — decoder tests against captured server payloads.
