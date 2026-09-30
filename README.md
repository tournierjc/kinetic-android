# Kinetic (kinetic-android)

Android companion for [`kcode --server`](https://github.com/tournierjc/kinetick-code) —
drive your kcode Sessions from your phone.

## Features

**Conversation**
- **Session switch** — list, pin, archive, create and open a Session. Subagent
  sessions stay off this list; they open from their parent.
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
  status, `Stop tree`, plus background tasks. Tap a row to open that session.
- **Queue** — snapshot, continue, steer an item into the live turn, drop an item.
- **Info** — token/cost usage, context-window gauge, model picker from the server
  roster, rename/pin/archive, fork, the skill list with per-session
  require/optional/forbid dispositions, and pending Skill/Memory draft review
  (approve / edit+approve / reject).

Every capability-optional endpoint answers `404` when the active Runtime lacks it;
the client surfaces that instead of silently failing. Skill policy and knowledge
review require a session server that exposes
`POST /sessions/:id/skill-policy`, `GET /skills/proposals`, and
`POST /skills/proposals/:id/review` (kinetick-code #99 / #102). Idle Memory drafts
target agent Memory unless `targetRef` is `user`; approve writes before the
decision is recorded so a failed apply stays pending for retry.

## Build

```
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew assembleDebug   # APK
JAVA_HOME=<jdk17> ANDROID_HOME=<sdk> ./gradlew testDebugUnitTest
```

`WireTest` decodes captured server payloads and always runs. `KcodeClientLiveTest`
drives a real server end to end and is skipped unless you point it at one:

```
KCODE_BASE_URL=http://127.0.0.1:8788 KCODE_SERVER_TOKEN="$(tr -d '\n' < ~/.kinetick/run/session-server.token)" ./gradlew testDebugUnitTest
```

CI (`.github/workflows/android.yml`) builds the APK and runs the offline tests on
every push.

minSdk 26, targetSdk 35. Requires the server write API and bearer-token auth
(kinetick-code 0.6.8).

## Setup

1. On your machine: `kcode --server --host 0.0.0.0 --port 8788` (loopback by
   default; a LAN bind is an execution surface — trust your network).
2. The process writes a bearer token to `~/.kinetick/run/session-server.token`
   (mode `0600`) and does not print it. Pass `--server-token` to choose the
   value yourself; it is written to the same file.
3. In the app, tap **Settings**, enter `http://<machine-ip>:8788` and that
   token, then Test & save. Every request, including `/health` and `GET /events`,
   sends `Authorization: Bearer`.

## Layout

- `api/Models.kt` — wire models mirroring `TuiMessage` / `TuiStreamEvent` /
  `TuiStructuredPreview` / questionnaire and permission payloads.
- `api/Wire.kt` — tolerant JSON decoding; dispatches the stream on `type` because
  `message` is an object on `message` events and a string on `session-status`.
- `api/KcodeClient.kt` — synchronous OkHttp client, one method per endpoint, SSE
  entry points for `/prompt` and `/events`. Attaches `Authorization: Bearer`
  on every call.
- `ui/ChatStore.kt` — turn reducer (message upserts, delta appends, rewind/replace,
  resync flag).
- `ui/DiffView.kt`, `ui/ToolCard.kt` — preview rendering and tool cards.
- `ui/QuestionnaireSheet.kt`, `ui/PermissionSheet.kt` — the input-needed dialogs.
- `ui/SessionScreen.kt`, `ui/SessionTabs.kt` — chat, agents, queue and info tabs.
- `events/EventStreamService.kt` — foreground `/events` subscriber → notifications
  + `EventBus`.
- `app/src/test/.../WireTest.kt` — decoder tests against captured server payloads.
